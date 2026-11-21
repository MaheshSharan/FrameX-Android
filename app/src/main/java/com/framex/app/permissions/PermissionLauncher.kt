package com.framex.app.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.framex.app.R
import com.framex.app.utils.FrameXLog

/**
 * Intent for the system screen where the user grants this permission, or null for
 * POST_NOTIFICATIONS, which is requested through the runtime permission dialog instead.
 */
fun AppPermission.requestIntent(context: Context): Intent? {
    val packageUri = Uri.parse("package:${context.packageName}")
    return when (this) {
        AppPermission.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
        AppPermission.NOTIFICATIONS -> null
        AppPermission.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        AppPermission.BATTERY_OPT -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        AppPermission.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri)
        AppPermission.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        AppPermission.NOTIF_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

/**
 * Starts [intent], telling the user when the device has no screen for it. Settings screens
 * differ between OEM skins, so a failed launch is expected on some devices and must not crash.
 */
fun Context.startSettingsActivity(intent: Intent) {
    runCatching { startActivity(intent) }.onFailure { error ->
        FrameXLog.w("Unable to open ${intent.action}", error)
        Toast.makeText(this, R.string.perm_open_settings_failed, Toast.LENGTH_SHORT).show()
    }
}

/** Opens the Shizuku manager app, which is where the user starts the service and grants access. */
object ShizukuIntents {
    const val PACKAGE = "moe.shizuku.privileged.api"

    fun launch(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (intent == null) {
            Toast.makeText(context, R.string.perm_shizuku_not_found, Toast.LENGTH_SHORT).show()
        } else {
            context.startSettingsActivity(intent)
        }
    }
}
