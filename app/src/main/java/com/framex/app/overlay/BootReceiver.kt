package com.framex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Restarts the overlay service automatically after a device reboot
 * or after the app is updated (MY_PACKAGE_REPLACED).
 * Registered in AndroidManifest with RECEIVE_BOOT_COMPLETED permission.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in validActions) return

        // Only restart if the service was previously running (user had it enabled).
        // OverlayService.isRunning is an in-process StateFlow, so it resets to false on
        // a fresh boot. We rely on the SettingsRepository persisted preference instead.
        val prefs = context.getSharedPreferences("framex_settings", Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean("overlay_was_running", false)
        if (!wasRunning) return

        val serviceIntent = Intent(context, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
