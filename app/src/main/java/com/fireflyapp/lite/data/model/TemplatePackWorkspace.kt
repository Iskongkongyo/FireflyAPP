package com.fireflyapp.lite.data.model

data class TemplatePackWorkspace(
    val projectId: String,
    val projectName: String,
    val applicationLabel: String,
    val versionName: String,
    val versionCode: Int,
    val packageName: String,
    val artifactFileName: String,
    val packRootPath: String,
    val templateSourcePath: String,
    val unpackedApkPath: String,
    val packJobPath: String,
    val packLogPath: String,
    val unsignedApkPath: String,
    val alignedApkPath: String,
    val signedApkPath: String,
    val sourceType: TemplateSourceType,
    val status: TemplatePackWorkspaceStatus,
    val preparedAt: Long? = null
)

enum class TemplatePackWorkspaceStatus {
    IDLE,
    PREPARED
}

enum class TemplateSourceType {
    BUNDLED_ASSET,
    INSTALLED_APP
}
