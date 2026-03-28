package com.fireflyapp.lite.core.webview

import com.fireflyapp.lite.core.rule.ResolvedPageState

interface WebPageCallback {
    fun onPageTitleChanged(title: String)
    fun onPageProgressChanged(progress: Int)
    fun onPageStateResolved(state: ResolvedPageState)
}
