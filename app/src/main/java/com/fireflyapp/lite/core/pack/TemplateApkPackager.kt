package com.fireflyapp.lite.core.pack

import com.fireflyapp.lite.data.model.TemplatePackExecutionResult
import com.fireflyapp.lite.data.model.TemplatePackExecutionStatus
import com.fireflyapp.lite.data.model.TemplatePackWorkspace
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

class TemplateApkPackager {
    fun packageUnsignedApk(workspace: TemplatePackWorkspace): TemplatePackExecutionResult {
        val unpackedDir = File(workspace.unpackedApkPath)
        if (!unpackedDir.exists()) {
            return writeBlockedResult(workspace, "Unpacked template APK directory is missing.")
        }

        val unsignedApkFile = File(workspace.unsignedApkPath)
        unsignedApkFile.parentFile?.mkdirs()
        if (unsignedApkFile.exists()) {
            unsignedApkFile.delete()
        }

        return runCatching {
            ZipFile(File(workspace.templateSourcePath)).use { templateZip ->
                val templateEntries = mutableListOf<ZipEntry>()
                val enumeration = templateZip.entries()
                while (enumeration.hasMoreElements()) {
                    templateEntries += enumeration.nextElement()
                }
                val templateEntryByName = templateEntries.associateBy { it.name }
                val writtenEntries = linkedSetOf<String>()

                CountingOutputStream(unsignedApkFile.outputStream()).use { countingOutput ->
                    ZipOutputStream(countingOutput).use { zip ->
                    templateEntries.forEach { originalEntry ->
                        if (originalEntry.isDirectory) {
                            return@forEach
                        }
                        val file = unpackedDir.resolve(originalEntry.name)
                        if (!file.exists() || !file.isFile) {
                            return@forEach
                        }
                        writeZipEntry(zip, countingOutput, file, originalEntry.name, originalEntry.method)
                        writtenEntries += originalEntry.name
                    }

                    unpackedDir.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val entryName = file.relativeTo(unpackedDir).invariantSeparatorsPath
                            if (entryName in writtenEntries) {
                                return@forEach
                            }
                            val originalMethod = templateEntryByName[entryName]?.method ?: ZipEntry.DEFLATED
                            writeZipEntry(zip, countingOutput, file, entryName, originalMethod)
                        }
                }
                }
            }

            val message = "Unsigned APK package generated. Signing is the next stage."
            appendLog(workspace, message)
            TemplatePackExecutionResult(
                status = TemplatePackExecutionStatus.SUCCEEDED,
                message = message,
                logPath = workspace.packLogPath,
                artifactPath = unsignedApkFile.absolutePath
            )
        }.getOrElse { throwable ->
            appendLog(workspace, "Unsigned packaging failed: ${throwable.message}")
            TemplatePackExecutionResult(
                status = TemplatePackExecutionStatus.FAILED,
                message = throwable.message ?: "Unsigned APK packaging failed.",
                logPath = workspace.packLogPath
            )
        }
    }

    private fun writeZipEntry(
        zip: ZipOutputStream,
        countingOutput: CountingOutputStream,
        file: File,
        entryName: String,
        method: Int
    ) {
        val entry = ZipEntry(entryName)
        val fileBytes = file.readBytes()
        if (method == ZipEntry.STORED) {
            val crc32 = CRC32().apply { update(fileBytes) }
            // Uncompressed native libraries must stay page-aligned when extractNativeLibs=false.
            val dataAlignment = resolveStoredEntryAlignment(entryName)
            val dataOffsetWithoutPadding = countingOutput.bytesWritten +
                ZIP_LOCAL_FILE_HEADER_SIZE +
                entryName.toByteArray(Charsets.UTF_8).size
            val padding = ((dataAlignment - (dataOffsetWithoutPadding % dataAlignment)) % dataAlignment)
            if (padding > 0) {
                entry.extra = ByteArray(padding.toInt())
            }
            entry.method = ZipEntry.STORED
            entry.size = fileBytes.size.toLong()
            entry.compressedSize = fileBytes.size.toLong()
            entry.crc = crc32.value
        }
        zip.putNextEntry(entry)
        zip.write(fileBytes)
        zip.closeEntry()
    }

    private fun resolveStoredEntryAlignment(entryName: String): Long {
        return if (entryName.startsWith(NATIVE_LIBRARY_PREFIX) && entryName.endsWith(NATIVE_LIBRARY_SUFFIX)) {
            NATIVE_LIBRARY_PAGE_ALIGNMENT
        } else {
            DEFAULT_ZIP_ALIGNMENT
        }
    }

    fun readLogPreview(workspace: TemplatePackWorkspace, maxLines: Int = 80): String {
        val logFile = File(workspace.packLogPath)
        if (!logFile.exists()) {
            return ""
        }
        return logFile.readLines(Charsets.UTF_8)
            .takeLast(maxLines)
            .joinToString(separator = "\n")
    }

    private fun writeBlockedResult(
        workspace: TemplatePackWorkspace,
        message: String
    ): TemplatePackExecutionResult {
        appendLog(workspace, message)
        return TemplatePackExecutionResult(
            status = TemplatePackExecutionStatus.BLOCKED,
            message = message,
            logPath = workspace.packLogPath
        )
    }

    private fun appendLog(workspace: TemplatePackWorkspace, message: String) {
        val logFile = File(workspace.packLogPath)
        logFile.parentFile?.mkdirs()
        logFile.appendText("[${timestamp()}] $message\n", Charsets.UTF_8)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var bytesWritten: Long = 0
            private set

        override fun write(b: Int) {
            out.write(b)
            bytesWritten += 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            bytesWritten += len.toLong()
        }
    }

    private companion object {
        const val DEFAULT_ZIP_ALIGNMENT = 4L
        const val NATIVE_LIBRARY_PAGE_ALIGNMENT = 16L * 1024L
        const val ZIP_LOCAL_FILE_HEADER_SIZE = 30L
        const val NATIVE_LIBRARY_PREFIX = "lib/"
        const val NATIVE_LIBRARY_SUFFIX = ".so"
    }
}
