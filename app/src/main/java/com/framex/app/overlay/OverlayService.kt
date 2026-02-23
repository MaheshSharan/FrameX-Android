package com.framex.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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

        // Acquire a partial WakeLock so the CPU never goes idle while the overlay is active.
        // This prevents vivo/IQOO game-boost from suspending the process mid-session.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FrameX::OverlayWakeLock"
        ).also { it.acquire() }

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
        // Release WakeLock only if it is still held to avoid "WakeLock under-locked" warnings.
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
