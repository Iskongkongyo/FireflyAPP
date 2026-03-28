package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PageRule(
    val match: MatchRule = MatchRule(),
    val overrides: PageOverride = PageOverride()
)
