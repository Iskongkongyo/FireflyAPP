package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppInfo(
    val name: String = "FireflyAPP",
    val template: TemplateType = TemplateType.TOP_BAR,
    val defaultUrl: String = "https://example.com"
)
