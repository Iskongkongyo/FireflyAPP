package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TemplatePackHistoryEntry(
    val packedAt: Long = 0L,
    val artifactPath: String = "",
    val artifactFileName: String = "",
    val artifactSizeBytes: Long = 0L,
    val applicationLabel: String = "",
    val versionName: String = "",
    val versionCode: Int = 1,
    val packageName: String = "",
    val template: TemplateType = TemplateType.TOP_BAR,
    val signingSummary: String = ""
)
