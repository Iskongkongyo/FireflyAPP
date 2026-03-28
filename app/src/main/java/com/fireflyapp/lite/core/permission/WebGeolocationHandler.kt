package com.fireflyapp.lite.core.permission

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.GeolocationPermissions
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.rule.UrlMatcher

class WebGeolocationHandler(
    private val fragment: Fragment,
    private val allowedHostsProvider: () -> List<String>,
    private val currentPageUrlProvider: () -> String?
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionStore = PersistentHostPermissionStore { fragment.context }
    private var pendingOrigin: String? = null
    private var pendingCallback: GeolocationPermissions.Callback? = null

    private val launcher: ActivityResultLauncher<Array<String>> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val origin = pendingOrigin
            val callback = pendingCallback
            pendingOrigin = null
            pendingCallback = null

            if (origin == null || callback == null) {
                return@registerForActivityResult
            }

            val granted = locationPermissions.any { permission ->
                result[permission] == true || hasPermission(permission)
            }
            Log.d(TAG, "Geolocation Android permission result: origin=$origin granted=$granted")
            if (granted) {
                GeolocationPermissions.getInstance().allow(origin)
            }
            callback.invoke(origin, granted, granted)
        }

    fun handle(origin: String?, callback: GeolocationPermissions.Callback?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { handle(origin, callback) }
            return
        }

        val safeOrigin = origin.orEmpty()
        val safeCallback = callback ?: return
        val uri = runCatching { Uri.parse(safeOrigin) }.getOrNull()
        val host = uri?.host?.lowercase()

        if (uri == null || host.isNullOrBlank() || !isAllowedOrigin(uri)) {
            Log.e(TAG, "Geolocation origin denied: origin=$origin currentPage=${currentPageUrlProvider()} allowedHosts=${allowedHostsProvider()}")
            safeCallback.invoke(safeOrigin, false, false)
            return
        }

        if (permissionStore.isApproved(PersistentHostPermissionStore.SCOPE_GEOLOCATION, host)) {
            requestAndroidPermissionOrGrant(safeOrigin, safeCallback)
            return
        }

        val context = fragment.context
        if (context == null || !fragment.isAdded) {
            safeCallback.invoke(safeOrigin, false, false)
            return
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.location_permission_title)
            .setMessage(context.getString(R.string.location_permission_message, host))
            .setCancelable(true)
            .setPositiveButton(R.string.permission_prompt_allow) { _, _ ->
                permissionStore.approve(PersistentHostPermissionStore.SCOPE_GEOLOCATION, host)
                requestAndroidPermissionOrGrant(safeOrigin, safeCallback)
            }
            .setNegativeButton(R.string.permission_prompt_cancel) { _, _ ->
                Log.d(TAG, "Geolocation denied by user: origin=$safeOrigin")
                safeCallback.invoke(safeOrigin, false, false)
            }
            .setOnCancelListener {
                safeCallback.invoke(safeOrigin, false, false)
            }
            .show()
    }

    fun cancelPending() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { cancelPending() }
            return
        }

        val origin = pendingOrigin
        val callback = pendingCallback
        pendingOrigin = null
        pendingCallback = null
        if (origin != null && callback != null) {
            callback.invoke(origin, false, false)
        }
    }

    private fun requestAndroidPermissionOrGrant(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        pendingOrigin?.let { previousOrigin ->
            pendingCallback?.invoke(previousOrigin, false, false)
        }
        pendingOrigin = origin
        pendingCallback = callback

        val deniedPermissions = locationPermissions.filterNot(::hasPermission)
        if (deniedPermissions.isEmpty()) {
            pendingOrigin = null
            pendingCallback = null
            Log.d(TAG, "Geolocation already granted: origin=$origin")
            GeolocationPermissions.getInstance().allow(origin)
            callback.invoke(origin, true, true)
            return
        }

        Log.d(TAG, "Requesting location permissions: ${deniedPermissions.joinToString()} for origin=$origin")
        launcher.launch(deniedPermissions.toTypedArray())
    }

    private fun isAllowedOrigin(origin: Uri): Boolean {
        val scheme = origin.scheme.orEmpty().lowercase()
        if (scheme !in setOf("http", "https")) {
            return false
        }

        val currentHost = runCatching {
            Uri.parse(currentPageUrlProvider().orEmpty()).host?.lowercase()
        }.getOrNull()
        val originHost = origin.host?.lowercase()
        if (!originHost.isNullOrBlank() && !currentHost.isNullOrBlank() && hostsMatch(originHost, currentHost)) {
            return true
        }

        val allowedHosts = buildList {
            addAll(allowedHostsProvider())
            if (!currentHost.isNullOrBlank()) {
                add(currentHost)
            }
        }
        return UrlMatcher.isHostAllowed(origin, allowedHosts)
    }

    private fun hasPermission(permission: String): Boolean {
        val context = fragment.context ?: return false
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hostsMatch(originHost: String, currentHost: String): Boolean {
        return originHost == currentHost ||
            originHost.endsWith(".$currentHost") ||
            currentHost.endsWith(".$originHost")
    }

    private companion object {
        val locationPermissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        const val TAG = "WebGeolocationHandler"
    }
}
