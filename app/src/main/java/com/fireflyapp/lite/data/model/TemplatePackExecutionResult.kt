package com.fireflyapp.lite.data.model

data class TemplatePackExecutionResult(
    val status: TemplatePackExecutionStatus,
    val message: String,
    val logPath: String? = null,
    val artifactPath: String? = null,
    val artifactCheck: TemplatePackArtifactCheck? = null
)

enum class TemplatePackExecutionStatus {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED
}
