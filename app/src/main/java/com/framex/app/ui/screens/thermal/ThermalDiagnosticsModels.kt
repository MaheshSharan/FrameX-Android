package com.framex.app.ui.screens.thermal

import androidx.compose.runtime.Immutable

/**
 * Target app option for per-app session profiling.
 * Populated strictly from System-Wide + games added in Performance -> Game Launcher.
 */
@Immutable
data class TargetAppOption(
    val packageName: String?,
    val label: String,
    val versionName: String = ""
)

/**
 * How much recent history the telemetry timelines show; [seconds] is null for everything kept.
 */
enum class TimelineWindow(val seconds: Int?, val labelRes: Int) {
    TWO_MIN(120, com.framex.app.R.string.thermal_window_2m),
    FIVE_MIN(300, com.framex.app.R.string.thermal_window_5m),
    TEN_MIN(600, com.framex.app.R.string.thermal_window_10m),
    ALL(null, com.framex.app.R.string.thermal_window_all)
}

/**
 * 4-column hardware identity strip: Chipset (SoC) | Device (Model) | System (OS) | Display (Max Hz).
 */
@Immutable
data class DevicePlatformInfo(
    val socName: String = "SoC",
    val modelName: String = "Android",
    val osVersion: String = "Android",
    val displayRefreshHz: Int = 60
)

/**
 * Complete 8-KPI session summary matrix + thermal excursion metrics matching the per-app profiler report.
 */
@Immutable
data class SessionKpiSummary(
    val sampleCount: Int = 0,
    val durationSec: Int = 0,
    val maxFps: Float = 0f,
    val minFps: Float = 0f,
    val avgFps: Float = 0f,
    val jitterPercent: Float = 0f,
    val onePercentLowFps: Float = 0f,
    val avgSocTempC: Float = 0f,
    val maxSocTempC: Float = 0f,
    val minSocTempC: Float = 0f,
    val highTempDurationSec: Int = 0,
    val maxBatteryTempC: Float = 0f,
    val avgPowerWatts: Float = 0f,
    val maxPowerWatts: Float = 0f,
    val minPowerWatts: Float = 0f,
    val maxFrameTimeMs: Int = 0
) {
    companion object {
        const val HIGH_SOC_TEMP_THRESHOLD_C = 75f
    }
}
