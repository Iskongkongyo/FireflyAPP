package com.fireflyapp.lite.core.pack

import java.io.ByteArrayOutputStream
import java.io.File

class BinaryManifestPatcher {
    data class StringPoolEntry(
        val index: Int,
        val entryOffset: Int,
        val offset: Int,
        val byteLength: Int,
        val charLength: Int,
        val charLengthFieldSize: Int,
        val byteLengthFieldSize: Int,
        val isUtf8: Boolean,
        val value: String
    )

    data class ManifestFlagState(
        val debuggable: Boolean?,
        val testOnly: Boolean?
    )

    data class ManifestStructureCheck(
        val isValid: Boolean,
        val message: String
    )

    private data class StringPoolChunk(
        val chunkOffset: Int,
        val headerSize: Int,
        val chunkSize: Int,
        val stringCount: Int,
        val styleCount: Int,
        val flags: Int,
        val stringsStart: Int,
        val stylesStart: Int,
        val entries: List<StringPoolEntry>
    )

    fun patchPackageIdentity(
        manifestFile: File,
        originalPackageName: String,
        targetPackageName: String
    ) {
        val replacements = linkedMapOf(
            originalPackageName to targetPackageName,
            "$originalPackageName.fileprovider" to "$targetPackageName.fileprovider",
            "$originalPackageName.androidx-startup" to "$targetPackageName.androidx-startup",
            "$originalPackageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" to
                "$targetPackageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ".ui.main.MainActivity" to "$originalPackageName.ui.main.MainActivity",
            ".ui.main.SplashActivity" to "$originalPackageName.ui.main.SplashActivity"
        )
        val (patchedManifestBytes, replacementCounts) = replaceStringPoolValues(
            manifestBytes = manifestFile.readBytes(),
            replacements = replacements
        )
        val patchedPackageCount = replacementCounts[originalPackageName] ?: 0
        val patchedFileProviderCount = replacementCounts["$originalPackageName.fileprovider"] ?: 0
        val patchedStartupAuthorityCount = replacementCounts["$originalPackageName.androidx-startup"] ?: 0

        require(patchedFileProviderCount > 0 || patchedStartupAuthorityCount > 0) {
            "Failed to patch provider authority in AndroidManifest.xml"
        }
        require(patchedPackageCount > 0) { "Failed to patch package name in AndroidManifest.xml" }
        manifestFile.writeBytes(patchedManifestBytes)
    }

    fun patchApplicationLabel(
        manifestFile: File,
        placeholderValue: String,
        applicationLabel: String
    ) {
        patchStringPoolPlaceholder(
            manifestFile = manifestFile,
            placeholderValue = placeholderValue,
            replacementValue = applicationLabel,
            fieldName = "application label"
        )
    }

    fun patchVersionName(
        manifestFile: File,
        placeholderValue: String,
        versionName: String
    ) {
        patchStringPoolPlaceholder(
            manifestFile = manifestFile,
            placeholderValue = placeholderValue,
            replacementValue = versionName,
            fieldName = "versionName"
        )
    }

    fun patchVersionCode(
        manifestFile: File,
        versionCode: Int
    ) {
        val manifestBytes = manifestFile.readBytes()
        val stringPool = parseStringPool(manifestBytes)
        val patchedCount = patchIntegerAttribute(
            manifestBytes = manifestBytes,
            strings = stringPool,
            tagName = "manifest",
            attributeName = "versionCode",
            value = versionCode
        )
        require(patchedCount > 0) { "Failed to patch versionCode in AndroidManifest.xml" }
        manifestFile.writeBytes(manifestBytes)
    }

    fun forceApplicationFlagsFalse(
        manifestFile: File,
        attributeTargets: Map<String, Set<String>> = mapOf(
            "manifest" to setOf("testOnly"),
            "application" to setOf("debuggable")
        )
    ): ManifestFlagState {
        val manifestBytes = manifestFile.readBytes()
        val stringPool = parseStringPool(manifestBytes)
        patchBooleanAttributes(
            manifestBytes = manifestBytes,
            strings = stringPool,
            attributeTargets = attributeTargets
        )
        neutralizeFlagAttributes(
            manifestBytes = manifestBytes,
            strings = stringPool,
            attributeNames = attributeTargets.values.flatten().toSet()
        )
        manifestFile.writeBytes(manifestBytes)
        return inspectFlags(manifestBytes, stringPool)
    }

