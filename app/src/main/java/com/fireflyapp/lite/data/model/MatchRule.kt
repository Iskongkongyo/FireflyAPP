package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MatchRule(
    val urlEquals: String? = null,
    val urlStartsWith: String? = null,
    val urlContains: String? = null
)
