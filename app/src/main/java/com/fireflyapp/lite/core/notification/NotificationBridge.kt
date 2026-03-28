package com.fireflyapp.lite.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fireflyapp.lite.R
import com.fireflyapp.lite.core.permission.PersistentHostPermissionStore
import com.fireflyapp.lite.core.rule.UrlMatcher
import kotlin.random.Random

class NotificationBridge(
    private val fragment: Fragment,
    private val allowedHostsProvider: () -> List<String>,
    private val currentPageUrlProvider: () -> String?,
    private val dispatchPermissionResult: (requestId: String, permission: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionStore = PersistentHostPermissionStore { fragment.context }
    private var pendingRequestId: String? = null
    private var pendingHost: String? = null

    private val launcher: ActivityResultLauncher<String> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val requestId = pendingRequestId
            val host = pendingHost
            pendingRequestId = null
            pendingHost = null

            if (requestId == null || host.isNullOrBlank()) {
                return@registerForActivityResult
            }

            if (granted) {
                permissionStore.approve(PersistentHostPermissionStore.SCOPE_NOTIFICATION, host)
                Log.d(TAG, "Notification Android permission granted: host=$host")
                dispatchPermissionResult(requestId, PERMISSION_GRANTED)
            } else {
                Log.d(TAG, "Notification Android permission denied: host=$host")
                dispatchPermissionResult(requestId, PERMISSION_DENIED)
            }
        }

    @JavascriptInterface
    fun getPermissionState(): String {
        val pageUri = currentPageUri() ?: return PERMISSION_DEFAULT
        val host = pageUri.host?.lowercase().orEmpty()
        if (host.isBlank() || !isTrustedPage(pageUri)) {
            return PERMISSION_DENIED
        }
        if (!permissionStore.isApproved(PersistentHostPermissionStore.SCOPE_NOTIFICATION, host)) {
            return PERMISSION_DEFAULT
        }
        return if (hasNotificationPermission()) {
            PERMISSION_GRANTED
        } else {
            PERMISSION_DENIED
        }
    }

    @JavascriptInterface
    fun requestPermission(requestId: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestPermission(requestId) }
            return
        }

        if (requestId.isNullOrBlank()) {
            Log.e(TAG, "requestPermission rejected: empty requestId")
            return
        }

        val pageUri = currentPageUri()
        val host = pageUri?.host?.lowercase()
        if (pageUri == null || host.isNullOrBlank() || !isTrustedPage(pageUri)) {
            Log.e(TAG, "requestPermission denied: pageUrl=${currentPageUrlProvider()} allowedHosts=${allowedHostsProvider()}")
            dispatchPermissionResult(requestId, PERMISSION_DENIED)
            return
        }

        if (permissionStore.isApproved(PersistentHostPermissionStore.SCOPE_NOTIFICATION, host) &&
            hasNotificationPermission()
        ) {
            dispatchPermissionResult(requestId, PERMISSION_GRANTED)
            return
        }

        val context = fragment.context
        if (context == null || !fragment.isAdded) {
            dispatchPermissionResult(requestId, PERMISSION_DEFAULT)
            return
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.notification_permission_title)
            .setMessage(context.getString(R.string.notification_permission_message, host))
            .setCancelable(true)
            .setPositiveButton(R.string.permission_prompt_allow) { _, _ ->
                requestAndroidPermissionOrGrant(requestId, host)
            }
            .setNegativeButton(R.string.permission_prompt_cancel) { _, _ ->
                dispatchPermissionResult(requestId, PERMISSION_DENIED)
            }
            .setOnCancelListener {
                dispatchPermissionResult(requestId, PERMISSION_DEFAULT)
            }
            .show()
    }

    @JavascriptInterface
    fun showNotification(title: String?, body: String?, tag: String?): Boolean {
        return runCatching {
            ensureNotificationChannel()

            val pageUri = currentPageUri() ?: return false
            val host = pageUri.host?.lowercase().orEmpty()
            if (
                host.isBlank() ||
                !permissionStore.isApproved(PersistentHostPermissionStore.SCOPE_NOTIFICATION, host) ||
                !isTrustedPage(pageUri) ||
                !hasNotificationPermission()
            ) {
                Log.e(TAG, "showNotification denied: host=$host pageUrl=${currentPageUrlProvider()}")
                return false
            }

            val context = fragment.context ?: return false
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name))
                .setContentText(body?.takeIf { it.isNotBlank() } ?: currentPageUrlProvider().orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body?.takeIf { it.isNotBlank() } ?: currentPageUrlProvider().orEmpty()))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(
                (tag?.hashCode() ?: Random.nextInt()).absoluteValue(),
                notification
            )
            Log.d(TAG, "showNotification success: host=$host title=$title")
            true
        }.getOrElse { throwable ->
            Log.e(TAG, "showNotification failed", throwable)
            false
        }
    }

    private fun requestAndroidPermissionOrGrant(requestId: String, host: String) {
        if (!requiresPostNotificationsPermission() || hasNotificationPermission()) {
            permissionStore.approve(PersistentHostPermissionStore.SCOPE_NOTIFICATION, host)
            dispatchPermissionResult(requestId, PERMISSION_GRANTED)
            return
        }

        pendingRequestId = requestId
        pendingHost = host
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasNotificationPermission(): Boolean {
        val context = fragment.context ?: return false
        if (!requiresPostNotificationsPermission()) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
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

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val context = fragment.context ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun Int.absoluteValue(): Int = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)

    private fun requiresPostNotificationsPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private companion object {
        const val TAG = "NotificationBridge"
        const val CHANNEL_ID = "firefly_web_notification"
        const val PERMISSION_DEFAULT = "default"
        const val PERMISSION_GRANTED = "granted"
        const val PERMISSION_DENIED = "denied"
    }
}
