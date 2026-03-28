package com.fireflyapp.lite.core.pack

import com.fireflyapp.lite.data.model.LocalBuildExecutionResult
import com.fireflyapp.lite.data.model.LocalBuildExecutionStatus
import com.fireflyapp.lite.data.model.LocalBuildWorkspace
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalBuildExecutor {
    fun execute(workspace: LocalBuildWorkspace): LocalBuildExecutionResult {
        val generatedProjectPath = workspace.generatedProjectPath
            ?: return writeBlockedResult(workspace, "Generated build project is missing.")
        val generatedProjectDir = File(generatedProjectPath)
        if (!generatedProjectDir.exists()) {
            return writeBlockedResult(workspace, "Generated build project directory does not exist.")
        }

        val shellPath = findShellPath()
            ?: return writeBlockedResult(workspace, "Device shell is unavailable.")
        val gradlewFile = generatedProjectDir.resolve("gradlew")
        if (!gradlewFile.exists()) {
            return writeBlockedResult(workspace, "Gradle wrapper script is missing from generated project.")
        }

        val javaCommand = findCommand("java")
            ?: return writeBlockedResult(
                workspace,
                "Java runtime was not found on this device. Local APK compilation cannot start yet."
            )

        val sdkPath = findAndroidSdkPath(generatedProjectDir)
            ?: return writeBlockedResult(
                workspace,
                "Android SDK was not found on this device. Local APK compilation cannot start yet."
            )

        val localPropertiesFile = generatedProjectDir.resolve("local.properties")
        localPropertiesFile.writeText("sdk.dir=${sdkPath.replace("\\", "\\\\")}", Charsets.UTF_8)

        val command = "cd \"${generatedProjectDir.absolutePath}\" && sh ./gradlew assembleDebug --stacktrace"
        val logFile = File(workspace.buildLogPath)
        logFile.parentFile?.mkdirs()
        logFile.appendText(
            buildString {
                appendLine()
                appendLine("[${timestamp()}] Starting build")
                appendLine("java=$javaCommand")
                appendLine("sdk.dir=$sdkPath")
                appendLine("cmd=$command")
            },
            Charsets.UTF_8
        )

        return runCatching {
            val process = ProcessBuilder(shellPath, "-c", command)
                .directory(generatedProjectDir)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    logFile.appendText("$line\n", Charsets.UTF_8)
                }
            }

            val exitCode = process.waitFor()
            val artifact = findGeneratedApk(generatedProjectDir)
            if (exitCode == 0 && artifact != null) {
                logFile.appendText("[${timestamp()}] Build succeeded: ${artifact.absolutePath}\n", Charsets.UTF_8)
                LocalBuildExecutionResult(
                    status = LocalBuildExecutionStatus.SUCCEEDED,
                    message = "APK build succeeded.",
                    command = command,
                    logPath = logFile.absolutePath,
                    artifactPath = artifact.absolutePath
                )
            } else {
                logFile.appendText("[${timestamp()}] Build failed. exitCode=$exitCode\n", Charsets.UTF_8)
                LocalBuildExecutionResult(
                    status = LocalBuildExecutionStatus.FAILED,
                    message = if (exitCode == 0) {
                        "Build finished but APK artifact was not found."
                    } else {
                        "Gradle build failed with exit code $exitCode."
                    },
                    command = command,
                    logPath = logFile.absolutePath,
                    artifactPath = artifact?.absolutePath
                )
            }
        }.getOrElse { throwable ->
            logFile.appendText("[${timestamp()}] Build crashed: ${throwable.message}\n", Charsets.UTF_8)
            LocalBuildExecutionResult(
                status = LocalBuildExecutionStatus.FAILED,
                message = throwable.message ?: "Local build execution failed.",
                command = command,
                logPath = logFile.absolutePath
            )
        }
    }

    fun readLogPreview(workspace: LocalBuildWorkspace, maxLines: Int = 80): String {
        val logFile = File(workspace.buildLogPath)
        if (!logFile.exists()) {
            return ""
        }
        return logFile.readLines(Charsets.UTF_8)
            .takeLast(maxLines)
            .joinToString(separator = "\n")
    }

    private fun writeBlockedResult(
        workspace: LocalBuildWorkspace,
        message: String
    ): LocalBuildExecutionResult {
        val logFile = File(workspace.buildLogPath)
        logFile.parentFile?.mkdirs()
        logFile.appendText("[${timestamp()}] Build blocked: $message\n", Charsets.UTF_8)
        return LocalBuildExecutionResult(
            status = LocalBuildExecutionStatus.BLOCKED,
            message = message,
            logPath = logFile.absolutePath
        )
    }

    private fun findShellPath(): String? {
        return listOf(
            "/system/bin/sh",
            "/bin/sh",
            "sh"
        ).firstOrNull { candidate ->
            candidate == "sh" || File(candidate).exists()
        }
    }

    private fun findCommand(command: String): String? {
        return listOf(
            "/system/bin/$command",
            "/system/xbin/$command",
            "/usr/bin/$command",
            "/usr/local/bin/$command"
        ).firstOrNull { File(it).exists() }
    }

    private fun findAndroidSdkPath(generatedProjectDir: File): String? {
        val environmentPath = listOf(
            "ANDROID_SDK_ROOT",
            "ANDROID_HOME"
        ).mapNotNull { System.getenv(it) }
            .firstOrNull { it.isNotBlank() && File(it).exists() }
        if (environmentPath != null) {
            return environmentPath
        }

        val localProperties = generatedProjectDir.resolve("local.properties")
        if (localProperties.exists()) {
            localProperties.readLines(Charsets.UTF_8)
                .firstOrNull { it.startsWith("sdk.dir=") }
                ?.substringAfter('=')
                ?.takeIf { it.isNotBlank() && File(it).exists() }
                ?.let { return it }
        }
        return null
    }

    private fun findGeneratedApk(generatedProjectDir: File): File? {
        val apkRoot = generatedProjectDir.resolve("app").resolve("build").resolve("outputs").resolve("apk")
        if (!apkRoot.exists()) {
            return null
        }
        return apkRoot.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }
}
