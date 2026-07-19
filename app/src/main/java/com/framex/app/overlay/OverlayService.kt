package com.framex.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    @Inject
    lateinit var overlayManager: OverlayManager

    private var wakeLock: PowerManager.WakeLock? = null

    // Only hold the wake lock while the screen is actually on. Holding it for the entire
    // lifetime of the service (previous behavior) prevented the device from ever entering
    // deep sleep after screen-off, even when the overlay/game session was effectively idle.
    // See: https://github.com/MaheshSharan/FrameX-Android/issues/20
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> releaseWakeLock()
                Intent.ACTION_SCREEN_ON -> acquireWakeLock()
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FrameX::OverlayWakeLock"
        ).also { it.acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        // Persist the running state so BootReceiver can auto-restart after reboot.
        getSharedPreferences("framex_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("overlay_was_running", true).apply()
        createNotificationChannel()
        // MEDIA_PLAYBACK is the universally accepted, permission-free type for overlay services.
        // Combined with DATA_SYNC in the manifest so game-boost managers on MediaTek/Snapdragon
        // OEM ROMs (vivo, IQOO, Realme) treat this service as I/O-critical, not killable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(1, createNotification())
        }

        // Acquire the WakeLock only while the screen is on (i.e. an active session), and
        // register to release/reacquire it as the screen turns off/on so the device can
        // still enter deep sleep once the user locks it.
        acquireWakeLock()
        registerReceiver(
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )

        overlayManager.showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY: if the process is killed by OEM game-boost, the system will
        // attempt to restart this service automatically with a null intent.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        // Clear the running flag so BootReceiver does not restart an intentionally stopped service.
        getSharedPreferences("framex_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("overlay_was_running", false).apply()
        overlayManager.hideOverlay()
        runCatching { unregisterReceiver(screenStateReceiver) }
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayManager.handleOrientationChange(newConfig.orientation)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FrameX Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FrameX Overlay Active")
            .setContentText("Monitoring system performance")
            .setSmallIcon(com.framex.app.R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)       // Prevents the user (and game-boost) from swiping it away
            .setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "framex_overlay_channel"
        const val ACTION_STOP = "com.framex.app.ACTION_STOP_OVERLAY"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}