package com.fireflyapp.lite.core.webview

import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.net.Uri
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.rule.PageRuleResolver
import com.fireflyapp.lite.core.rule.ResolvedPageState
import com.fireflyapp.lite.core.rule.UrlMatcher
import com.fireflyapp.lite.data.model.AppConfig
import org.json.JSONObject

class FireflyWebViewClient(
    private val appConfig: AppConfig,
    private val pageRuleResolver: PageRuleResolver,
    private val pageCallback: WebPageCallback?,
    private val openExternal: (Intent) -> Boolean,
    private val onPageLoadError: (PageLoadErrorState?) -> Unit,
    private val onPageLoadingChanged: ((Boolean) -> Unit)? = null,
    private val onPageStarted: ((String) -> Unit)? = null,
    private val onResolvedPageStateChanged: ((ResolvedPageState) -> Unit)? = null,
    private val onPageFinished: ((WebView, String) -> Unit)? = null
) : WebViewClient() {
    private var mainFrameError: PageLoadErrorState? = null

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val targetUri = request?.url ?: return false
        if (request.isForMainFrame.not()) {
            return false
        }

        val scheme = targetUri.scheme.orEmpty().lowercase()
        if (scheme in externalSchemes) {
            return handleExternalUri(targetUri)
        }
        if (scheme !in internalSchemes) {
            return if (scheme in blockedSchemes) {
                true
            } else {
                handleExternalUri(targetUri)
            }
        }

        val targetUrl = targetUri.toString()
        val state = pageRuleResolver.resolve(targetUrl)
        if (state.openExternal) {
            return handleExternalUri(targetUri)
        }

        return when {
            UrlMatcher.isHostAllowed(targetUri, appConfig.security.allowedHosts) -> false
            appConfig.security.allowExternalHosts -> handleExternalUri(targetUri)
            else -> true
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        mainFrameError = null
        onPageLoadingChanged?.invoke(true)
        url?.let {
            onPageStarted?.invoke(it)
            val state = pageRuleResolver.resolve(it)
            pageCallback?.onPageStateResolved(state)
            onResolvedPageStateChanged?.invoke(state)
            onPageLoadError(null)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val currentUrl = url ?: return
        val state = pageRuleResolver.resolve(currentUrl)
        pageCallback?.onPageStateResolved(state)
        onResolvedPageStateChanged?.invoke(state)
        view?.applyResolvedInjections(state)
        if (view != null) {
            onPageFinished?.invoke(view, currentUrl)
        }
        onPageLoadingChanged?.invoke(false)
        if (mainFrameError == null) {
            onPageLoadError(null)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            mainFrameError = mapResourceError(error?.errorCode)
            onPageLoadingChanged?.invoke(false)
            onPageLoadError(mainFrameError ?: PageLoadErrorState.Generic)
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
            mainFrameError = mapHttpError(errorResponse?.statusCode ?: 0)
            onPageLoadingChanged?.invoke(false)
            onPageLoadError(mainFrameError ?: PageLoadErrorState.Generic)
        }
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        handler?.cancel()
        mainFrameError = PageLoadErrorState.Certificate
        onPageLoadingChanged?.invoke(false)
        onPageLoadError(PageLoadErrorState.Certificate)
    }

    private fun handleExternalUri(uri: Uri): Boolean {
        val intent = when (uri.scheme.orEmpty().lowercase()) {
            "intent" -> Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            else -> Intent(Intent.ACTION_VIEW, uri)
        }.apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        openExternal(intent)
        return true
    }

    private fun WebView.applyResolvedInjections(state: ResolvedPageState) {
        val builtInCss = JSONObject.quote(DEFAULT_INTERACTION_CSS)
        evaluateJavascript(
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
        if (shouldApplyNightMode(this)) {
            val darkCss = JSONObject.quote(FORCED_DARK_CSS)
            val enhancedDarkCss = JSONObject.quote(ENHANCED_FORCED_DARK_CSS)
            evaluateJavascript(
                """
                (function(){
                    var style=document.getElementById('$FORCED_DARK_STYLE_ID');
                    if(!style){
                        style=document.createElement('style');
                        style.id='$FORCED_DARK_STYLE_ID';
                        document.head.appendChild(style);
                    }
                    style.innerHTML=$darkCss;
                    if(document.documentElement){
                        document.documentElement.setAttribute('data-firefly-dark','true');
                        document.documentElement.style.colorScheme='dark';
                    }
                    var meta=document.querySelector('meta[name="color-scheme"]');
                    if(!meta){
                        meta=document.createElement('meta');
                        meta.name='color-scheme';
                        document.head.appendChild(meta);
                    }
                    meta.content='dark light';
                    function ffParseColor(value){
                        if(!value){return null;}
                        var match=value.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
                        if(!match){return null;}
                        return {
                            r: parseInt(match[1], 10),
                            g: parseInt(match[2], 10),
                            b: parseInt(match[3], 10)
                        };
                    }
                    function ffBrightness(color){
                        if(!color){return 0;}
                        return (color.r * 299 + color.g * 587 + color.b * 114) / 1000;
                    }
                    function ffSolidBackground(node){
                        var current=node;
                        while(current){
                            var bg=window.getComputedStyle(current).backgroundColor;
                            if(bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent'){
                                return bg;
                            }
                            current=current.parentElement;
                        }
                        return null;
                    }
                    function ffSampleBrightness(x, y){
                        var element=document.elementFromPoint(x, y);
                        return ffBrightness(ffParseColor(ffSolidBackground(element)));
                    }
                    var sampleX=Math.max(0, Math.floor(window.innerWidth / 2));
                    var topY=Math.max(0, Math.floor(window.innerHeight * 0.2));
                    var middleY=Math.max(0, Math.floor(window.innerHeight * 0.5));
                    var bottomY=Math.max(0, Math.floor(window.innerHeight * 0.8));
                    var bodyBg=ffBrightness(ffParseColor(ffSolidBackground(document.body)));
                    var htmlBg=ffBrightness(ffParseColor(ffSolidBackground(document.documentElement)));
                    var samples=[bodyBg, htmlBg, ffSampleBrightness(sampleX, topY), ffSampleBrightness(sampleX, middleY), ffSampleBrightness(sampleX, bottomY)];
                    var shouldEnhance=samples.some(function(value){ return value >= 190; });
                    var enhanced=document.getElementById('$ENHANCED_FORCED_DARK_STYLE_ID');
                    if(shouldEnhance){
                        if(!enhanced){
                            enhanced=document.createElement('style');
                            enhanced.id='$ENHANCED_FORCED_DARK_STYLE_ID';
                            document.head.appendChild(enhanced);
                        }
                        enhanced.innerHTML=$enhancedDarkCss;
                    }else if(enhanced && enhanced.parentNode){
                        enhanced.parentNode.removeChild(enhanced);
                    }
                })();
                """.trimIndent(),
                null
            )
        } else {
            evaluateJavascript(
                """
                (function(){
                    var style=document.getElementById('$FORCED_DARK_STYLE_ID');
                    if(style && style.parentNode){style.parentNode.removeChild(style);}
                    var enhanced=document.getElementById('$ENHANCED_FORCED_DARK_STYLE_ID');
                    if(enhanced && enhanced.parentNode){enhanced.parentNode.removeChild(enhanced);}
                    var meta=document.querySelector('meta[name="color-scheme"]');
                    if(meta && meta.parentNode){meta.parentNode.removeChild(meta);}
                    if(document.documentElement){
                        document.documentElement.removeAttribute('data-firefly-dark');
                        document.documentElement.style.colorScheme='';
                    }
                })();
                """.trimIndent(),
                null
            )
        }
        if (state.suppressFocusHighlight) {
            val suppressCss = JSONObject.quote(FOCUS_SUPPRESSION_CSS)
            evaluateJavascript(
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
            evaluateJavascript(
                """
                (function(){
                    var style=document.getElementById('$FOCUS_SUPPRESSION_STYLE_ID');
                    if(style && style.parentNode){style.parentNode.removeChild(style);}
                })();
                """.trimIndent(),
                null
            )
        }
        state.injectCss.forEach { css ->
            val escapedCss = JSONObject.quote(css)
            evaluateJavascript(
                "(function(){var style=document.createElement('style');style.innerHTML=$escapedCss;document.head.appendChild(style);})();",
                null
            )
        }
        state.injectJs.forEach { script ->
            evaluateJavascript(script, null)
        }
    }

    private companion object {
        val internalSchemes = setOf("http", "https")
        val externalSchemes = setOf("tel", "mailto", "intent")
        val blockedSchemes = setOf("about", "blob", "data", "file", "javascript")
        const val BUILT_IN_STYLE_ID = "firefly-built-in-style"
        const val FORCED_DARK_STYLE_ID = "firefly-forced-dark-style"
        const val ENHANCED_FORCED_DARK_STYLE_ID = "firefly-enhanced-forced-dark-style"
        const val FOCUS_SUPPRESSION_STYLE_ID = "firefly-focus-suppression-style"
        const val DEFAULT_INTERACTION_CSS =
            "*{-webkit-tap-highlight-color:transparent!important;}" +
            "a,button,img,input,textarea,select{-webkit-tap-highlight-color:transparent!important;}"
        const val FORCED_DARK_CSS =
            "html[data-firefly-dark='true']{" +
            "background-color:#111827!important;" +
            "color-scheme:dark!important;" +
            "}" +
            "html[data-firefly-dark='true'] body{" +
            "background-color:#111827!important;" +
            "}"
        const val ENHANCED_FORCED_DARK_CSS =
            "html[data-firefly-dark='true']{" +
            "filter:invert(1) hue-rotate(180deg)!important;" +
            "background:#111827!important;" +
            "}" +
            "html[data-firefly-dark='true'] img," +
            "html[data-firefly-dark='true'] video," +
            "html[data-firefly-dark='true'] picture," +
            "html[data-firefly-dark='true'] canvas," +
            "html[data-firefly-dark='true'] svg," +
            "html[data-firefly-dark='true'] iframe{" +
            "filter:invert(1) hue-rotate(180deg)!important;" +
            "}" +
            "html[data-firefly-dark='true'] [style*='background-image']{" +
            "filter:invert(1) hue-rotate(180deg)!important;" +
            "}"
        const val FOCUS_SUPPRESSION_CSS =
            "*:focus,*:active{" +
            "outline:none!important;" +
            "box-shadow:none!important;" +
            "-webkit-tap-highlight-color:transparent!important;" +
            "}"
    }

    private fun shouldApplyNightMode(webView: WebView): Boolean {
        return when (appConfig.browser.nightMode.trim().lowercase()) {
            "on" -> true
            "follow_theme" -> {
                val nightMask = webView.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }

            else -> false
        }
    }

    sealed class PageLoadErrorState(val titleRes: Int, val messageRes: Int) {
        data object NoNetwork : PageLoadErrorState(
            titleRes = R.string.web_error_no_network_title,
            messageRes = R.string.web_error_no_network_message
        )

        data object Timeout : PageLoadErrorState(
            titleRes = R.string.web_error_timeout_title,
            messageRes = R.string.web_error_timeout_message
        )

        data object NotFound : PageLoadErrorState(
            titleRes = R.string.web_error_not_found_title,
            messageRes = R.string.web_error_not_found_message
        )

        data object Certificate : PageLoadErrorState(
            titleRes = R.string.web_error_certificate_title,
            messageRes = R.string.web_error_certificate_message
        )

        data object Generic : PageLoadErrorState(
            titleRes = R.string.web_error_generic_title,
            messageRes = R.string.web_error_generic_message
        )
    }

    private fun mapResourceError(errorCode: Int?): PageLoadErrorState {
        return when (errorCode) {
            ERROR_TIMEOUT -> PageLoadErrorState.Timeout
            ERROR_CONNECT,
            ERROR_HOST_LOOKUP -> PageLoadErrorState.NoNetwork
            ERROR_FAILED_SSL_HANDSHAKE -> PageLoadErrorState.Certificate
            else -> PageLoadErrorState.Generic
        }
    }

    private fun mapHttpError(statusCode: Int): PageLoadErrorState {
        return when (statusCode) {
            404 -> PageLoadErrorState.NotFound
            else -> PageLoadErrorState.Generic
        }
    }
}
