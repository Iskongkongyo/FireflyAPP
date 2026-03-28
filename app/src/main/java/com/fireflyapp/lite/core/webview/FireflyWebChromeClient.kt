package com.fireflyapp.lite.core.webview

import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class FireflyWebChromeClient(
    private val pageCallback: WebPageCallback?,
    private val onPageTitleChanged: ((String) -> Unit)? = null,
    private val openFileChooser: (
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ) -> Boolean,
    private val requestWebPermission: (PermissionRequest) -> Unit,
    private val cancelWebPermission: (PermissionRequest?) -> Unit,
    private val requestGeolocationPermission: (String?, GeolocationPermissions.Callback?) -> Unit,
    private val cancelGeolocationPermission: () -> Unit,
    private val showFullscreenView: (View) -> Boolean,
    private val hideFullscreenView: () -> Unit
) : WebChromeClient() {
    private var customViewCallback: CustomViewCallback? = null

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        val safeTitle = title.orEmpty()
        pageCallback?.onPageTitleChanged(safeTitle)
        onPageTitleChanged?.invoke(safeTitle)
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        pageCallback?.onPageProgressChanged(newProgress)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        val callback = filePathCallback ?: return false
        return openFileChooser(callback, fileChooserParams)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        val safeRequest = request ?: return
        Log.d(TAG, "onPermissionRequest origin=${safeRequest.origin} resources=${safeRequest.resources.joinToString()}")
        requestWebPermission(safeRequest)
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        Log.d(TAG, "onPermissionRequestCanceled origin=${request?.origin}")
        cancelWebPermission(request)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        Log.d(TAG, "onGeolocationPermissionsShowPrompt origin=$origin")
        requestGeolocationPermission(origin, callback)
    }

    override fun onGeolocationPermissionsHidePrompt() {
        Log.d(TAG, "onGeolocationPermissionsHidePrompt")
        cancelGeolocationPermission()
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null || callback == null) {
            super.onShowCustomView(view, callback)
            return
        }
        if (customViewCallback != null) {
            callback.onCustomViewHidden()
            return
        }
        if (!showFullscreenView(view)) {
            callback.onCustomViewHidden()
            return
        }
        customViewCallback = callback
    }

    override fun onHideCustomView() {
        val callback = customViewCallback ?: return
        hideFullscreenView()
        callback.onCustomViewHidden()
        customViewCallback = null
    }

    fun exitFullscreen(): Boolean {
        if (customViewCallback == null) {
            return false
        }
        onHideCustomView()
        return true
    }

    private companion object {
        const val TAG = "FireflyWebChromeClient"
    }
}
