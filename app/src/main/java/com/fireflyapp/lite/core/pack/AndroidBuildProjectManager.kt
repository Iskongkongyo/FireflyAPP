package com.fireflyapp.lite.core.pack

import android.content.Context
import com.fireflyapp.lite.data.model.LocalBuildWorkspace
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

class AndroidBuildProjectManager {
    fun generateProject(
        context: Context,
        workspace: LocalBuildWorkspace,
        rawConfig: String
    ): LocalBuildWorkspace {
        val buildRootDir = File(workspace.buildRootPath)
        val generatedProjectDir = buildRootDir.resolve(GENERATED_PROJECT_DIR_NAME)
        val buildLogFile = File(workspace.buildLogPath)

        if (generatedProjectDir.exists()) {
            generatedProjectDir.deleteRecursively()
        }
        generatedProjectDir.mkdirs()

        val logLines = mutableListOf<String>()
        logLines += "Template asset: $TEMPLATE_ASSET_PATH"
        context.assets.open(TEMPLATE_ASSET_PATH).use { input ->
            extractZip(input = input, destinationDir = generatedProjectDir)
        }
        logLines += "Extracted template to ${generatedProjectDir.absolutePath}"

        val configFile = generatedProjectDir.resolve(GENERATED_CONFIG_PATH)
        configFile.parentFile?.mkdirs()
        configFile.writeText(rawConfig, Charsets.UTF_8)
        logLines += "Injected project config into ${configFile.absolutePath}"

        val manifestFile = generatedProjectDir.resolve(GENERATED_MANIFEST_PATH)
        if (manifestFile.exists()) {
            manifestFile.writeText(patchManifest(manifestFile.readText(Charsets.UTF_8)), Charsets.UTF_8)
            logLines += "Patched launcher activity in AndroidManifest.xml"
        }

        val stringsFile = generatedProjectDir.resolve(GENERATED_STRINGS_PATH)
        if (stringsFile.exists()) {
            stringsFile.writeText(
                patchAppName(stringsFile.readText(Charsets.UTF_8), workspace.projectName),
                Charsets.UTF_8
            )
            logLines += "Patched app_name resource to ${workspace.projectName}"
        }

        val settingsFile = generatedProjectDir.resolve(SETTINGS_FILE_NAME)
        if (settingsFile.exists()) {
            settingsFile.writeText(
                patchRootProjectName(settingsFile.readText(Charsets.UTF_8), workspace.projectName),
                Charsets.UTF_8
            )
            logLines += "Patched Gradle root project name"
        }

        buildLogFile.parentFile?.mkdirs()
        buildLogFile.writeText(logLines.joinToString(separator = "\n"), Charsets.UTF_8)

        return workspace.copy(
            generatedProjectPath = generatedProjectDir.absolutePath,
            generatedProjectReady = true
        )
    }

    private fun extractZip(input: java.io.InputStream, destinationDir: File) {
        val rootCanonicalPath = destinationDir.canonicalFile.absolutePath + File.separator
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace('\\', '/').trimStart('/')
                if (entryName.isNotBlank()) {
                    val outputFile = File(destinationDir, entryName)
                    val outputCanonicalPath = outputFile.canonicalFile.absolutePath
                    if (!outputCanonicalPath.startsWith(rootCanonicalPath) &&
                        outputCanonicalPath != destinationDir.canonicalPath
                    ) {
                        error("Unsafe template entry: $entryName")
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

    private fun patchManifest(manifest: String): String {
        val splashActivityPattern = Regex(
            """<activity\s+android:name="\.ui\.main\.SplashActivity"([^>]*)android:exported="false"([^>]*)/>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val patchedSplashActivity = splashActivityPattern.replace(manifest) { match ->
            """
            <activity
                        android:name=".ui.main.SplashActivity"${match.groupValues[1]}android:exported="true"${match.groupValues[2]}>
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />

                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
            """.trimIndent()
        }

        val projectHubPattern = Regex(
            """<activity\s+android:name="\.ui\.project\.ProjectHubActivity"([\s\S]*?)</activity>"""
        )
        return projectHubPattern.replace(patchedSplashActivity) { match ->
            val startTag = Regex("""<activity([^>]*)android:exported="true"([^>]*)>""")
                .replace(match.value.substringBefore("<intent-filter").trim(), """<activity$1android:exported="false"$2 />""")
            startTag
        }
    }

    private fun patchAppName(stringsXml: String, projectName: String): String {
        val escaped = projectName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return stringsXml.replace(
            Regex("""<string name="app_name">.*?</string>"""),
            """<string name="app_name">$escaped</string>"""
        )
    }

    private fun patchRootProjectName(settings: String, projectName: String): String {
        val escaped = projectName.replace("\"", "\\\"")
        return settings.replace(
            Regex("rootProject\\.name\\s*=\\s*\"[^\"]*\""),
            "rootProject.name = \"$escaped\""
        )
    }

    private companion object {
        const val TEMPLATE_ASSET_PATH = "runtime-shell-template.zip"
        const val GENERATED_PROJECT_DIR_NAME = "generated-project"
        const val GENERATED_CONFIG_PATH = "app/src/main/assets/app-config.json"
        const val GENERATED_MANIFEST_PATH = "app/src/main/AndroidManifest.xml"
        const val GENERATED_STRINGS_PATH = "app/src/main/res/values/strings.xml"
        const val SETTINGS_FILE_NAME = "settings.gradle.kts"
    }
}
