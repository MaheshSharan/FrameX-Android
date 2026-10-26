package com.framex.app.gaming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.framex.app.R

/**
 * Gaming Mode channels: a quiet one for the running-service notice and a high-priority one for
 * recovery alerts, which must be seen when restoration did not complete.
 */
object GamingChannels {
    const val GAMING_CHANNEL_ID = "framex_gaming_channel"
    const val ALERT_CHANNEL_ID = "framex_gaming_alert_channel"

    private val LEGACY_IDS = listOf("framex_gaming_mode_channel", "framex_gaming_recovery_channel")

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            GAMING_CHANNEL_ID,
            context.getString(R.string.gaming_notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.gaming_notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun ensureAlert(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.gaming_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    /** Removes the channels created by earlier builds. Safe to repeat. */
    fun deleteLegacy(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        LEGACY_IDS.forEach { nm.deleteNotificationChannel(it) }
    }
}