    fun inspectFlags(manifestFile: File): ManifestFlagState {
        val manifestBytes = manifestFile.readBytes()
        return inspectFlags(manifestBytes, parseStringPool(manifestBytes))
    }

    fun validateStructure(manifestFile: File): ManifestStructureCheck {
        return validateStructure(manifestFile.readBytes())
    }

    fun validateStructure(manifestBytes: ByteArray): ManifestStructureCheck {
        if (manifestBytes.size < RES_XML_FILE_HEADER_SIZE) {
            return ManifestStructureCheck(
                isValid = false,
                message = "Binary AndroidManifest.xml is too small to contain a valid XML header."
            )
        }
        val fileHeaderSize = readU16(manifestBytes, 2)
        if (fileHeaderSize != RES_XML_FILE_HEADER_SIZE) {
            return ManifestStructureCheck(
                isValid = false,
                message = "Binary AndroidManifest.xml headerSize 0x${fileHeaderSize.toString(16)} is invalid."
            )
        }
        val declaredSize = readU32(manifestBytes, 4)
        if (declaredSize != manifestBytes.size) {
            return ManifestStructureCheck(
                isValid = false,
                message = "Binary AndroidManifest.xml declared size $declaredSize does not match file size ${manifestBytes.size}."
            )
        }

        var offset = RES_XML_FILE_HEADER_SIZE
        while (offset <= manifestBytes.size - RES_XML_CHUNK_HEADER_SIZE) {
            if (offset % CHUNK_ALIGNMENT != 0) {
                return ManifestStructureCheck(
                    isValid = false,
                    message = "Binary AndroidManifest.xml chunk offset 0x${offset.toString(16)} is not 4-byte aligned."
                )
            }
            val chunkType = readU16(manifestBytes, offset)
            val headerSize = readU16(manifestBytes, offset + 2)
            val chunkSize = readU32(manifestBytes, offset + 4)
            if (headerSize < RES_XML_CHUNK_HEADER_SIZE) {
                return ManifestStructureCheck(
                    isValid = false,
                    message = "Binary AndroidManifest.xml chunk 0x${chunkType.toString(16)} has invalid headerSize 0x${headerSize.toString(16)}."
                )
            }
            if (chunkSize < headerSize) {
                return ManifestStructureCheck(
                    isValid = false,
                    message = "Binary AndroidManifest.xml chunk 0x${chunkType.toString(16)} size 0x${chunkSize.toString(16)} is smaller than its header."
                )
            }
            if (chunkSize % CHUNK_ALIGNMENT != 0) {
                return ManifestStructureCheck(
                    isValid = false,
                    message = "Binary AndroidManifest.xml chunk 0x${chunkType.toString(16)} size 0x${chunkSize.toString(16)} is not 4-byte aligned."
                )
            }
            if (offset + chunkSize > manifestBytes.size) {
                return ManifestStructureCheck(
                    isValid = false,
                    message = "Binary AndroidManifest.xml chunk 0x${chunkType.toString(16)} overruns the file boundary."
                )
            }
            offset += chunkSize
        }

        if (offset != manifestBytes.size) {
            return ManifestStructureCheck(
                isValid = false,
                message = "Binary AndroidManifest.xml ended at 0x${offset.toString(16)} but file size is 0x${manifestBytes.size.toString(16)}."
            )
        }

        return runCatching {
            parseStringPoolChunk(manifestBytes)
            ManifestStructureCheck(
                isValid = true,
                message = "Binary AndroidManifest.xml structure is valid."
            )
        }.getOrElse { throwable ->
            ManifestStructureCheck(
                isValid = false,
                message = throwable.message ?: "Binary AndroidManifest.xml structure could not be parsed."
            )
        }
    }

