package com.fireflyapp.lite.core.webview

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.fireflyapp.lite.data.model.BrowserConfig

object WebViewConfigurator {
    fun apply(webView: WebView, config: BrowserConfig) {
        webView.settings.apply {
            javaScriptEnabled = config.javaScriptEnabled
            domStorageEnabled = config.domStorageEnabled
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(true)
            allowFileAccess = false
            allowContentAccess = true

            if (config.userAgent.isNotBlank()) {
                userAgentString = config.userAgent
            }
        }

        applyNightMode(
            webView = webView,
            mode = config.nightMode
        )

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    private fun applyNightMode(webView: WebView, mode: String) {
        val normalizedMode = mode.trim().lowercase()
        val allowAlgorithmicDarkening = normalizedMode != "off"

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, allowAlgorithmicDarkening)
        }
    }
}
