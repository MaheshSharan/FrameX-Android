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
 * Declared in the manifest with foregroundServiceType="specialUse" per Android 14+ requirements.
 * This service does no business logic—all operations delegate to [GamingModeEngine].
 */
@AndroidEntryPoint
class GamingModeService : Service() {

    @Inject
    lateinit var gamingModeEngine: GamingModeEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // =========================================================================
    // Lifecycle Callbacks
    // =========================================================================

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        createNotificationChannel()
        startForegroundServiceCompat()
        FrameXLog.i("GamingModeService created and foregrounded", tag = TAG)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return handleIntentAction(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        serviceScope.cancel()
        FrameXLog.i("GamingModeService destroyed", tag = TAG)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        triggerRecentsTeardown()
    }

    // =========================================================================
    // Service Command & Teardown Routines (IDE Navigation)
    // =========================================================================

    private fun handleIntentAction(intent: Intent?): Int {
        if (intent?.action == ACTION_STOP) {
            FrameXLog.i("Received ACTION_STOP, stopping service", tag = TAG)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun triggerRecentsTeardown() {
        FrameXLog.w("Task swiped from Recents - initiating immediate teardown", tag = TAG)
        serviceScope.launch {
            runCatching {
                gamingModeEngine.disableGamingMode()
            }.onFailure { error ->
                FrameXLog.e("Emergency deactivation failed on task removal", error, tag = TAG)
            }
            stopSelf()
        }
    }

    // =========================================================================
    // Foreground & Notification Helpers
    // =========================================================================

    private fun startForegroundServiceCompat() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gaming Mode Active")
            .setContentText("Background apps suspended · DND enabled")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    // =========================================================================
    // Static Constants & State
    // =========================================================================

    companion object {
        private const val TAG = "GamingModeService"

        const val CHANNEL_ID = "framex_gaming_channel"
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.framex.app.ACTION_STOP_GAMING_MODE"
        const val EXTRA_LAUNCHED_GAME_PKG = "extra_launched_game_pkg"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}