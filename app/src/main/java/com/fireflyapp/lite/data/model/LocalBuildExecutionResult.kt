package com.fireflyapp.lite.data.model

data class LocalBuildExecutionResult(
    val status: LocalBuildExecutionStatus,
    val message: String,
    val command: String? = null,
    val logPath: String? = null,
    val artifactPath: String? = null
)

enum class LocalBuildExecutionStatus {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
    BLOCKED
}
