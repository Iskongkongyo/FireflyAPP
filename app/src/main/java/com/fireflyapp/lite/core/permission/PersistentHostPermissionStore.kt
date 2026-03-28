package com.fireflyapp.lite.core.permission

import android.content.Context

class PersistentHostPermissionStore(
    private val contextProvider: () -> Context?
) {
    private val memoryCache = mutableSetOf<String>()

    fun isApproved(scope: String, host: String): Boolean {
        val key = scopedKey(scope, host)
        if (key in memoryCache) {
            return true
        }
        val approved = preferences()?.getBoolean(key, false) == true
        if (approved) {
            memoryCache += key
        }
        return approved
    }

    fun approve(scope: String, host: String) {
        val key = scopedKey(scope, host)
        memoryCache += key
        preferences()?.edit()?.putBoolean(key, true)?.apply()
    }

    private fun scopedKey(scope: String, host: String): String {
        return "${scope.trim().lowercase()}:${host.trim().lowercase()}"
    }

    private fun preferences() = contextProvider()
        ?.applicationContext
        ?.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    companion object {
        const val SCOPE_CAMERA = "camera"
        const val SCOPE_CLIPBOARD = "clipboard"
        const val SCOPE_FILE_CHOOSER = "file_chooser"
        const val SCOPE_GEOLOCATION = "geolocation"
        const val SCOPE_MICROPHONE = "microphone"
        const val SCOPE_NOTIFICATION = "notification"

        private const val PREFERENCES_NAME = "firefly_runtime_permission_store"
    }
}
