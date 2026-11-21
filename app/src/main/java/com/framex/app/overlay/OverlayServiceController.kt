package com.framex.app.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller to manage the OverlayService lifecycle.
 * Decouples Android Context and system service calls from ViewModels.
 */
@Singleton
class OverlayServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startOverlayService() {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopOverlayService() {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        context.startService(intent)
    }
}
