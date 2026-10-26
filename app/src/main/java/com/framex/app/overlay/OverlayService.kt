package com.framex.app.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.app.Service
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.framex.app.R
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.SessionLogger
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Inject
    lateinit var overlayManager: OverlayManager

    @Inject
    lateinit var metricsEngine: MetricsEngine

    @Inject
    lateinit var sessionLogger: SessionLogger

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var visibilityObserverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** Held only while sampling is active; the timeout bounds it if teardown is ever missed. */
    private fun acquireSamplingWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FrameX:OverlaySampling")
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
            ?: return
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseSamplingWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: RuntimeException) {
            FrameXLog.w("Wake lock release failed", e)
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    overlayManager.onScreenOff()
                    // Keep telemetry engine running if an active session log is recording (Rule 4.1 / Plan 1.1).
                    if (!sessionLogger.isRecording.value) {
                        metricsEngine.setOverlayActive(false)
                        releaseSamplingWakeLock()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    overlayManager.onScreenOn()
                    if (_isRunning.value) {
                        metricsEngine.setOverlayActive(true)
                        acquireSamplingWakeLock()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        settingsRepository.setOverlayWasRunning(true)
        createNotificationChannel()
        val notification = OverlayNotificationBuilder.build(
            context = this,
            isOverlayVisible = overlayManager.isOverlayVisible.value
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            screenFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        acquireSamplingWakeLock()
        metricsEngine.setOverlayActive(true)
        overlayManager.showOverlay()
        observeOverlayVisibility()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                metricsEngine.setOverlayActive(true)
                overlayManager.showOverlay()
            }
            ACTION_STOP -> {
                if (sessionLogger.isRecording.value) {
                    sessionLogger.stopRecording()
                }
                settingsRepository.setOverlayWasRunning(false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HIDE_OVERLAY -> {
                overlayManager.hideOverlay()
            }
            ACTION_SHOW_OVERLAY -> {
                overlayManager.showOverlay()
            }
            ACTION_START_LOG -> {
                acquireSamplingWakeLock()
                sessionLogger.startRecording()
            }
            ACTION_STOP_LOG -> {
                sessionLogger.stopRecording()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayManager.handleOrientationChange(newConfig.orientation)
    }

    private fun observeOverlayVisibility() {
        visibilityObserverJob?.cancel()
        visibilityObserverJob = serviceScope.launch {
            overlayManager.isOverlayVisible.collect { isVisible ->
                val notification = OverlayNotificationBuilder.build(
                    context = this@OverlayService,
                    isOverlayVisible = isVisible
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        visibilityObserverJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            FrameXLog.w("screenStateReceiver was not registered or already unregistered", e)
        }
        if (sessionLogger.isRecording.value) {
            sessionLogger.stopRecording()
        }
        metricsEngine.setOverlayActive(false)
        releaseSamplingWakeLock()
        overlayManager.hideOverlay()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "framex_overlay_channel"
        private const val WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1000L
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.framex.app.ACTION_START"
        const val ACTION_STOP = "com.framex.app.ACTION_STOP"
        const val ACTION_HIDE_OVERLAY = "com.framex.app.ACTION_HIDE_OVERLAY"
        const val ACTION_SHOW_OVERLAY = "com.framex.app.ACTION_SHOW_OVERLAY"
        const val ACTION_START_LOG = "com.framex.app.ACTION_START_LOG"
        const val ACTION_STOP_LOG = "com.framex.app.ACTION_STOP_LOG"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }
}