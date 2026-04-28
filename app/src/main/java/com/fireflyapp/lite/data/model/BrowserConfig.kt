package com.fireflyapp.lite.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BrowserConfig(
    val javaScriptEnabled: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val allowPageZoom: Boolean = false,
    val userAgent: String = "",
    val showLoadingOverlay: Boolean = true,
    val showPageProgressBar: Boolean = true,
    val showErrorView: Boolean = true,
    val backAction: String = "go_back_or_exit",
    val immersiveStatusBar: Boolean = false,
    val nightMode: String = "off",
    val screenOrientation: String = BROWSER_SCREEN_ORIENTATION_UNSPECIFIED
)

const val BROWSER_SCREEN_ORIENTATION_UNSPECIFIED = "unspecified"
const val BROWSER_SCREEN_ORIENTATION_PORTRAIT = "portrait"
const val BROWSER_SCREEN_ORIENTATION_LANDSCAPE = "landscape"

val supportedBrowserScreenOrientations = setOf(
    BROWSER_SCREEN_ORIENTATION_UNSPECIFIED,
    BROWSER_SCREEN_ORIENTATION_PORTRAIT,
    BROWSER_SCREEN_ORIENTATION_LANDSCAPE
)
