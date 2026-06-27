package com.fireflyapp.lite.core.webview

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object PreviewWebConsole {
    private const val TAG = "PreviewWebConsole"
    private const val ASSET_PATH = "host-app/vconsole/vconsole-3.15.1.min.js"

    @Volatile
    private var cachedInjectionScript: String? = null

    fun install(context: Context, webView: WebView): Installation? {
        val script = loadInjectionScript(context.applicationContext) ?: return null
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return Installation(injectionScript = script)
        }

        return runCatching {
            Installation(
                injectionScript = script,
                scriptHandler = WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    script,
                    setOf("*")
                )
            )
        }.getOrElse { throwable ->
            Log.w(TAG, "Document-start injection unavailable; using fallback.", throwable)
            Installation(injectionScript = script)
        }
    }

    private fun loadInjectionScript(context: Context): String? {
        cachedInjectionScript?.let { return it }
        return synchronized(this) {
            cachedInjectionScript?.let { return@synchronized it }
            runCatching {
                val vConsoleSource = context.assets.open(ASSET_PATH)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                buildInjectionScript(vConsoleSource)
            }.onFailure { throwable ->
                Log.e(TAG, "Unable to load preview console asset.", throwable)
            }.getOrNull()?.also { script ->
                cachedInjectionScript = script
            }
        }
    }

    private fun buildInjectionScript(vConsoleSource: String): String {
        return """
            (function() {
                if (window.top !== window || window.__fireflyVConsoleLoading || window.__fireflyVConsole) {
                    return;
                }
                window.__fireflyVConsoleLoading = true;
                try {
                    var module;
                    var exports;
                    var define;
                    $vConsoleSource
                    if (typeof window.VConsole !== 'function') {
                        throw new Error('VConsole constructor was not exported');
                    }
                    window.__fireflyVConsole = new window.VConsole();
                } catch (error) {
                    window.__fireflyVConsoleLoading = false;
                    if (window.console && typeof window.console.error === 'function') {
                        window.console.error('[Firefly Preview] vConsole failed to initialize:', error);
                    }
                }
            })();
        """.trimIndent()
    }

    class Installation internal constructor(
        private val injectionScript: String,
        private val scriptHandler: ScriptHandler? = null
    ) {
        fun injectFallback(webView: WebView) {
            if (scriptHandler == null) {
                webView.evaluateJavascript(injectionScript, null)
            }
        }

        fun injectCurrentDocument(webView: WebView) {
            webView.evaluateJavascript(injectionScript, null)
        }

        fun dispose() {
            scriptHandler?.let { handler ->
                runCatching { handler.remove() }
            }
        }
    }
}
