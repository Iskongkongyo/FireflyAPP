package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SecurityConfig(
    val allowedHosts: List<String> = emptyList(),
    val allowExternalHosts: Boolean = true,
    val enableNativeKvBridge: Boolean = false,
    val kvTrustedHosts: List<String> = emptyList(),
    val nativeKvStorageMode: String = NATIVE_KV_STORAGE_MODE_PERSISTENT,
    val openOtherAppsMode: String = "ask",
    val sslErrorHandling: String = SSL_ERROR_HANDLING_STRICT
)

const val SSL_ERROR_HANDLING_STRICT = "strict"
const val SSL_ERROR_HANDLING_IGNORE = "ignore"
const val NATIVE_KV_STORAGE_MODE_PERSISTENT = "persistent"
const val NATIVE_KV_STORAGE_MODE_SESSION = "session"

val supportedSslErrorHandlingModes = setOf(
    SSL_ERROR_HANDLING_STRICT,
    SSL_ERROR_HANDLING_IGNORE
)

val supportedNativeKvStorageModes = setOf(
    NATIVE_KV_STORAGE_MODE_PERSISTENT,
    NATIVE_KV_STORAGE_MODE_SESSION
)
