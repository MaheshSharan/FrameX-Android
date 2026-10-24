package com.framex.app.gaming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.framex.app.MainActivity
import com.framex.app.R
import com.framex.app.utils.FrameXLog

/**
 * Builds and manages the Gaming Mode crash-recovery notification when Shizuku is unavailable
 * on startup while suspended packages or system overrides still require restoration (Plan 4.4).
 */
object GamingNotificationHelper {

    private const val TAG = "GamingNotifHelper"
    const val RECOVERY_CHANNEL_ID = "framex_gaming_recovery_channel"
    const val RECOVERY_NOTIFICATION_ID = 1003

    fun showRecoveryNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    RECOVERY_CHANNEL_ID,
                    context.getString(R.string.gaming_notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, RECOVERY_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.gaming_recovery_notif_title))
                .setContentText(context.getString(R.string.gaming_recovery_notif_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            nm.notify(RECOVERY_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            FrameXLog.e("Failed to post recovery notification", e, tag = TAG)
        }
    }

    fun dismissRecoveryNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(RECOVERY_NOTIFICATION_ID)
        } catch (e: Exception) {
            FrameXLog.e("Failed to dismiss recovery notification", e, tag = TAG)
        }
    }
}
