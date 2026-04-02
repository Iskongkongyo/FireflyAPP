package com.fireflyapp.lite.core.webview

import android.webkit.WebView
import com.fireflyapp.lite.core.rule.ResolvedPageState
import org.json.JSONObject

class ResolvedPageInjectionApplier {
    fun apply(webView: WebView, state: ResolvedPageState) {
        applyBuiltInInteractionStyle(webView)
        applyFocusSuppressionStyle(webView, state)
        applyResolvedPageCss(webView, state)
        state.injectJs.forEach { script ->
            webView.evaluateJavascript(script, null)
        }
    }

    private fun applyBuiltInInteractionStyle(webView: WebView) {
        val builtInCss = JSONObject.quote(DEFAULT_INTERACTION_CSS)
        webView.evaluateJavascript(
            """
            (function(){
                if(document.getElementById('$BUILT_IN_STYLE_ID')){return;}
                var style=document.createElement('style');
                style.id='$BUILT_IN_STYLE_ID';
                style.innerHTML=$builtInCss;
                document.head.appendChild(style);
            })();
            """.trimIndent(),
            null
        )
    }

    private fun applyFocusSuppressionStyle(webView: WebView, state: ResolvedPageState) {
        if (state.suppressFocusHighlight) {
            val suppressCss = JSONObject.quote(FOCUS_SUPPRESSION_CSS)
            webView.evaluateJavascript(
                """
                (function(){
                    if(document.getElementById('$FOCUS_SUPPRESSION_STYLE_ID')){return;}
                    var style=document.createElement('style');
                    style.id='$FOCUS_SUPPRESSION_STYLE_ID';
                    style.innerHTML=$suppressCss;
                    document.head.appendChild(style);
                })();
                """.trimIndent(),
                null
            )
        } else {
            webView.evaluateJavascript(
                """
                (function(){
                    var style=document.getElementById('$FOCUS_SUPPRESSION_STYLE_ID');
                    if(style && style.parentNode){style.parentNode.removeChild(style);}
                })();
                """.trimIndent(),
                null
            )
        }
    }

    private fun applyResolvedPageCss(webView: WebView, state: ResolvedPageState) {
        val resolvedCss = state.injectCss.joinToString("\n").trim()
        if (resolvedCss.isBlank()) {
            webView.evaluateJavascript(
                """
                (function(){
                    var style=document.getElementById('$RESOLVED_PAGE_STYLE_ID');
                    if(style && style.parentNode){style.parentNode.removeChild(style);}
                })();
                """.trimIndent(),
                null
            )
            return
        }

        val escapedCss = JSONObject.quote(resolvedCss)
        webView.evaluateJavascript(
            """
            (function(){
                var style=document.getElementById('$RESOLVED_PAGE_STYLE_ID');
                if(!style){
                    style=document.createElement('style');
                    style.id='$RESOLVED_PAGE_STYLE_ID';
                    document.head.appendChild(style);
                }
                style.innerHTML=$escapedCss;
            })();
            """.trimIndent(),
            null
        )
    }

    private companion object {
        const val BUILT_IN_STYLE_ID = "firefly-built-in-style"
        const val FOCUS_SUPPRESSION_STYLE_ID = "firefly-focus-suppression-style"
        const val RESOLVED_PAGE_STYLE_ID = "firefly-resolved-page-style"
        const val DEFAULT_INTERACTION_CSS =
            "*{-webkit-tap-highlight-color:transparent!important;}" +
            "a,button,img,input,textarea,select{-webkit-tap-highlight-color:transparent!important;}"
        const val FOCUS_SUPPRESSION_CSS =
            "*:focus,*:active{" +
            "outline:none!important;" +
            "box-shadow:none!important;" +
            "-webkit-tap-highlight-color:transparent!important;" +
            "}"
    }
}
