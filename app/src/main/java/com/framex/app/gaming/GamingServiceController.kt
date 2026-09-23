package com.framex.app.gaming

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller to manage GamingModeService lifecycle and game app launches.
 * Decouples Android Context and system service calls from ViewModels.
 */
@Singleton
class GamingServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startGamingService() {
        context.startForegroundService(Intent(context, GamingModeService::class.java))
    }

    fun stopGamingService() {
        context.startService(
            Intent(context, GamingModeService::class.java).apply {
                action = GamingModeService.ACTION_STOP
            }
        )
    }

    fun launchApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }
}
