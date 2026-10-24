package com.framex.app.gaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.framex.app.MainActivity
import com.framex.app.R
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Minimal foreground service that keeps the FrameX process alive while Gaming Mode is active.
 *
 * When the user swipes FrameX away from Recents, [onTaskRemoved] fires. Instead of letting the OS
 * kill the process and leave apps suspended and DND stuck on, this service triggers an
 * emergency teardown on [GamingModeEngine.recoveryScope] (which survives [onDestroy]) and stops
 * itself only after restoration completes (Plan 5.3, 6.2).
 */
@AndroidEntryPoint
class GamingModeService : Service() {

    @Inject
    lateinit var gamingModeEngine: GamingModeEngine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            gamingModeEngine.recoveryScope.launch {
                try {
                    if (GamingModeEngine.isActive.value) {
                        gamingModeEngine.disableGamingMode()
                    }
                } catch (e: Exception) {
                    FrameXLog.e("ACTION_STOP disableGamingMode failed", e, tag = TAG)
                } finally {
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            FrameXLog.e("Failed to start foreground GamingModeService", e, tag = TAG)
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        FrameXLog.w("Task removed while GamingModeService running — triggering emergency teardown", tag = TAG)
        gamingModeEngine.recoveryScope.launch {
            try {
                gamingModeEngine.emergencyTeardownOnSwipeKill()
            } catch (e: Exception) {
                FrameXLog.e("Emergency teardown on swipe-kill failed", e, tag = TAG)
            } finally {
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.gaming_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.gaming_notif_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GamingModeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.gaming_notif_title_active))
            .setContentText(getString(R.string.gaming_notif_text_active))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .addAction(
                0,
                getString(R.string.gaming_notif_action_deactivate),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        private const val TAG = "GamingModeService"
        const val CHANNEL_ID = "framex_gaming_mode_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "com.framex.app.gaming.ACTION_STOP"
    }
}