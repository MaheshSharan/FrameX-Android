package com.framex.app.gaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.framex.app.MainActivity
import com.framex.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the Gaming Mode process alive.
 *
 * Declared in the manifest with foregroundServiceType="specialUse" per the
 * Android 16 / OriginOS 6 requirements in the architecture PRD.
 *
 * This service intentionally does NO heavy lifting — all logic lives in
 * GamingModeEngine.  The service exists solely to hold the persistent
 * notification and prevent the process from being killed by OEM game-boost.
 */
@AndroidEntryPoint
class GamingModeService : Service() {

    @Inject
    lateinit var gamingModeEngine: GamingModeEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+ requires explicit FGS type in startForeground()
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // OriginOS 6 "Final Boss" Fix: If user swipes app from Recents, FGS is killed instantly.
        // We trigger immediate teardown to restore system apps before the process dies.
        serviceScope.launch {
            gamingModeEngine.disableGamingMode()
            stopSelf()
        }
    }

    // ---- Notification -------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FrameX Gaming Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while Gaming Mode is active"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gaming Mode Active")
            .setContentText("Background apps suspended · DND enabled")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
    }

    // ---- Companion ----------------------------------------------------------

    companion object {
        const val CHANNEL_ID = "framex_gaming_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.framex.app.ACTION_STOP_GAMING_MODE"
        const val EXTRA_LAUNCHED_GAME_PKG = "extra_launched_game_pkg"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}
