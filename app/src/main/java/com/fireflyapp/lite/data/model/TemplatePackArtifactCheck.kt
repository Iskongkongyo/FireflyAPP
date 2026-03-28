package com.fireflyapp.lite.data.model

data class TemplatePackArtifactCheck(
    val status: TemplatePackArtifactCheckStatus,
    val manifestCheck: String,
    val signatureCheck: String,
    val packageParserCheck: String
)

enum class TemplatePackArtifactCheckStatus {
    PASSED,
    WARNING,
    FAILED
}
