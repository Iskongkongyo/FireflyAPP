package com.fireflyapp.lite.core.webview

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
import com.fireflyapp.lite.data.model.SSL_ERROR_HANDLING_IGNORE

class FireflyWebViewClient(
    private val appConfig: AppConfig,
    private val pageRuleResolver: PageRuleResolver,
    private val pageCallback: WebPageCallback?,
    private val openExternal: (Intent) -> Boolean,
    private val onPageLoadError: (PageLoadErrorState?) -> Unit,
    private val onPageLoadingChanged: ((Boolean) -> Unit)? = null,
    private val onPageStarted: ((String) -> Unit)? = null,
    private val onResolvedPageStateChanged: ((ResolvedPageState) -> Unit)? = null,
    private val onPageCommitVisible: ((WebView, String) -> Unit)? = null,
    private val onPageFinished: ((WebView, String) -> Unit)? = null
) : WebViewClient() {
    private var mainFrameError: PageLoadErrorState? = null
    private val resolvedPageInjectionApplier = ResolvedPageInjectionApplier()

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
        view?.let { resolvedPageInjectionApplier.apply(it, state) }
        if (view != null) {
            onPageFinished?.invoke(view, currentUrl)
        }
        onPageLoadingChanged?.invoke(false)
        if (mainFrameError == null) {
            onPageLoadError(null)
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        val currentView = view ?: return
        val currentUrl = url ?: return
        onPageCommitVisible?.invoke(currentView, currentUrl)
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
        if (appConfig.security.sslErrorHandling == SSL_ERROR_HANDLING_IGNORE) {
            handler?.proceed()
            return
        }
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

    private companion object {
        val internalSchemes = setOf("http", "https")
        val externalSchemes = setOf("tel", "mailto", "intent")
        val blockedSchemes = setOf("about", "blob", "data", "file", "javascript")
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
