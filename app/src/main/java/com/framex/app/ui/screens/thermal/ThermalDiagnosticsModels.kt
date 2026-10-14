package com.framex.app.ui.screens.thermal

import androidx.compose.runtime.Immutable
import com.framex.app.metrics.MetricCapability

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
 * Standardized OEM-agnostic thermal sensor channels across Qualcomm, MediaTek, Exynos, Tensor, and Unisoc.
 */
enum class StandardizedThermalChannel(
    val id: String,
    val shortTitle: String,
    val fullTitle: String,
    val warningTempC: Float,
    val criticalTempC: Float
) {
    SOC_COMPLEX("soc", "CPU", "SoC / CPU Package", 70f, 85f),
    GPU("gpu", "GPU", "GPU Core", 68f, 82f),
    CHASSIS_SKIN("skin", "SKIN", "Chassis / Surface", 42f, 47f),
    BATTERY("battery", "BATTERY", "Battery Cell", 41f, 45f),
    NPU("npu", "NPU", "AI / NPU Accelerator", 68f, 82f)
}

@Immutable
data class ThermalChannelState(
    val channel: StandardizedThermalChannel,
    val currentTempC: Float,
    val peakTempC: Float,
    val avgTempC: Float,
    val delta30sC: Float,
    val capability: MetricCapability
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
