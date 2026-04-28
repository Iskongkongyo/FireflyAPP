package com.fireflyapp.lite.core.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal object DeclaredPermissionInspector {
    fun hasDeclaredPermission(context: Context, permission: String): Boolean {
        val requestedPermissions = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                ).requestedPermissions
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS
                ).requestedPermissions
            }
        }.getOrNull().orEmpty()

        return permission in requestedPermissions
    }
}
