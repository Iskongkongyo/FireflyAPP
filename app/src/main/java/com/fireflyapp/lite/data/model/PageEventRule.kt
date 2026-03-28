package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PageEventRule(
    val id: String = "",
    val enabled: Boolean = true,
    val trigger: String = "page_finished",
    val match: MatchRule = MatchRule(),
    val actions: List<PageEventAction> = emptyList()
)

@Serializable
data class PageEventAction(
    val type: String = "toast",
    val value: String = "",
    val url: String = "",
    val script: String = ""
)
