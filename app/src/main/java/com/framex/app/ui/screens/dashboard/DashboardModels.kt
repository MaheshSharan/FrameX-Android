package com.framex.app.ui.screens.dashboard

import androidx.compose.runtime.Immutable

@Immutable
data class FpsStatsSummary(
    val currentFps: Int = 0,
    val avgFps: Int = 0,
    val onePercentLow: Int = 0,
    val frametimeMs: Int = 0
)

enum class OverlayActionState {
    RUNNING,
    READY,
    MISSING_PERMISSIONS
}

@Immutable
data class DashboardUiState(
    val isOverlayRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val isShizukuAvailable: Boolean = false,
    val hasShizukuPermission: Boolean = false,
    val fpsHistory: List<Int> = emptyList(),
    val fpsStats: FpsStatsSummary = FpsStatsSummary(),
    /** True only under Root; write-locking sysfs nodes needs Root, Shizuku support is partial. */
    val canShowHardwareTuning: Boolean = false
) {
    val allPermissionsReady: Boolean
        get() = hasOverlayPermission && isShizukuAvailable && hasShizukuPermission

    val isShizukuReady: Boolean
        get() = isShizukuAvailable && hasShizukuPermission
}

sealed interface DashboardUiEvent {
    data object StartOverlay : DashboardUiEvent
    data object StopOverlay : DashboardUiEvent
    data object RefreshPermissions : DashboardUiEvent
}
