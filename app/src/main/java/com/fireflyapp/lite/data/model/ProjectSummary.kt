package com.fireflyapp.lite.data.model

data class ProjectSummary(
    val id: String,
    val name: String,
    val defaultUrl: String,
    val template: TemplateType,
    val updatedAt: Long,
    val applicationLabel: String,
    val versionName: String,
    val versionCode: Int,
    val packageName: String
)
