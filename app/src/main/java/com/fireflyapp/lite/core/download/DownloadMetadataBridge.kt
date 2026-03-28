package com.fireflyapp.lite.core.download

import android.webkit.JavascriptInterface
import java.util.concurrent.ConcurrentHashMap

class DownloadMetadataBridge {
    private val suggestedFileNames = ConcurrentHashMap<String, String>()

    @JavascriptInterface
    fun rememberFileName(url: String?, fileName: String?) {
        val normalizedUrl = url?.trim().orEmpty()
        val normalizedFileName = fileName?.trim().orEmpty()
        if (normalizedUrl.isBlank() || normalizedFileName.isBlank()) {
            return
        }
        suggestedFileNames[normalizedUrl] = normalizedFileName
    }

    fun consumeSuggestedFileName(url: String?): String? {
        val normalizedUrl = url?.trim().orEmpty()
        if (normalizedUrl.isBlank()) {
            return null
        }
        return suggestedFileNames.remove(normalizedUrl)
    }
}
