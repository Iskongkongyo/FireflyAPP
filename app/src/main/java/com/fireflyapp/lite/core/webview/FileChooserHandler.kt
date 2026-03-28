package com.fireflyapp.lite.core.webview

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.permission.PersistentHostPermissionStore
import com.fireflyapp.lite.core.rule.UrlMatcher

class FileChooserHandler(
    fragment: Fragment,
    private val allowedHostsProvider: () -> List<String>,
    private val currentPageUrlProvider: () -> String?
) {
    private val fragment = fragment
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionStore = PersistentHostPermissionStore { fragment.context }
    private var pendingCallback: ValueCallback<Array<Uri>>? = null
    private var pendingParams: WebChromeClient.FileChooserParams? = null
    private var permissionDialog: AlertDialog? = null

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingCallback
            val params = pendingParams
            pendingCallback = null
            pendingParams = null

            if (callback == null) {
                return@registerForActivityResult
            }

            if (result.resultCode != Activity.RESULT_OK) {
                callback.onReceiveValue(null)
                return@registerForActivityResult
            }

            val parsedUris = parseResult(result.resultCode, result.data, params)
            callback.onReceiveValue(parsedUris)
        }

    fun openFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { openFileChooser(filePathCallback, fileChooserParams) }
            return true
        }

        pendingCallback?.onReceiveValue(null)
        permissionDialog?.dismiss()
        permissionDialog = null
        pendingCallback = filePathCallback
        pendingParams = fileChooserParams

        val pageUri = currentPageUri()
        val host = pageUri?.host?.lowercase()
        if (pageUri == null || host.isNullOrBlank() || !isTrustedPage(pageUri)) {
            cancelPending()
            return false
        }

        if (permissionStore.isApproved(PersistentHostPermissionStore.SCOPE_FILE_CHOOSER, host)) {
            return launchChooser(fileChooserParams)
        }

        val context = fragment.context
        if (context == null || !fragment.isAdded) {
            cancelPending()
            return false
        }

        permissionDialog = AlertDialog.Builder(context)
            .setTitle(R.string.file_upload_permission_title)
            .setMessage(context.getString(R.string.file_upload_permission_message, host))
            .setCancelable(true)
            .setPositiveButton(R.string.permission_prompt_allow) { _, _ ->
                permissionDialog = null
                permissionStore.approve(PersistentHostPermissionStore.SCOPE_FILE_CHOOSER, host)
                launchChooser(fileChooserParams)
            }
            .setNegativeButton(R.string.permission_prompt_cancel) { _, _ ->
                permissionDialog = null
                cancelPending()
            }
            .setOnCancelListener {
                permissionDialog = null
                cancelPending()
            }
            .show()
        return true
    }

    fun cancelPending() {
        permissionDialog?.dismiss()
        permissionDialog = null
        pendingCallback?.onReceiveValue(null)
        pendingCallback = null
        pendingParams = null
    }

    private fun launchChooser(fileChooserParams: WebChromeClient.FileChooserParams?): Boolean {
        val chooserIntent = buildChooserIntent(fileChooserParams)
        return try {
            fileChooserLauncher.launch(chooserIntent)
            true
        } catch (_: ActivityNotFoundException) {
            cancelPending()
            false
        }
    }

    private fun buildChooserIntent(fileChooserParams: WebChromeClient.FileChooserParams?): Intent {
        val mimeTypes = detectMimeTypes(fileChooserParams)
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            type = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
            if (mimeTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
        }
    }

    private fun detectMimeTypes(fileChooserParams: WebChromeClient.FileChooserParams?): Array<String> {
        val mimeTypes = fileChooserParams?.acceptTypes
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

        return if (mimeTypes.isEmpty()) {
            arrayOf("*/*")
        } else {
            mimeTypes.toTypedArray()
        }
    }

    private fun parseResult(
        resultCode: Int,
        data: Intent?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Array<Uri>? {
        val clipData = data?.clipData
        if (clipData != null && clipData.itemCount > 0) {
            return Array(clipData.itemCount) { index ->
                clipData.getItemAt(index).uri
            }.also(::persistReadPermissions)
        }

        val singleUri = data?.data
        if (singleUri != null) {
            return arrayOf(singleUri).also(::persistReadPermissions)
        }

        if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            return emptyArray()
        }

        return null
    }

    private fun persistReadPermissions(uris: Array<Uri>) {
        val contentResolver = fragment.context?.contentResolver ?: return
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
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
}
