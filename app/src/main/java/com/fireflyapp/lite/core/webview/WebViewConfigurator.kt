package com.fireflyapp.lite.core.webview

import android.content.res.Configuration
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
        val shouldUseDarkMode = when (normalizedMode) {
            "on" -> true
            "follow_theme" -> {
                val nightMask = webView.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }

            else -> false
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, shouldUseDarkMode)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            val forceDarkMode = when (normalizedMode) {
                "on" -> WebSettingsCompat.FORCE_DARK_ON
                "follow_theme" -> WebSettingsCompat.FORCE_DARK_AUTO
                else -> WebSettingsCompat.FORCE_DARK_OFF
            }
            WebSettingsCompat.setForceDark(webView.settings, forceDarkMode)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
            WebSettingsCompat.setForceDarkStrategy(
                webView.settings,
                WebSettingsCompat.DARK_STRATEGY_USER_AGENT_DARKENING_ONLY
            )
        }

        webView.setBackgroundColor(
            if (shouldUseDarkMode) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        )
    }
}
