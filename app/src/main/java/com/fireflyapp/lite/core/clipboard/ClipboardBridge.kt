package com.fireflyapp.lite.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.permission.PersistentHostPermissionStore
import com.fireflyapp.lite.core.rule.UrlMatcher

class ClipboardBridge(
    private val fragment: Fragment,
    private val allowedHostsProvider: () -> List<String>,
    private val currentPageUrlProvider: () -> String?,
    private val dispatchReadResult: (requestId: String, text: String?, error: String?) -> Unit,
    private val dispatchWriteResult: (requestId: String, error: String?) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionStore = PersistentHostPermissionStore { fragment.context }

    @JavascriptInterface
    fun readText(requestId: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { readText(requestId) }
            return
        }

        if (requestId.isNullOrBlank()) {
            Log.e(TAG, "readText rejected: empty requestId")
            return
        }

        val pageUri = currentPageUri()
        val host = pageUri?.host?.lowercase()
        if (pageUri == null || host.isNullOrBlank() || !isTrustedPage(pageUri)) {
            Log.e(TAG, "readText denied: pageUrl=${currentPageUrlProvider()} allowedHosts=${allowedHostsProvider()}")
            dispatchReadResult(requestId, null, "clipboard access denied")
            return
        }

        if (permissionStore.isApproved(PersistentHostPermissionStore.SCOPE_CLIPBOARD, host)) {
            dispatchClipboardText(requestId)
            return
        }

        val context = fragment.context
        if (context == null || !fragment.isAdded) {
            dispatchReadResult(requestId, null, "clipboard access unavailable")
            return
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.clipboard_permission_title)
            .setMessage(context.getString(R.string.clipboard_permission_message, host))
            .setCancelable(true)
            .setPositiveButton(R.string.permission_prompt_allow) { _, _ ->
                permissionStore.approve(PersistentHostPermissionStore.SCOPE_CLIPBOARD, host)
                dispatchClipboardText(requestId)
            }
            .setNegativeButton(R.string.permission_prompt_cancel) { _, _ ->
                dispatchReadResult(requestId, null, "clipboard access denied")
            }
            .setOnCancelListener {
                dispatchReadResult(requestId, null, "clipboard access canceled")
            }
            .show()
    }

    @JavascriptInterface
    fun writeText(requestId: String?, text: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { writeText(requestId, text) }
            return
        }

        if (requestId.isNullOrBlank()) {
            Log.e(TAG, "writeText rejected: empty requestId")
            return
        }

        val pageUri = currentPageUri()
        val host = pageUri?.host?.lowercase()
        if (pageUri == null || host.isNullOrBlank() || !isTrustedPage(pageUri)) {
            Log.e(TAG, "writeText denied: pageUrl=${currentPageUrlProvider()} allowedHosts=${allowedHostsProvider()}")
            dispatchWriteResult(requestId, "clipboard access denied")
            return
        }

        if (permissionStore.isApproved(PersistentHostPermissionStore.SCOPE_CLIPBOARD, host)) {
            writeClipboardText(requestId, text.orEmpty())
            return
        }

        val context = fragment.context
        if (context == null || !fragment.isAdded) {
            dispatchWriteResult(requestId, "clipboard access unavailable")
            return
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.clipboard_permission_title)
            .setMessage(context.getString(R.string.clipboard_permission_message, host))
            .setCancelable(true)
            .setPositiveButton(R.string.permission_prompt_allow) { _, _ ->
                permissionStore.approve(PersistentHostPermissionStore.SCOPE_CLIPBOARD, host)
                writeClipboardText(requestId, text.orEmpty())
            }
            .setNegativeButton(R.string.permission_prompt_cancel) { _, _ ->
                dispatchWriteResult(requestId, "clipboard access denied")
            }
            .setOnCancelListener {
                dispatchWriteResult(requestId, "clipboard access canceled")
            }
            .show()
    }

    private fun dispatchClipboardText(requestId: String) {
        val context = fragment.context
        if (context == null) {
            dispatchReadResult(requestId, null, "clipboard unavailable")
            return
        }

        runCatching {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboardManager.primaryClip
            val text = clipData
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()

            Log.d(TAG, "readText success host=${Uri.parse(currentPageUrlProvider().orEmpty()).host} length=${text.length}")
            dispatchReadResult(requestId, text, null)
        }.onFailure { throwable ->
            Log.e(TAG, "readText failed", throwable)
            dispatchReadResult(requestId, null, throwable.message ?: "clipboard read failed")
        }
    }

    private fun writeClipboardText(requestId: String, text: String) {
        val context = fragment.context
        if (context == null) {
            dispatchWriteResult(requestId, "clipboard unavailable")
            return
        }

        runCatching {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText(CLIPBOARD_LABEL, text))
            Log.d(TAG, "writeText success host=${Uri.parse(currentPageUrlProvider().orEmpty()).host} length=${text.length}")
            dispatchWriteResult(requestId, null)
        }.onFailure { throwable ->
            Log.e(TAG, "writeText failed", throwable)
            dispatchWriteResult(requestId, throwable.message ?: "clipboard write failed")
        }
    }

    private fun isTrustedPage(pageUri: Uri): Boolean {
        val scheme = pageUri.scheme.orEmpty().lowercase()
        if (scheme !in setOf("http", "https")) {
            return false
        }
        return UrlMatcher.isHostAllowed(pageUri, allowedHostsProvider())
    }

    private fun currentPageUri(): Uri? {
        return runCatching {
            Uri.parse(currentPageUrlProvider().orEmpty())
        }.getOrNull()
    }

    private companion object {
        const val TAG = "ClipboardBridge"
        const val CLIPBOARD_LABEL = "FireflyClipboard"
    }
}