    private fun replaceEncodedSequence(source: ByteArray, oldValue: String, newValue: String): Int {
        return replaceByteSequence(
            source = source,
            oldBytes = oldValue.toByteArray(Charsets.UTF_8),
            newBytes = newValue.toByteArray(Charsets.UTF_8)
        ) + replaceByteSequence(
            source = source,
            oldBytes = oldValue.toByteArray(Charsets.UTF_16LE),
            newBytes = newValue.toByteArray(Charsets.UTF_16LE)
        )
    }

    private fun replaceByteSequence(source: ByteArray, oldBytes: ByteArray, newBytes: ByteArray): Int {
        require(oldBytes.size == newBytes.size) { "Replacement must preserve byte size" }
        var replacements = 0
        var index = 0
        while (index <= source.size - oldBytes.size) {
            var matched = true
            var cursor = 0
            while (cursor < oldBytes.size) {
                if (source[index + cursor] != oldBytes[cursor]) {
                    matched = false
                    break
                }
                cursor += 1
            }
            if (matched) {
                newBytes.copyInto(source, destinationOffset = index)
                replacements += 1
                index += oldBytes.size
            } else {
                index += 1
            }
        }
        return replacements
    }

    private fun patchExactStringPoolValue(
        manifestBytes: ByteArray,
        entries: List<StringPoolEntry>,
        oldValue: String,
        newValue: String
    ): Int {
        var patched = 0
        entries
            .filter { it.value == oldValue }
            .forEach { entry ->
                require(oldValue.length == newValue.length) {
                    "Replacement must preserve string length for binary manifest patching"
                }
                val replacementBytes = if (entry.isUtf8) {
                    newValue.toByteArray(Charsets.UTF_8)
                } else {
                    newValue.toByteArray(Charsets.UTF_16LE)
                }
                require(replacementBytes.size == entry.byteLength) {
                    "Replacement must preserve byte length for binary manifest patching"
                }
                replacementBytes.copyInto(manifestBytes, destinationOffset = entry.offset)
                patched += 1
            }
        return patched
    }

