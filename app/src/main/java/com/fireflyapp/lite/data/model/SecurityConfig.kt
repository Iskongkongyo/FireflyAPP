package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SecurityConfig(
    val allowedHosts: List<String> = emptyList(),
    val allowExternalHosts: Boolean = true,
    val openOtherAppsMode: String = "ask",
    val sslErrorHandling: String = SSL_ERROR_HANDLING_STRICT
)

const val SSL_ERROR_HANDLING_STRICT = "strict"
const val SSL_ERROR_HANDLING_IGNORE = "ignore"

val supportedSslErrorHandlingModes = setOf(
    SSL_ERROR_HANDLING_STRICT,
    SSL_ERROR_HANDLING_IGNORE
)
