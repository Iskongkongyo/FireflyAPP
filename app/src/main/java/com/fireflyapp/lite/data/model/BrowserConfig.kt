package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BrowserConfig(
    val javaScriptEnabled: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val userAgent: String = "",
    val showLoadingOverlay: Boolean = true,
    val showPageProgressBar: Boolean = true,
    val showErrorView: Boolean = true,
    val backAction: String = "go_back_or_exit",
    val immersiveStatusBar: Boolean = false,
    val nightMode: String = "off"
)
