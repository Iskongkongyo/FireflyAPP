package com.fireflyapp.lite.core.download

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import com.fireflyapp.lite.core.rule.UrlMatcher
import com.fireflyapp.lite.core.download.DownloadHandler.DownloadEvent

class BlobDownloadBridge(
    private val downloadHandler: DownloadHandler,
    private val getCurrentPageUrl: () -> String?,
    private val allowedHostsProvider: () -> List<String>,
    private val onDownloadEvent: (DownloadEvent) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun beginBlobDownload(sessionId: String?, fileName: String?, mimeType: String?, totalChunks: Int): Boolean {
        return runCatching {
            val currentPageUrl = getCurrentPageUrl().orEmpty()
            val allowedHosts = allowedHostsProvider()
            val trusted = runCatching {
                val uri = Uri.parse(currentPageUrl)
                val scheme = uri.scheme.orEmpty().lowercase()
                scheme in setOf("http", "https") &&
                    UrlMatcher.isHostAllowed(uri, allowedHosts)
            }.getOrDefault(false)

            if (!trusted || sessionId.isNullOrBlank()) {
                val safeFileName = fileName?.takeIf { it.isNotBlank() }
                    ?: downloadHandler.guessFileName(currentPageUrl, null, mimeType)
                Log.e(
                    TAG,
                    "beginBlobDownload rejected. trusted=$trusted sessionId=$sessionId pageUrl=$currentPageUrl allowedHosts=$allowedHosts"
                )
                notifyEvent(DownloadEvent.Failure(safeFileName, "blob download rejected"))
                return false
            }

            val safeFileName = fileName?.takeIf { it.isNotBlank() }
                ?: downloadHandler.guessFileName(currentPageUrl, null, mimeType)

            val result = downloadHandler.createBlobDownloadSession(
                sessionId = sessionId,
                fileName = safeFileName,
                mimeType = mimeType,
                totalChunks = totalChunks,
                onEvent = ::notifyEvent
            )

            if (result.isSuccess) {
                Log.d(TAG, "beginBlobDownload success. sessionId=$sessionId fileName=$safeFileName mimeType=$mimeType totalChunks=$totalChunks")
                true
            } else {
                Log.e(TAG, "beginBlobDownload failed. sessionId=$sessionId", result.exceptionOrNull())
                notifyEvent(DownloadEvent.Failure(safeFileName, result.exceptionOrNull()?.message ?: "blob download failed"))
                false
            }
        }.onFailure { throwable ->
            val safeFileName = fileName?.takeIf { it.isNotBlank() }
                ?: downloadHandler.guessFileName(getCurrentPageUrl().orEmpty(), null, mimeType)
            Log.e(TAG, "beginBlobDownload crashed. sessionId=$sessionId fileName=$fileName mimeType=$mimeType", throwable)
            notifyEvent(DownloadEvent.Failure(safeFileName, throwable.message ?: "blob download failed"))
        }.getOrDefault(false)
    }

    @JavascriptInterface
    fun appendBlobChunk(sessionId: String?, base64Chunk: String?, isLastChunk: Boolean) {
        if (sessionId.isNullOrBlank() || base64Chunk.isNullOrBlank()) {
            Log.e(TAG, "appendBlobChunk rejected. sessionId=$sessionId hasChunk=${!base64Chunk.isNullOrBlank()}")
            notifyEvent(DownloadEvent.Failure("download", "blob chunk rejected"))
            return
        }

        val result = downloadHandler.appendBase64Chunk(sessionId, base64Chunk, isLastChunk)
        if (result.isSuccess && isLastChunk) {
            Log.d(TAG, "appendBlobChunk finished. sessionId=$sessionId")
        } else if (result.isFailure) {
            Log.e(TAG, "appendBlobChunk failed. sessionId=$sessionId", result.exceptionOrNull())
        }
    }

    @JavascriptInterface
    fun cancelBlobDownload(sessionId: String?, message: String?) {
        if (!sessionId.isNullOrBlank()) {
            downloadHandler.abortBlobDownloadSession(sessionId, message ?: "blob download canceled")
        }
        Log.e(TAG, "blob javascript failed: $message sessionId=$sessionId")
        if (sessionId.isNullOrBlank()) {
            notifyEvent(DownloadEvent.Failure("download", message ?: "blob download failed"))
        }
    }

    private fun notifyEvent(event: DownloadEvent) {
        mainHandler.post {
            onDownloadEvent(event)
        }
    }

    private companion object {
        const val TAG = "BlobDownloadBridge"
    }
}
