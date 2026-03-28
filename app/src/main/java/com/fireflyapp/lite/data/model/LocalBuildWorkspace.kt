package com.fireflyapp.lite.data.model

data class LocalBuildWorkspace(
    val projectId: String,
    val projectName: String,
    val buildRootPath: String,
    val workspacePath: String,
    val projectPackagePath: String,
    val jobFilePath: String,
    val buildLogPath: String,
    val artifactDirectoryPath: String,
    val expectedApkPath: String,
    val status: LocalBuildWorkspaceStatus,
    val generatedProjectPath: String? = null,
    val generatedProjectReady: Boolean = false,
    val preparedAt: Long? = null
)

enum class LocalBuildWorkspaceStatus {
    IDLE,
    PREPARED
}
