package com.fireflyapp.lite.core.project

import com.fireflyapp.lite.data.model.ProjectManifest
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectPackageManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun isZipArchive(inputStream: InputStream): Boolean {
        val buffered = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream)
        buffered.mark(ZIP_HEADER_SIZE)
        val header = ByteArray(ZIP_HEADER_SIZE)
        val read = buffered.read(header)
        buffered.reset()
        if (read < ZIP_HEADER_SIZE) {
            return false
        }
        return header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            (header[2] == 3.toByte() || header[2] == 5.toByte() || header[2] == 7.toByte()) &&
            (header[3] == 4.toByte() || header[3] == 6.toByte() || header[3] == 8.toByte())
    }

    fun exportPackage(
        projectId: String,
        projectDir: File,
        rawConfig: String,
        projectManifest: ProjectManifest,
        outputStream: OutputStream
    ) {
        val exportedPaths = linkedSetOf<String>()
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            val manifest = projectManifest.copy(
                projectId = projectId,
                exportedAt = System.currentTimeMillis()
            )
            writeEntry(
                zip = zip,
                entryName = MANIFEST_FILE_NAME,
                bytes = json.encodeToString(ProjectManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
            )
            exportedPaths += MANIFEST_FILE_NAME

            writeEntry(
                zip = zip,
                entryName = CONFIG_FILE_NAME,
                bytes = rawConfig.toByteArray(Charsets.UTF_8)
            )
            exportedPaths += CONFIG_FILE_NAME

            projectDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relativePath = file.relativeTo(projectDir).invariantSeparatorsPath
                    if (relativePath in exportedPaths || shouldSkipExportPath(relativePath)) {
                        return@forEach
                    }
                    writeEntry(
                        zip = zip,
                        entryName = relativePath,
                        bytes = file.readBytes()
                    )
                }
        }
    }

    fun importPackage(inputStream: InputStream, destinationDir: File) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val rootCanonicalPath = destinationDir.canonicalFile.absolutePath + File.separator
        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace('\\', '/').trimStart('/')
                if (entryName.isNotBlank()) {
                    val outputFile = File(destinationDir, entryName)
                    val outputCanonicalPath = outputFile.canonicalFile.absolutePath
                    if (!outputCanonicalPath.startsWith(rootCanonicalPath) && outputCanonicalPath != destinationDir.canonicalPath) {
                        error("Unsafe package entry: $entryName")
                    }
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        outputFile.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    fun readManifest(projectDir: File): ProjectManifest? {
        val manifestFile = projectDir.resolve(MANIFEST_FILE_NAME)
        if (!manifestFile.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString(ProjectManifest.serializer(), manifestFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun writeEntry(zip: ZipOutputStream, entryName: String, bytes: ByteArray) {
        val entry = ZipEntry(entryName)
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    companion object {
        const val MANIFEST_FILE_NAME = "project-manifest.json"
        const val CONFIG_FILE_NAME = "app-config.json"
        private const val ZIP_HEADER_SIZE = 4
        private const val BUILD_DIR_NAME = "build"
    }

    private fun shouldSkipExportPath(relativePath: String): Boolean {
        return relativePath == BUILD_DIR_NAME || relativePath.startsWith("$BUILD_DIR_NAME/")
    }
}
