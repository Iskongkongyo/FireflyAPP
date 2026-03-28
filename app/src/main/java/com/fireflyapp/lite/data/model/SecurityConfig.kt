package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SecurityConfig(
    val allowedHosts: List<String> = emptyList(),
    val allowExternalHosts: Boolean = true,
    val openOtherAppsMode: String = "ask"
)
