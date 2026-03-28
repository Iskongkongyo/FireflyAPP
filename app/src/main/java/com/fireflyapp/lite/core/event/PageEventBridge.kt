package com.fireflyapp.lite.core.event

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class PageEventBridge(
    private val onSpaUrlChanged: (url: String, title: String) -> Unit,
    private val onPageTitleChanged: (title: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onSpaUrlChanged(url: String?, title: String?) {
        val safeUrl = url?.trim().orEmpty()
        val safeTitle = title?.trim().orEmpty()
        if (safeUrl.isBlank()) {
            return
        }
        mainHandler.post {
            onSpaUrlChanged(safeUrl, safeTitle)
        }
    }

    @JavascriptInterface
    fun onPageTitleChanged(title: String?) {
        val safeTitle = title?.trim().orEmpty()
        mainHandler.post {
            onPageTitleChanged(safeTitle)
        }
    }
}
