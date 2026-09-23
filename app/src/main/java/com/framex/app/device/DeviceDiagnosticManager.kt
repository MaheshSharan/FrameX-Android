package com.framex.app.device

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DeviceDiagnosticManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun isVivoOrIqoo(): Boolean {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        return manufacturer.contains("vivo") || manufacturer.contains("iqoo") ||
                brand.contains("vivo") || brand.contains("iqoo")
    }

    fun getDeviceModelInfo(): String {
        return "${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (${Build.BRAND})"
    }

    fun getMaxHardwareRefreshRate(): Float {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        var maxHz = 60.0f
        display?.supportedModes?.forEach { mode ->
            if (mode.refreshRate > maxHz) {
                maxHz = mode.refreshRate
            }
        }
        return maxHz
    }

    fun getAvailableMemoryBytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return 0L
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.availMem
    }

    fun getStorageInfo(): StorageInfo {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalGb = totalBytes / (1024L * 1024L * 1024L)
            val freeGb = freeBytes / (1024L * 1024L * 1024L)
            val usedGb = totalGb - freeGb
            StorageInfo(usedGb = usedGb, totalGb = totalGb, freeGb = freeGb)
        } catch (e: Exception) {
            StorageInfo()
        }
    }

    fun hasDndAccess(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun hasNotificationListenerAccess(): Boolean {
        return android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )?.contains(context.packageName) == true
    }

    fun hasWriteSettingsAccess(): Boolean {
        return android.provider.Settings.System.canWrite(context)
    }
}

data class StorageInfo(
    val usedGb: Long = 0L,
    val totalGb: Long = 0L,
    val freeGb: Long = 0L
)