    private fun replaceStringPoolValues(
        manifestBytes: ByteArray,
        replacements: Map<String, String>
    ): Pair<ByteArray, Map<String, Int>> {
        if (replacements.isEmpty()) {
            return manifestBytes to emptyMap()
        }

        val stringPoolChunk = parseStringPoolChunk(manifestBytes)
        val stringOffsetTableSize = stringPoolChunk.stringCount * 4
        val styleOffsetTableSize = stringPoolChunk.styleCount * 4
        val styleOffsetsOffset = stringPoolChunk.chunkOffset + stringPoolChunk.headerSize + stringOffsetTableSize
        val originalStyleOffsets = manifestBytes.copyOfRange(
            styleOffsetsOffset,
            styleOffsetsOffset + styleOffsetTableSize
        )
        val originalStyleData = if (stringPoolChunk.styleCount > 0 && stringPoolChunk.stylesStart > 0) {
            manifestBytes.copyOfRange(
                stringPoolChunk.chunkOffset + stringPoolChunk.stylesStart,
                stringPoolChunk.chunkOffset + stringPoolChunk.chunkSize
            )
        } else {
            byteArrayOf()
        }

        val replacementCounts = mutableMapOf<String, Int>()
        val rebuiltStringData = ByteArrayOutputStream()
        val rebuiltStringOffsets = IntArray(stringPoolChunk.stringCount)

        stringPoolChunk.entries.forEachIndexed { index, entry ->
            rebuiltStringOffsets[index] = rebuiltStringData.size()
            val replacementValue = replacements[entry.value] ?: entry.value
            if (replacementValue != entry.value) {
                replacementCounts[entry.value] = (replacementCounts[entry.value] ?: 0) + 1
            }
            writeStringPoolEntry(
                output = rebuiltStringData,
                isUtf8 = entry.isUtf8,
                value = replacementValue
            )
        }

        val rebuiltStringBytes = alignChunkData(rebuiltStringData.toByteArray())
        val rebuiltStringsStart = stringPoolChunk.headerSize + stringOffsetTableSize + styleOffsetTableSize
        val rebuiltStylesStart = if (stringPoolChunk.styleCount > 0) {
            rebuiltStringsStart + rebuiltStringBytes.size
        } else {
            0
        }
        val rebuiltChunkSize = rebuiltStringsStart + rebuiltStringBytes.size + originalStyleData.size

        val rebuiltChunk = ByteArray(rebuiltChunkSize)
        manifestBytes.copyInto(
            rebuiltChunk,
            endIndex = stringPoolChunk.chunkOffset + stringPoolChunk.headerSize,
            destinationOffset = 0,
            startIndex = stringPoolChunk.chunkOffset
        )
        writeU32(rebuiltChunk, 4, rebuiltChunkSize)
        writeU32(rebuiltChunk, 20, rebuiltStringsStart)
        writeU32(rebuiltChunk, 24, rebuiltStylesStart)

        var offset = stringPoolChunk.headerSize
        rebuiltStringOffsets.forEach { stringOffset ->
            writeU32(rebuiltChunk, offset, stringOffset)
            offset += 4
        }
        if (originalStyleOffsets.isNotEmpty()) {
            originalStyleOffsets.copyInto(rebuiltChunk, destinationOffset = offset)
            offset += originalStyleOffsets.size
        }
        rebuiltStringBytes.copyInto(rebuiltChunk, destinationOffset = offset)
        offset += rebuiltStringBytes.size
        if (originalStyleData.isNotEmpty()) {
            originalStyleData.copyInto(rebuiltChunk, destinationOffset = offset)
        }

        val delta = rebuiltChunkSize - stringPoolChunk.chunkSize
        val rebuiltManifest = ByteArray(manifestBytes.size + delta)
        manifestBytes.copyInto(
            rebuiltManifest,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = stringPoolChunk.chunkOffset
        )
        rebuiltChunk.copyInto(rebuiltManifest, destinationOffset = stringPoolChunk.chunkOffset)
        manifestBytes.copyInto(
            rebuiltManifest,
            destinationOffset = stringPoolChunk.chunkOffset + rebuiltChunkSize,
            startIndex = stringPoolChunk.chunkOffset + stringPoolChunk.chunkSize,
            endIndex = manifestBytes.size
        )
        writeU32(rebuiltManifest, 4, readU32(manifestBytes, 4) + delta)
        return rebuiltManifest to replacementCounts
    }

    private fun alignChunkData(bytes: ByteArray): ByteArray {
        val padding = (4 - (bytes.size % 4)) % 4
        if (padding == 0) {
            return bytes
        }
        return bytes.copyOf(bytes.size + padding)
    }

