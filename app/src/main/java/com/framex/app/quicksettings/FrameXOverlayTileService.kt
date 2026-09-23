package com.framex.app.quicksettings

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.framex.app.MainActivity
import com.framex.app.R
import com.framex.app.overlay.OverlayService
import com.framex.app.utils.FrameXLog

/**
 * System Quick Settings Tile allowing users to toggle the FrameX performance overlay
 * directly from the notification shade / Quick Settings panel.
 *
 * Implements a 2-state toggle ([Tile.STATE_ACTIVE] vs [Tile.STATE_INACTIVE]) bound to
 * [OverlayService.isRunning]. Uses [unlockAndRun] on keyguard and safe [PendingIntent]
 * collapsing on Android 14+ (API 34+).
 */
class FrameXOverlayTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun {
                handleTileClick()
            }
        } else {
            handleTileClick()
        }
    }

    private fun handleTileClick() {
        if (!Settings.canDrawOverlays(this)) {
            FrameXLog.w("Overlay permission missing. Redirecting user to application.")
            openAppForPermission()
            return
        }

        if (OverlayService.isRunning.value) {
            val stopIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            val startIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = OverlayService.isRunning.value

        tile.icon = Icon.createWithResource(this, R.drawable.ic_quick_tile_overlay)
        tile.label = getString(R.string.quick_tile_overlay)
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        val statusText = getString(
            if (isRunning) R.string.quick_tile_overlay_active else R.string.quick_tile_overlay_inactive
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = statusText
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = statusText
        }

        tile.updateTile()
    }

    private fun openAppForPermission() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    companion object {
        /**
         * Requests the system to refresh the listening state of this TileService,
         * ensuring the Quick Settings tile updates immediately when the overlay
         * is toggled from other entry points (App UI or Notification action).
         */
        fun requestTileUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, FrameXOverlayTileService::class.java)
                )
            }.onFailure { error ->
                FrameXLog.w("Failed to request Quick Settings tile listening state: ${error.message}")
            }
        }
    }
}
