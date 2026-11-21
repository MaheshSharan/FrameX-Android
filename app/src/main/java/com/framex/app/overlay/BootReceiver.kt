package com.framex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Restores the floating FPS overlay after a reboot or an app update if it was active
 * before. The settings are read asynchronously because the DataStore has no blocking read.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                restoreIfNeeded(appContext, action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun restoreIfNeeded(context: Context, action: String) {
        val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            settingsRepository.awaitSettingsLoaded()
        }
        if (loaded == null) {
            FrameXLog.w("Settings did not load in time; overlay not restored", tag = TAG)
            return
        }
        val wasRunning = settingsRepository.overlay.wasOverlayRunning()
        if (!shouldRestoreOverlay(action, wasRunning, Settings.canDrawOverlays(context))) return
        startOverlay(context)
    }

    private fun startOverlay(context: Context) {
        val serviceIntent = Intent(context, OverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: IllegalStateException) {
            // Android 12+ can refuse a background foreground-service start; the flag stays set
            // so the overlay is restored the next time the app is opened.
            FrameXLog.w("Overlay restore blocked by the system", e, tag = TAG)
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        const val LOAD_TIMEOUT_MS = 4_000L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