    private fun writeStringPoolEntry(
        output: ByteArrayOutputStream,
        isUtf8: Boolean,
        value: String
    ) {
        if (isUtf8) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            output.write(encodeUtf8Length(value.length))
            output.write(encodeUtf8Length(encoded.size))
            output.write(encoded)
            output.write(0)
            output.write(0)
        } else {
            val encoded = value.toByteArray(Charsets.UTF_16LE)
            output.write(encodeUtf16Length(value.length))
            output.write(encoded)
            output.write(0)
            output.write(0)
        }
    }

    private fun encodeUtf8Length(value: Int): ByteArray {
        require(value >= 0) { "Length must be non-negative" }
        return if (value <= 0x7F) {
            byteArrayOf(value.toByte())
        } else {
            require(value <= 0x7FFF) { "UTF-8 length exceeds two-byte field capacity" }
            byteArrayOf(
                ((value shr 8) or 0x80).toByte(),
                (value and 0xFF).toByte()
            )
        }
    }

    private fun encodeUtf16Length(value: Int): ByteArray {
        require(value >= 0) { "Length must be non-negative" }
        return if (value <= 0x7FFF) {
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte()
            )
        } else {
            require(value <= 0x7FFFFFFF) { "UTF-16 length exceeds four-byte field capacity" }
            val high = ((value shr 16) and 0x7FFF) or 0x8000
            val low = value and 0xFFFF
            byteArrayOf(
                (high and 0xFF).toByte(),
                ((high shr 8) and 0xFF).toByte(),
                (low and 0xFF).toByte(),
                ((low shr 8) and 0xFF).toByte()
            )
        }
    }

    private fun patchStringPoolPlaceholder(
        manifestFile: File,
        placeholderValue: String,
        replacementValue: String,
        fieldName: String
    ) {
        val manifestBytes = manifestFile.readBytes()
        val entries = parseStringPoolEntries(manifestBytes)
        val patchedCount = patchVariableLengthStringPoolValue(
            manifestBytes = manifestBytes,
            entries = entries,
            oldValue = placeholderValue,
            newValue = replacementValue
        )
        require(patchedCount > 0) { "Failed to patch $fieldName in AndroidManifest.xml" }
        manifestFile.writeBytes(manifestBytes)
    }

    private fun patchVariableLengthStringPoolValue(
        manifestBytes: ByteArray,
        entries: List<StringPoolEntry>,
        oldValue: String,
        newValue: String
    ): Int {
        var patched = 0
        entries
            .filter { it.value == oldValue }
            .forEach { entry ->
                val replacementBytes = if (entry.isUtf8) {
                    newValue.toByteArray(Charsets.UTF_8)
                } else {
                    newValue.toByteArray(Charsets.UTF_16LE)
                }
                require(replacementBytes.size <= entry.byteLength) {
                    "Replacement exceeds reserved placeholder size for binary manifest patching"
                }
                require(newValue.length <= maxEncodableLength(entry)) {
                    "Replacement exceeds encodable string length for binary manifest patching"
                }

                val terminatorSize = if (entry.isUtf8) 1 else 2
                val zeroUntil = entry.offset + entry.byteLength + terminatorSize
                for (index in entry.offset until zeroUntil) {
                    manifestBytes[index] = 0
                }
                writeStringLengths(manifestBytes, entry, newValue.length, replacementBytes.size)
                replacementBytes.copyInto(manifestBytes, destinationOffset = entry.offset)
                patched += 1
            }
        return patched
    }

    private fun patchBooleanAttributes(
        manifestBytes: ByteArray,
        strings: List<String>,
        attributeTargets: Map<String, Set<String>>
    ): Int {
        var patched = 0
        forEachStartElement(manifestBytes, strings) { _, extOffset, tagName, attributeStart, attributeSize, attributeCount ->
            val targetNames = attributeTargets[tagName].orEmpty()
            if (targetNames.isEmpty()) {
                return@forEachStartElement
            }
            var attributeOffset = extOffset + attributeStart
            repeat(attributeCount) {
                val attributeNameIndex = readU32(manifestBytes, attributeOffset + 4)
                val attributeName = strings.getOrNull(attributeNameIndex)
                if (attributeName in targetNames) {
                    writeU32(manifestBytes, attributeOffset + 8, NO_STRING)
                    manifestBytes[attributeOffset + 15] = TYPE_INT_BOOLEAN.toByte()
                    writeU32(manifestBytes, attributeOffset + 16, FALSE_VALUE)
                    patched += 1
                }
                attributeOffset += attributeSize
            }
        }
        return patched
    }

    private fun patchIntegerAttribute(
        manifestBytes: ByteArray,
        strings: List<String>,
        tagName: String,
        attributeName: String,
        value: Int
    ): Int {
        var patched = 0
        forEachStartElement(manifestBytes, strings) { _, extOffset, currentTagName, attributeStart, attributeSize, attributeCount ->
            if (currentTagName != tagName) {
                return@forEachStartElement
            }
            var attributeOffset = extOffset + attributeStart
            repeat(attributeCount) {
                val attributeNameIndex = readU32(manifestBytes, attributeOffset + 4)
                val currentAttributeName = strings.getOrNull(attributeNameIndex)
                if (currentAttributeName == attributeName) {
                    writeU32(manifestBytes, attributeOffset + 8, NO_STRING)
                    manifestBytes[attributeOffset + 15] = TYPE_INT_DEC.toByte()
                    writeU32(manifestBytes, attributeOffset + 16, value)
                    patched += 1
                }
                attributeOffset += attributeSize
            }
        }
        return patched
    }

    private fun neutralizeFlagAttributes(
        manifestBytes: ByteArray,
        strings: List<String>,
        attributeNames: Set<String>
    ): Int {
        var patched = 0
        val targetIndexes = strings.mapIndexedNotNull { index, value ->
            if (value in attributeNames) index else null
        }.toSet()
        if (targetIndexes.isEmpty()) {
            return 0
        }

        var offset = 8
        while (offset <= manifestBytes.size - 8) {
            val chunkType = readU16(manifestBytes, offset)
            val headerSize = readU16(manifestBytes, offset + 2)
            val chunkSize = readU32(manifestBytes, offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > manifestBytes.size) {
                break
            }

            if (chunkType == RES_XML_RESOURCE_MAP_TYPE) {
                val count = (chunkSize - headerSize) / 4
                repeat(count) { index ->
                    if (index in targetIndexes) {
                        writeU32(manifestBytes, offset + headerSize + (index * 4), 0)
                        patched += 1
                    }
                }
                break
            }
            offset += chunkSize
        }

        attributeNames.forEach { attributeName ->
            val replacement = attributeName.dropLast(1) + "x"
            patched += replaceEncodedSequence(
                source = manifestBytes,
                oldValue = attributeName,
                newValue = replacement
            )
        }
        return patched
    }

    private fun inspectFlags(
        manifestBytes: ByteArray,
        strings: List<String>
    ): ManifestFlagState {
        var debuggable: Boolean? = null
        var testOnly: Boolean? = null
        forEachStartElement(manifestBytes, strings) { _, extOffset, tagName, attributeStart, attributeSize, attributeCount ->
            val trackedAttribute = when (tagName) {
                "manifest" -> "testOnly"
                "application" -> "debuggable"
                else -> null
            } ?: return@forEachStartElement

            var attributeOffset = extOffset + attributeStart
            repeat(attributeCount) {
                val attributeNameIndex = readU32(manifestBytes, attributeOffset + 4)
                val attributeName = strings.getOrNull(attributeNameIndex)
                if (attributeName == trackedAttribute) {
                    val dataType = manifestBytes[attributeOffset + 15].toInt() and 0xFF
                    val data = readU32(manifestBytes, attributeOffset + 16)
                    val value = when (dataType) {
                        TYPE_INT_BOOLEAN -> data != FALSE_VALUE
                        else -> data != FALSE_VALUE
                    }
                    when (tagName) {
                        "manifest" -> testOnly = value
                        "application" -> debuggable = value
                    }
                }
                attributeOffset += attributeSize
            }
        }
        return ManifestFlagState(
            debuggable = debuggable,
            testOnly = testOnly
        )
    }

    private fun forEachStartElement(
        manifestBytes: ByteArray,
        strings: List<String>,
        block: (offset: Int, extOffset: Int, tagName: String, attributeStart: Int, attributeSize: Int, attributeCount: Int) -> Unit
    ) {
        var offset = 8
        while (offset <= manifestBytes.size - 8) {
            val chunkType = readU16(manifestBytes, offset)
            val chunkSize = readU32(manifestBytes, offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > manifestBytes.size) {
                break
            }

            if (chunkType == RES_XML_START_ELEMENT_TYPE) {
                val extOffset = offset + RES_XML_TREE_NODE_HEADER_SIZE
                val nameIndex = readU32(manifestBytes, extOffset + 4)
                val tagName = strings.getOrNull(nameIndex).orEmpty()
                val attributeStart = readU16(manifestBytes, extOffset + 8)
                val attributeSize = readU16(manifestBytes, extOffset + 10)
                val attributeCount = readU16(manifestBytes, extOffset + 12)
                if (tagName.isNotEmpty()) {
                    block(offset, extOffset, tagName, attributeStart, attributeSize, attributeCount)
                }
            }

            offset += chunkSize
        }
    }

    private fun parseStringPool(manifestBytes: ByteArray): List<String> {
        return parseStringPoolChunk(manifestBytes).entries.map { it.value }
    }

    private fun parseStringPoolEntries(manifestBytes: ByteArray): List<StringPoolEntry> {
        return parseStringPoolChunk(manifestBytes).entries
    }

    private fun parseStringPoolChunk(manifestBytes: ByteArray): StringPoolChunk {
        var offset = 8
        while (offset <= manifestBytes.size - 8) {
            val chunkType = readU16(manifestBytes, offset)
            val headerSize = readU16(manifestBytes, offset + 2)
            val chunkSize = readU32(manifestBytes, offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > manifestBytes.size) {
                break
            }
            if (chunkType == RES_STRING_POOL_TYPE) {
                val stringCount = readU32(manifestBytes, offset + 8)
                val styleCount = readU32(manifestBytes, offset + 12)
                val flags = readU32(manifestBytes, offset + 16)
                val stringsStart = readU32(manifestBytes, offset + 20)
                val stylesStart = readU32(manifestBytes, offset + 24)
                val isUtf8 = (flags and UTF8_FLAG) != 0
                val stringIndexBase = offset + headerSize
                val stringsBase = offset + stringsStart
                val entries = List(stringCount) { index ->
                    val stringOffset = stringsBase + readU32(manifestBytes, stringIndexBase + (index * 4))
                    if (isUtf8) {
                        decodeUtf8Entry(index, manifestBytes, stringOffset)
                    } else {
                        decodeUtf16Entry(index, manifestBytes, stringOffset)
                    }
                }
                return StringPoolChunk(
                    chunkOffset = offset,
                    headerSize = headerSize,
                    chunkSize = chunkSize,
                    stringCount = stringCount,
                    styleCount = styleCount,
                    flags = flags,
                    stringsStart = stringsStart,
                    stylesStart = stylesStart,
                    entries = entries
                )
            }
            offset += chunkSize
        }
        error("String pool not found in AndroidManifest.xml")
    }

    private fun decodeUtf8Entry(index: Int, bytes: ByteArray, offset: Int): StringPoolEntry {
        val charLengthFieldSize = utf8LengthFieldSize(bytes, offset)
        val charLength = readUtf8Length(bytes, offset)
        val byteLengthOffset = offset + charLengthFieldSize
        val byteLengthFieldSize = utf8LengthFieldSize(bytes, byteLengthOffset)
        val byteLength = readUtf8Length(bytes, byteLengthOffset)
        val cursor = byteLengthOffset + byteLengthFieldSize
        return StringPoolEntry(
            index = index,
            entryOffset = offset,
            offset = cursor,
            byteLength = byteLength,
            charLength = charLength,
            charLengthFieldSize = charLengthFieldSize,
            byteLengthFieldSize = byteLengthFieldSize,
            isUtf8 = true,
            value = bytes.copyOfRange(cursor, cursor + byteLength).toString(Charsets.UTF_8)
        )
    }

    private fun decodeUtf16Entry(index: Int, bytes: ByteArray, offset: Int): StringPoolEntry {
        val charLength = readUtf16Length(bytes, offset)
        val lengthFieldSize = utf16LengthFieldSize(bytes, offset)
        val start = offset + lengthFieldSize
        val byteLength = charLength * 2
        return StringPoolEntry(
            index = index,
            entryOffset = offset,
            offset = start,
            byteLength = byteLength,
            charLength = charLength,
            charLengthFieldSize = lengthFieldSize,
            byteLengthFieldSize = 0,
            isUtf8 = false,
            value = bytes.copyOfRange(start, start + byteLength).toString(Charsets.UTF_16LE)
        )
    }

    private fun decodeUtf8String(bytes: ByteArray, offset: Int): String {
        var cursor = offset
        cursor += utf8LengthFieldSize(bytes, cursor)
        val byteLength = readUtf8Length(bytes, cursor)
        cursor += utf8LengthFieldSize(bytes, cursor)
        return bytes.copyOfRange(cursor, cursor + byteLength).toString(Charsets.UTF_8)
    }

    private fun decodeUtf16String(bytes: ByteArray, offset: Int): String {
        val charLength = readUtf16Length(bytes, offset)
        val lengthFieldSize = utf16LengthFieldSize(bytes, offset)
        val start = offset + lengthFieldSize
        val byteLength = charLength * 2
        return bytes.copyOfRange(start, start + byteLength).toString(Charsets.UTF_16LE)
    }

    private fun readUtf8Length(bytes: ByteArray, offset: Int): Int {
        val first = bytes[offset].toInt() and 0xFF
        return if ((first and 0x80) == 0) {
            first
        } else {
            ((first and 0x7F) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        }
    }

    private fun utf8LengthFieldSize(bytes: ByteArray, offset: Int): Int {
        return if ((bytes[offset].toInt() and 0x80) == 0) 1 else 2
    }

    private fun readUtf16Length(bytes: ByteArray, offset: Int): Int {
        val first = readU16(bytes, offset)
        return if ((first and 0x8000) == 0) {
            first
        } else {
            ((first and 0x7FFF) shl 16) or readU16(bytes, offset + 2)
        }
    }

    private fun utf16LengthFieldSize(bytes: ByteArray, offset: Int): Int {
        return if ((readU16(bytes, offset) and 0x8000) == 0) 2 else 4
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeStringLengths(
        bytes: ByteArray,
        entry: StringPoolEntry,
        charLength: Int,
        byteLength: Int
    ) {
        if (entry.isUtf8) {
            writeUtf8Length(bytes, entry.entryOffset, entry.charLengthFieldSize, charLength)
            writeUtf8Length(
                bytes,
                entry.entryOffset + entry.charLengthFieldSize,
                entry.byteLengthFieldSize,
                byteLength
            )
        } else {
            writeUtf16Length(bytes, entry.entryOffset, entry.charLengthFieldSize, charLength)
        }
    }

    private fun writeUtf8Length(bytes: ByteArray, offset: Int, fieldSize: Int, value: Int) {
        require(fieldSize in 1..2) { "Unsupported UTF-8 length field size" }
        require(value >= 0) { "Length must be non-negative" }
        if (fieldSize == 1) {
            require(value <= 0x7F) { "UTF-8 length exceeds one-byte field capacity" }
            bytes[offset] = value.toByte()
            return
        }
        require(value <= 0x7FFF) { "UTF-8 length exceeds two-byte field capacity" }
        bytes[offset] = ((value shr 8) or 0x80).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeUtf16Length(bytes: ByteArray, offset: Int, fieldSize: Int, value: Int) {
        require(fieldSize in setOf(2, 4)) { "Unsupported UTF-16 length field size" }
        require(value >= 0) { "Length must be non-negative" }
        if (fieldSize == 2) {
            require(value <= 0x7FFF) { "UTF-16 length exceeds two-byte field capacity" }
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
            return
        }
        require(value <= 0x7FFFFFFF) { "UTF-16 length exceeds four-byte field capacity" }
        val high = ((value shr 16) and 0x7FFF) or 0x8000
        val low = value and 0xFFFF
        bytes[offset] = (high and 0xFF).toByte()
        bytes[offset + 1] = ((high shr 8) and 0xFF).toByte()
        bytes[offset + 2] = (low and 0xFF).toByte()
        bytes[offset + 3] = ((low shr 8) and 0xFF).toByte()
    }

    private fun maxEncodableLength(entry: StringPoolEntry): Int {
        return if (entry.isUtf8) {
            if (entry.charLengthFieldSize == 1) 0x7F else 0x7FFF
        } else {
            if (entry.charLengthFieldSize == 2) 0x7FFF else 0x7FFFFFFF
        }
    }

    private companion object {
        const val CHUNK_ALIGNMENT = 4
        const val RES_XML_FILE_HEADER_SIZE = 8
        const val RES_XML_CHUNK_HEADER_SIZE = 8
        const val RES_STRING_POOL_TYPE = 0x0001
        const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
        const val RES_XML_START_ELEMENT_TYPE = 0x0102
        const val RES_XML_TREE_NODE_HEADER_SIZE = 16
        const val TYPE_INT_BOOLEAN = 0x12
        const val TYPE_INT_DEC = 0x10
        const val FALSE_VALUE = 0
        const val NO_STRING = -1
        const val UTF8_FLAG = 1 shl 8
    }
}
