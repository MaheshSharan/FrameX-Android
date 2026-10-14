package com.framex.app.ui.screens.thermal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.framex.app.metrics.MetricCapability
import com.framex.app.metrics.MetricsEngine

enum class TimeWindow(val label: String, val seconds: Int) {
    SEC_60("60 Seconds", 60),
    MIN_5("5 Minutes", 300),
    MIN_15("15 Minutes", 900),
    MIN_30("30 Minutes", 1800),
    HOUR_1("1 Hour", 3600),
    FULL("Full Session", Int.MAX_VALUE)
}

enum class GraphMetricMode(val label: String) {
    FPS_THERMAL("FPS vs Thermal (CPU + Skin)"),
    FPS_JANK("FPS + Jank Frames"),
    FPS_ONLY("FPS Only"),
    THERMAL_ONLY("Thermal Only (CPU, GPU, Skin, Battery)"),
    FPS_TOP_PROCESS("FPS + Top Process CPU%")
}

/**
 * Standardized OEM-agnostic thermal sensor channels across Qualcomm, MediaTek, Exynos, Tensor, and Unisoc.
 */
enum class StandardizedThermalChannel(
    val id: String,
    val label: String,
    val shortLabel: String,
    val warningTempC: Float,
    val criticalTempC: Float
) {
    SOC_COMPLEX("soc", "SoC / CPU Package", "CPU", 70f, 85f),
    GPU("gpu", "GPU Core", "GPU", 68f, 82f),
    CHASSIS_SKIN("skin", "Chassis / Skin", "SKIN", 42f, 47f),
    BATTERY("battery", "Battery Cell", "BATTERY", 41f, 45f),
    NPU("npu", "NPU / AI Accelerator", "NPU", 68f, 82f)
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
 * Pre-computed session summary KPIs matching the 2x4 profiler matrix and chart footers.
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

@Immutable
data class DiagnosticCause(
    val title: String,
    val description: String,
    val color: Color
)

/** One plotted line on the graph: its display name, theme color, unit suffix, and value extractor. */
data class GraphSeries(
    val label: String,
    val color: Color,
    val unitSuffix: String,
    val maxScale: Float,
    val fillArea: Boolean,
    val valueOf: (MetricsEngine.MetricsSnapshot) -> Float
)
