package com.framex.app.ui.screens.dashboard

import com.framex.app.BuildConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal

object WhatsNewRegistry {

    /**
     * Debug testing flag. When true, forces the What's New modal to appear on every dashboard load.
     * Switch to false for release production behavior.
     */
    const val DEBUG_FORCE_SHOW_WHATS_NEW = false

    /**
     * Resolves the list of What's New items to display.
     * Configure specific releases and items here.
     */
    fun getWhatsNewForVersion(versionCode: Long): WhatsNewInfo? {
        val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        if (!DEBUG_FORCE_SHOW_WHATS_NEW && versionCode != currentVersionCode) return null

        val items = listOf(
            WhatsNewFeatureItem(
                id = "cpu_telemetry_overlay",
                category = UpdateItemCategory.LIVE_METRICS,
                title = "CPU Telemetry & Overlay",
                description = "More accurate CPU telemetry readings and cleaner overlay reporting with unavailable clusters omitted.",
                customIcon = Icons.Default.Speed
            ),
            WhatsNewFeatureItem(
                id = "thermal_session_diagnostics",
                category = UpdateItemCategory.HARDWARE,
                title = "Session Logging & Thermals",
                description = "Enhanced session logging with hitch tracking and overhauled thermal diagnostics with interactive charts.",
                isNewBadge = true,
                customIcon = Icons.Default.Memory
            ),
            WhatsNewFeatureItem(
                id = "gaming_iqoo_sliders",
                category = UpdateItemCategory.GAME_MODE,
                title = "Dedicated iQOO Suite & Sliders",
                description = "Separated iQOO and Vivo controls, resolved late game launch handshake, and restored standalone optimization sliders.",
                isNewBadge = true,
                customIcon = Icons.Default.Terminal
            ),
            WhatsNewFeatureItem(
                id = "startup_behavior_fix",
                category = UpdateItemCategory.BUG_FIXES,
                title = "Startup & Updates",
                description = "Fixed startup loading behavior when automatic update checking is disabled.",
                customIcon = Icons.Default.OpenWith
            )
        )

        return WhatsNewInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = currentVersionCode,
            subtitle = "Here's what's new in this update. Thank you for keeping FrameX up to date!",
            items = items
        )
    }
}
