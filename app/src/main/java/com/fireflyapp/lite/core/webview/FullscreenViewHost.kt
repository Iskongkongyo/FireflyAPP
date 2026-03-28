package com.fireflyapp.lite.core.webview

import android.view.View

interface FullscreenViewHost {
    fun showFullscreenView(view: View): Boolean
    fun hideFullscreenView()
}
