package com.fireflyapp.lite.core.pack

import com.fireflyapp.lite.data.model.LocalBuildWorkspace
import com.fireflyapp.lite.data.model.LocalBuildWorkspaceStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LocalBuildWorkspaceManager(
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    fun inspectWorkspace(
        projectId: String,
        projectName: String,
        buildRootDir: File
    ): LocalBuildWorkspace {
        val packageFile = buildRootDir.resolve("$projectId.fireflyproj.zip")
        val workspaceDir = buildRootDir.resolve(WORKSPACE_DIR_NAME)
        val artifactDir = buildRootDir.resolve(ARTIFACTS_DIR_NAME)
        val jobFile = buildRootDir.resolve(JOB_FILE_NAME)
        val buildLogFile = buildRootDir.resolve(BUILD_LOG_FILE_NAME)
        val generatedProjectDir = buildRootDir.resolve(GENERATED_PROJECT_DIR_NAME)
        val job = readJob(jobFile)
        val expectedApkFile = artifactDir.resolve("${sanitizeFileName(projectName).ifBlank { projectId }}-debug.apk")
        val generatedProjectPath = job?.generatedProjectPath
            ?: generatedProjectDir.takeIf { it.exists() }?.absolutePath

        return LocalBuildWorkspace(
            projectId = projectId,
            projectName = projectName,
            buildRootPath = buildRootDir.absolutePath,
            workspacePath = workspaceDir.absolutePath,
            projectPackagePath = packageFile.absolutePath,
            jobFilePath = jobFile.absolutePath,
            buildLogPath = buildLogFile.absolutePath,
            artifactDirectoryPath = artifactDir.absolutePath,
            expectedApkPath = expectedApkFile.absolutePath,
            status = if (job != null) LocalBuildWorkspaceStatus.PREPARED else LocalBuildWorkspaceStatus.IDLE,
            generatedProjectPath = generatedProjectPath,
            generatedProjectReady = generatedProjectPath
                ?.let(::File)
                ?.resolve("settings.gradle.kts")
                ?.exists() == true,
            preparedAt = job?.preparedAt
        )
    }

    fun prepareWorkspace(
        projectId: String,
        projectName: String,
        buildRootDir: File,
        writeProjectPackage: (File) -> Unit
    ): LocalBuildWorkspace {
        val packageFile = buildRootDir.resolve("$projectId.fireflyproj.zip")
        val artifactDir = buildRootDir.resolve(ARTIFACTS_DIR_NAME)
        val jobFile = buildRootDir.resolve(JOB_FILE_NAME)
        val readmeFile = buildRootDir.resolve(README_FILE_NAME)
        val preparedAt = System.currentTimeMillis()

        buildRootDir.mkdirs()
        buildRootDir.resolve(WORKSPACE_DIR_NAME).mkdirs()
        artifactDir.mkdirs()

        writeProjectPackage(packageFile)
        readmeFile.writeText(buildReadme(projectName), Charsets.UTF_8)

        val workspace = inspectWorkspace(
            projectId = projectId,
            projectName = projectName,
            buildRootDir = buildRootDir
        ).copy(
            status = LocalBuildWorkspaceStatus.PREPARED,
            preparedAt = preparedAt
        )

        jobFile.writeText(
            json.encodeToString(
                LocalBuildJob(
                    projectId = projectId,
                    projectName = projectName,
                    preparedAt = preparedAt,
                    status = STATUS_PREPARED,
                    buildRootPath = workspace.buildRootPath,
                    workspacePath = workspace.workspacePath,
                    projectPackagePath = workspace.projectPackagePath,
                    jobFilePath = workspace.jobFilePath,
                    buildLogPath = workspace.buildLogPath,
                    artifactDirectoryPath = workspace.artifactDirectoryPath,
                    expectedApkPath = workspace.expectedApkPath,
                    generatedProjectPath = workspace.generatedProjectPath,
                    generatedProjectReady = workspace.generatedProjectReady
                )
            ),
            Charsets.UTF_8
        )

        return workspace
    }

    fun saveWorkspace(workspace: LocalBuildWorkspace, buildRootDir: File) {
        buildRootDir.mkdirs()
        buildRootDir.resolve(JOB_FILE_NAME).writeText(
            json.encodeToString(
                LocalBuildJob(
                    projectId = workspace.projectId,
                    projectName = workspace.projectName,
                    preparedAt = workspace.preparedAt ?: System.currentTimeMillis(),
                    status = STATUS_PREPARED,
                    buildRootPath = workspace.buildRootPath,
                    workspacePath = workspace.workspacePath,
                    projectPackagePath = workspace.projectPackagePath,
                    jobFilePath = workspace.jobFilePath,
                    buildLogPath = workspace.buildLogPath,
                    artifactDirectoryPath = workspace.artifactDirectoryPath,
                    expectedApkPath = workspace.expectedApkPath,
                    generatedProjectPath = workspace.generatedProjectPath,
                    generatedProjectReady = workspace.generatedProjectReady
                )
            ),
            Charsets.UTF_8
        )
    }

    private fun readJob(jobFile: File): LocalBuildJob? {
        if (!jobFile.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString<LocalBuildJob>(jobFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun buildReadme(projectName: String): String {
        return """
            Firefly local packager workspace

            Project: $projectName

            This directory is generated by the Pack page.
            The current stage prepares a deterministic local build workspace:
            - a snapshot project package
            - a build job manifest
            - a reserved artifacts directory

            Real APK compilation will be connected in the next stage.
        """.trimIndent()
    }

    private fun sanitizeFileName(value: String): String {
        return value.trim()
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), "-")
            .trim('-', '_')
    }

    @Serializable
    data class LocalBuildJob(
        val schemaVersion: Int = 1,
        val format: String = "firefly-local-build",
        val projectId: String,
        val projectName: String,
        val preparedAt: Long,
        val status: String,
        val buildRootPath: String,
        val workspacePath: String,
        val projectPackagePath: String,
        val jobFilePath: String,
        val buildLogPath: String,
        val artifactDirectoryPath: String,
        val expectedApkPath: String,
        val generatedProjectPath: String? = null,
        val generatedProjectReady: Boolean = false
    )

    private companion object {
        const val WORKSPACE_DIR_NAME = "workspace"
        const val ARTIFACTS_DIR_NAME = "artifacts"
        const val GENERATED_PROJECT_DIR_NAME = "generated-project"
        const val JOB_FILE_NAME = "build-job.json"
        const val BUILD_LOG_FILE_NAME = "build.log"
        const val README_FILE_NAME = "README.txt"
        const val STATUS_PREPARED = "prepared"
    }
}
