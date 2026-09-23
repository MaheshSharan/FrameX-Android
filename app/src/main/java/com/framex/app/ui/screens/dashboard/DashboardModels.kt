package com.framex.app.ui.screens.dashboard

import androidx.compose.runtime.Immutable

@Immutable
data class FpsStatsSummary(
    val currentFps: Int = 0,
    val avgFps: Int = 0,
    val onePercentLow: Int = 0,
    val frametimeMs: Int = 0
)

@Immutable
data class DashboardUiState(
    val isOverlayRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isShizukuAvailable: Boolean = false,
    val hasShizukuPermission: Boolean = false,
    val fpsHistory: List<Int> = emptyList(),
    val fpsStats: FpsStatsSummary = FpsStatsSummary()
) {
    val allPermissionsReady: Boolean
        get() = hasOverlayPermission && isShizukuAvailable && hasShizukuPermission

    val isShizukuReady: Boolean
        get() = isShizukuAvailable && hasShizukuPermission

    val missingPermissions: List<String>
        get() = buildList {
            if (!hasOverlayPermission) add("Overlay permission")
            if (!isShizukuAvailable) add("Shizuku service")
            if (!hasShizukuPermission) add("Shizuku permission")
        }
}

sealed interface DashboardUiEvent {
    object StartOverlay : DashboardUiEvent
    object StopOverlay : DashboardUiEvent
    object RefreshPermissions : DashboardUiEvent
}
