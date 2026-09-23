package com.framex.app.repository

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of system-level permission grants.
 */
data class SystemPermissionsSnapshot(
    val hasOverlayPermission: Boolean,
    val hasUsageStatsPermission: Boolean,
    val hasBatteryOptDisabled: Boolean,
    val hasNotificationPermission: Boolean,
    val hasWriteSettingsPermission: Boolean
)

/**
 * Repository encapsulating platform permission and system setting checks.
 * Completely decouples Android Context and system services from ViewModels.
 */
@Singleton
class SystemPermissionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasWriteSettingsPermission(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasBatteryOptimizationDisabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun getPermissionsSnapshot(): SystemPermissionsSnapshot {
        return SystemPermissionsSnapshot(
            hasOverlayPermission = hasOverlayPermission(),
            hasUsageStatsPermission = hasUsageStatsPermission(),
            hasBatteryOptDisabled = hasBatteryOptimizationDisabled(),
            hasNotificationPermission = hasNotificationPermission(),
            hasWriteSettingsPermission = hasWriteSettingsPermission()
        )
    }

    fun isShizukuAppInstalled(): Boolean {
        return context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") != null
    }
}
