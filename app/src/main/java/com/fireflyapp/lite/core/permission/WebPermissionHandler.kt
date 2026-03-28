package com.fireflyapp.lite.core.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.util.Log
import android.webkit.PermissionRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.rule.UrlMatcher

class WebPermissionHandler(
    private val fragment: Fragment,
    private val allowedHostsProvider: () -> List<String>,
    private val currentPageUrlProvider: () -> String?
) {
    private var pendingRequest: PermissionRequest? = null
    private var permissionDialog: AlertDialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionStore = PersistentHostPermissionStore { fragment.context }

    private val launcher: ActivityResultLauncher<Array<String>> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val request = pendingRequest ?: return@registerForActivityResult
            pendingRequest = null

            val grantedResources = request.resources.filter { resource ->
                val permission = mapToAndroidPermission(resource) ?: return@filter false
                result[permission] == true || hasPermission(permission)
            }.toTypedArray()

            if (grantedResources.isNotEmpty()) {
                request.grant(grantedResources)
            } else {
                request.deny()
            }
        }

    fun handle(request: PermissionRequest) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { handle(request) }
            return
        }

        if (!isAllowedOrigin(request.origin)) {
            Log.e(TAG, "Permission origin denied: origin=${request.origin} currentPage=${currentPageUrlProvider()} allowedHosts=${allowedHostsProvider()}")
            request.deny()
            return
        }

        val requestedResources = request.resources
        val androidPermissions = requestedResources
            .mapNotNull(::mapToAndroidPermission)
            .distinct()

        if (androidPermissions.isEmpty()) {
            Log.e(TAG, "Permission resources unsupported: ${request.resources.joinToString()}")
            request.deny()
            return
        }

        pendingRequest?.deny()
        permissionDialog?.dismiss()
        permissionDialog = null
        pendingRequest = request

        val deniedPermissions = androidPermissions.filterNot(::hasPermission)
        if (deniedPermissions.isEmpty()) {
            pendingRequest = null
            Log.d(TAG, "Permission already granted: origin=${request.origin} resources=${requestedResources.joinToString()}")
            request.grant(requestedResources.filter { mapToAndroidPermission(it) != null }.toTypedArray())
            return
        }

        val host = request.origin?.host?.lowercase()
        if (host.isNullOrBlank()) {
            pendingRequest = null
            request.deny()
            return
        }

        if (arePermissionsApproved(host, requestedResources)) {
            Log.d(TAG, "Permission host prompt already approved: host=$host resources=${requestedResources.joinToString()}")
            launcher.launch(deniedPermissions.toTypedArray())
            return
        }

        val context = fragment.context
        if (context == null || !fragment.isAdded) {
            pendingRequest = null
            request.deny()
            return
        }

        permissionDialog = AlertDialog.Builder(context)
            .setTitle(permissionDialogTitleRes(requestedResources))
            .setMessage(context.getString(permissionDialogMessageRes(requestedResources), host))
            .setCancelable(true)
            .setPositiveButton(R.string.permission_prompt_allow) { _, _ ->
                permissionDialog = null
                approvePermissions(host, requestedResources)
                launcher.launch(deniedPermissions.toTypedArray())
            }
            .setNegativeButton(R.string.permission_prompt_cancel) { _, _ ->
                permissionDialog = null
                pendingRequest = null
                request.deny()
            }
            .setOnCancelListener {
                permissionDialog = null
                pendingRequest = null
                request.deny()
            }
            .show()

        Log.d(TAG, "Showing permission prompt: host=$host resources=${requestedResources.joinToString()}")
    }

    fun onCanceled(request: PermissionRequest?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onCanceled(request) }
            return
        }

        if (request != null && pendingRequest === request) {
            permissionDialog?.dismiss()
            permissionDialog = null
            pendingRequest = null
        }
    }

    fun cancelPending() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { cancelPending() }
            return
        }

        permissionDialog?.dismiss()
        permissionDialog = null
        pendingRequest?.deny()
        pendingRequest = null
    }

    private fun isAllowedOrigin(origin: Uri?): Boolean {
        val uri = origin ?: return false
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme !in setOf("http", "https")) {
            return false
        }

        val currentHost = runCatching {
            Uri.parse(currentPageUrlProvider().orEmpty()).host?.lowercase()
        }.getOrNull()

        val originHost = uri.host?.lowercase()
        if (!originHost.isNullOrBlank() && !currentHost.isNullOrBlank() && hostsMatch(originHost, currentHost)) {
            return true
        }

        val allowedHosts = buildList {
            addAll(allowedHostsProvider())
            if (!currentHost.isNullOrBlank()) {
                add(currentHost)
            }
        }
        return UrlMatcher.isHostAllowed(uri, allowedHosts)
    }

    private fun hasPermission(permission: String): Boolean {
        val context = fragment.context ?: return false
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun mapToAndroidPermission(resource: String): String? {
        return when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
            else -> null
        }
    }

    private fun permissionDialogTitleRes(resources: Array<String>): Int {
        val hasVideo = PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources
        val hasAudio = PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources
        return when {
            hasVideo && hasAudio -> R.string.camera_microphone_permission_title
            hasVideo -> R.string.camera_permission_title
            hasAudio -> R.string.microphone_permission_title
            else -> R.string.camera_microphone_permission_title
        }
    }

    private fun permissionDialogMessageRes(resources: Array<String>): Int {
        val hasVideo = PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources
        val hasAudio = PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources
        return when {
            hasVideo && hasAudio -> R.string.camera_microphone_permission_message
            hasVideo -> R.string.camera_permission_message
            hasAudio -> R.string.microphone_permission_message
            else -> R.string.camera_microphone_permission_message
        }
    }

    private fun arePermissionsApproved(host: String, resources: Array<String>): Boolean {
        val scopes = resources.mapNotNull(::mapToScope).distinct()
        return scopes.isNotEmpty() && scopes.all { scope -> permissionStore.isApproved(scope, host) }
    }

    private fun approvePermissions(host: String, resources: Array<String>) {
        resources.mapNotNull(::mapToScope).distinct().forEach { scope ->
            permissionStore.approve(scope, host)
        }
    }

    private fun mapToScope(resource: String): String? {
        return when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> PersistentHostPermissionStore.SCOPE_CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> PersistentHostPermissionStore.SCOPE_MICROPHONE
            else -> null
        }
    }

    private fun hostsMatch(originHost: String, currentHost: String): Boolean {
        return originHost == currentHost ||
            originHost.endsWith(".$currentHost") ||
            currentHost.endsWith(".$originHost")
    }

    private companion object {
        const val TAG = "WebPermissionHandler"
    }
}
