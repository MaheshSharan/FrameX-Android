package com.framex.app.ui.screens.thermal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.framex.app.metrics.MetricCapability
import com.framex.app.metrics.MetricReadStatus
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.toCapability
import java.util.Locale
import kotlin.math.abs

/** Single source of truth for human-readable thermal status text across UI and ViewModels. */
fun getThermalStatusLabel(status: Int): String = when (status) {
    0 -> "Normal"
    1 -> "Light Throttling"
    2 -> "Moderate Throttling"
    3 -> "Severe Throttling"
    4 -> "Critical Throttling"
    5 -> "Emergency Mitigation"
    6 -> "System Shutdown"
    else -> "Level $status"
}

/** Pure helper to evaluate thermal pressure level based on status code and delta thresholds. */
fun computeThermalPressure(status: Int, cpuDelta: Float, skinDelta: Float): String = when (status) {
    0 -> if (cpuDelta > 1.5f || skinDelta > 1.0f) "Rising" else "Stable"
    1, 2 -> "Elevated"
    else -> "Critical"
}

fun getThermalDisplayValue(value: Float, present: Boolean, readStatus: MetricReadStatus): String = when (readStatus) {
    MetricReadStatus.NoShizuku -> "Needs Shizuku"
    MetricReadStatus.PermissionDenied -> "Restricted"
    MetricReadStatus.HardwareUnsupported,
    MetricReadStatus.EmptyOutput -> "Not Supported"
    MetricReadStatus.ParseFailed -> "Parse Failed"
    MetricReadStatus.Loading -> "Waiting..."
    else -> if (present && value > 0f) String.format(Locale.US, "%.1f°C", value) else "Not Supported"
}

fun getBatteryDisplayValue(tempC: Float): String =
    if (tempC <= 0f) "Unavailable" else String.format(Locale.US, "%.1f°C", tempC)

fun getTopProcessDisplayValue(name: String?, percent: Float, readStatus: MetricReadStatus): String = when (readStatus) {
    MetricReadStatus.NoShizuku -> "Needs Shizuku"
    MetricReadStatus.PermissionDenied -> "Restricted"
    MetricReadStatus.HardwareUnsupported,
    MetricReadStatus.EmptyOutput -> "Unavailable"
    MetricReadStatus.ParseFailed -> "Parse Failed"
    MetricReadStatus.Loading -> "Waiting..."
    MetricReadStatus.NoData -> "No process data"
    MetricReadStatus.Ok -> if (name != null) "$name (${String.format(Locale.US, "%.0f", percent)}%)" else "—"
    else -> "—"
}

/**
 * Resolves the capability and live/historical stats for each standardized OEM thermal channel.
 */
fun buildStandardizedThermalChannels(
    state: MetricsState,
    cpuPeak: Float,
    cpuAvg: Float,
    cpuDelta: Float,
    gpuPeak: Float,
    gpuAvg: Float,
    skinPeak: Float,
    skinAvg: Float,
    skinDelta: Float,
    batteryPeak: Float,
    batteryAvg: Float,
    batteryDelta: Float
): List<ThermalChannelState> {
    fun sensorCapability(hasSensor: Boolean, tempC: Float): MetricCapability {
        if (state.thermalReadStatus == MetricReadStatus.NoShizuku) {
            return MetricReadStatus.NoShizuku.toCapability(requiresShizuku = true)
        }
        if (state.thermalReadStatus == MetricReadStatus.Loading) {
            return MetricReadStatus.Loading.toCapability(requiresShizuku = true)
        }
        return if (hasSensor && tempC > 0f) {
            MetricReadStatus.Ok.toCapability(requiresShizuku = true)
        } else {
            MetricReadStatus.HardwareUnsupported.toCapability(requiresShizuku = false)
        }
    }

    val batteryCap = if (state.batteryTempC > 0f) {
        MetricReadStatus.Ok.toCapability(requiresShizuku = false)
    } else {
        MetricReadStatus.NoData.toCapability(requiresShizuku = false)
    }

    return listOf(
        ThermalChannelState(
            channel = StandardizedThermalChannel.SOC_COMPLEX,
            currentTempC = state.thermalCpuC,
            peakTempC = cpuPeak,
            avgTempC = cpuAvg,
            delta30sC = cpuDelta,
            capability = sensorCapability(state.hasThermalCpu, state.thermalCpuC)
        ),
        ThermalChannelState(
            channel = StandardizedThermalChannel.GPU,
            currentTempC = state.thermalGpuC,
            peakTempC = gpuPeak,
            avgTempC = gpuAvg,
            delta30sC = 0f,
            capability = sensorCapability(state.hasThermalGpu, state.thermalGpuC)
        ),
        ThermalChannelState(
            channel = StandardizedThermalChannel.CHASSIS_SKIN,
            currentTempC = state.thermalSkinC,
            peakTempC = skinPeak,
            avgTempC = skinAvg,
            delta30sC = skinDelta,
            capability = sensorCapability(state.hasThermalSkin, state.thermalSkinC)
        ),
        ThermalChannelState(
            channel = StandardizedThermalChannel.BATTERY,
            currentTempC = state.batteryTempC,
            peakTempC = batteryPeak,
            avgTempC = batteryAvg,
            delta30sC = batteryDelta,
            capability = batteryCap
        ),
        ThermalChannelState(
            channel = StandardizedThermalChannel.NPU,
            currentTempC = state.thermalNpuC,
            peakTempC = state.thermalNpuC,
            avgTempC = state.thermalNpuC,
            delta30sC = 0f,
            capability = sensorCapability(state.hasThermalNpu, state.thermalNpuC)
        )
    )
}

/**
 * Pure single-pass O(N) KPI calculator for per-app session profiling:
 * Computes MAX/MIN/AVG FPS, Jitter %, 1% Low FPS, SoC Temp stats, High-Temp Duration,
 * Battery Temp Max, Power (W) Max/Min/Avg, and worst Frame Time (ms).
 */
fun computeSessionKpiSummary(
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    highTempThresholdC: Float = SessionKpiSummary.HIGH_SOC_TEMP_THRESHOLD_C
): SessionKpiSummary {
    if (snapshots.isEmpty()) return SessionKpiSummary()

    val n = snapshots.size
    val validFps = ArrayList<Int>(n)

    var fpsSum = 0L
    var maxFps = 0
    var minFps = Int.MAX_VALUE
    var frameDeltaSum = 0f
    var frameDeltaCount = 0
    var prevFps = -1

    var socSum = 0f
    var socCount = 0
    var maxSoc = 0f
    var minSoc = Float.MAX_VALUE
    var highTempSec = 0

    var maxBat = 0f
    var powerSum = 0f
    var powerCount = 0
    var maxPower = 0f
    var minPower = Float.MAX_VALUE
    var maxFrameTime = 0

    for (i in 0 until n) {
        val s = snapshots[i].state
        val fps = s.fps
        if (fps > 0) {
            validFps.add(fps)
            fpsSum += fps
            if (fps > maxFps) maxFps = fps
            if (fps < minFps) minFps = fps
            if (prevFps > 0) {
                frameDeltaSum += abs(fps - prevFps).toFloat()
                frameDeltaCount++
            }
            prevFps = fps
        }

        if (s.frameTimeMs > maxFrameTime) {
            maxFrameTime = s.frameTimeMs
        }

        val socC = if (s.thermalCpuC > 0f) s.thermalCpuC else s.batteryTempC
        if (socC > 0f) {
            socSum += socC
            socCount++
            if (socC > maxSoc) maxSoc = socC
            if (socC < minSoc) minSoc = socC
            if (socC >= highTempThresholdC) {
                highTempSec++
            }
        }

        if (s.batteryTempC > maxBat) {
            maxBat = s.batteryTempC
        }

        if (s.powerWatts > 0f) {
            powerSum += s.powerWatts
            powerCount++
            if (s.powerWatts > maxPower) maxPower = s.powerWatts
            if (s.powerWatts < minPower) minPower = s.powerWatts
        }
    }

    val fpsCount = validFps.size
    val avgFps = if (fpsCount > 0) fpsSum.toFloat() / fpsCount else 0f
    val resolvedMinFps = if (minFps == Int.MAX_VALUE) 0f else minFps.toFloat()

    // 1% Low FPS: average of the lowest 1% of non-zero FPS samples (at least 1 sample)
    val onePercentLow = if (fpsCount > 0) {
        validFps.sort()
        val tailCount = (fpsCount / 100).coerceAtLeast(1)
        var tailSum = 0L
        for (idx in 0 until tailCount) {
            tailSum += validFps[idx]
        }
        tailSum.toFloat() / tailCount
    } else {
        0f
    }

    // Jitter %: normalized frame-to-frame variance + janky frame ratio
    val jitterPct = if (avgFps > 0f && frameDeltaCount > 0) {
        val meanDelta = frameDeltaSum / frameDeltaCount
        ((meanDelta / avgFps) * 100f).coerceIn(0f, 100f)
    } else {
        0f
    }

    val durationSec = if (n >= 2) {
        ((snapshots.last().timestampMs - snapshots.first().timestampMs) / 1000L).toInt().coerceAtLeast(n)
    } else {
        n
    }

    return SessionKpiSummary(
        sampleCount = n,
        durationSec = durationSec,
        maxFps = maxFps.toFloat(),
        minFps = resolvedMinFps,
        avgFps = avgFps,
        jitterPercent = jitterPct,
        onePercentLowFps = onePercentLow,
        avgSocTempC = if (socCount > 0) socSum / socCount else 0f,
        maxSocTempC = maxSoc,
        minSocTempC = if (minSoc == Float.MAX_VALUE) 0f else minSoc,
        highTempDurationSec = highTempSec,
        maxBatteryTempC = maxBat,
        avgPowerWatts = if (powerCount > 0) powerSum / powerCount else 0f,
        maxPowerWatts = maxPower,
        minPowerWatts = if (minPower == Float.MAX_VALUE) 0f else minPower,
        maxFrameTimeMs = maxFrameTime
    )
}

fun evaluateLikelyCause(
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    currentState: MetricsState
): DiagnosticCause {
    if (snapshots.size < 5) {
        return DiagnosticCause(
            title = "Monitoring Performance",
            description = "Gathering telemetry samples to evaluate frame drop root cause...",
            color = Color(0xFF60A5FA)
        )
    }

    val avgFps = snapshots.map { it.state.fps }.average()
    val minFps = snapshots.minOf { it.state.fps }
    val isFpsDropped = minFps < (avgFps * 0.85) || currentState.fps < 45

    val maxCpuTemp = snapshots.maxOf { it.state.thermalCpuC }
    val maxSkinTemp = snapshots.maxOf { it.state.thermalSkinC }
    val isHot = maxCpuTemp >= 65f || maxSkinTemp >= 42f || currentState.thermalStatus > 0 || currentState.isThrottling

    val topCpu = currentState.topProcessCpuPercent
    val isProcessBusy = topCpu >= 20f && currentState.topProcessName != null

    return when {
        isFpsDropped && isHot -> DiagnosticCause(
            title = "Likely Thermal Pressure",
            description = "FPS dropped while CPU/Skin temperature elevated to ${String.format(Locale.US, "%.1f°C", maxCpuTemp)}.",
            color = Color(0xFFEF4444)
        )
        isFpsDropped && isProcessBusy -> DiagnosticCause(
            title = "Likely Background CPU Contention",
            description = "FPS dropped while '${currentState.topProcessName}' consumed ${String.format(Locale.US, "%.0f%%", topCpu)} CPU.",
            color = Color(0xFFF59E0B)
        )
        currentState.jankyFrames >= 6 -> DiagnosticCause(
            title = "Likely Frame Pacing Issue",
            description = "Average FPS is steady, but display compositor detected sudden jank frame spikes.",
            color = Color(0xFFFB7185)
        )
        else -> DiagnosticCause(
            title = "No Thermal Correlation Detected",
            description = "Thermals and background CPU usage are within normal limits.",
            color = Color(0xFF34D399)
        )
    }
}

fun seriesForMode(
    mode: GraphMetricMode,
    maxFps: Float,
    maxTemp: Float,
    maxJank: Float = 10f,
    hasGpu: Boolean = true
): List<GraphSeries> {
    val fpsSeries = GraphSeries("FPS", Color(0xFF38BDF8), "", maxFps, fillArea = true) { it.state.fps.toFloat() }
    val cpuSeries = GraphSeries("CPU", Color(0xFFFF2E4D), "°", maxTemp, fillArea = false) { it.state.thermalCpuC }
    val gpuSeries = GraphSeries("GPU", Color(0xFF3B82F6), "°", maxTemp, fillArea = false) { it.state.thermalGpuC }
    val skinSeries = GraphSeries("Skin", Color(0xFFFFB703), "°", maxTemp, fillArea = false) { it.state.thermalSkinC }
    val batterySeries = GraphSeries("Battery", Color(0xFF06D6A0), "°", maxTemp, fillArea = false) { it.state.batteryTempC }
    val jankSeries = GraphSeries("Jank", Color(0xFFEF476F), "", maxJank, fillArea = false) { it.state.jankyFrames.toFloat() }
    val topProcessSeries = GraphSeries("Top Process", Color(0xFFFF7300), "%", 100f, fillArea = false) { it.state.topProcessCpuPercent }

    return when (mode) {
        GraphMetricMode.FPS_THERMAL -> listOf(cpuSeries, skinSeries, fpsSeries)
        GraphMetricMode.FPS_JANK -> listOf(jankSeries, fpsSeries)
        GraphMetricMode.FPS_ONLY -> listOf(fpsSeries)
        GraphMetricMode.THERMAL_ONLY -> {
            val list = mutableListOf(cpuSeries.copy(fillArea = true))
            if (hasGpu) list.add(gpuSeries)
            list.add(skinSeries)
            list.add(batterySeries)
            list
        }
        GraphMetricMode.FPS_TOP_PROCESS -> listOf(topProcessSeries, fpsSeries)
    }
}

fun formatSecondsAgo(secondsAgo: Int): String {
    if (secondsAgo <= 0) return "now"
    val hours = secondsAgo / 3600
    val minutes = (secondsAgo % 3600) / 60
    val seconds = secondsAgo % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

fun formatDurationMinSec(totalSec: Int): String {
    if (totalSec <= 0) return "0s"
    val mins = totalSec / 60
    val secs = totalSec % 60
    return if (mins > 0) "${mins}m${String.format(Locale.US, "%02ds", secs)}" else "${secs}s"
}

fun plateBgForLabel(label: String): Color = when (label) {
    "CPU" -> Color(0xFFEF4444).copy(alpha = 0.14f)
    "GPU" -> Color(0xFF3B82F6).copy(alpha = 0.14f)
    "SKIN" -> Color(0xFFF59E0B).copy(alpha = 0.14f)
    "NPU" -> Color(0xFF8B5CF6).copy(alpha = 0.14f)
    "BATTERY" -> Color(0xFF10B981).copy(alpha = 0.14f)
    "JANK RATE" -> Color(0xFFF43F5E).copy(alpha = 0.14f)
    "TOP PROCESS" -> Color(0xFF06B6D4).copy(alpha = 0.14f)
    else -> Color(0xFFEC4899).copy(alpha = 0.14f)
}

fun plateBorderForLabel(label: String): Color = when (label) {
    "CPU" -> Color(0xFFEF4444).copy(alpha = 0.28f)
    "GPU" -> Color(0xFF3B82F6).copy(alpha = 0.28f)
    "SKIN" -> Color(0xFFF59E0B).copy(alpha = 0.28f)
    "NPU" -> Color(0xFF8B5CF6).copy(alpha = 0.28f)
    "BATTERY" -> Color(0xFF10B981).copy(alpha = 0.28f)
    "JANK RATE" -> Color(0xFFF43F5E).copy(alpha = 0.28f)
    "TOP PROCESS" -> Color(0xFF06B6D4).copy(alpha = 0.28f)
    else -> Color(0xFFEC4899).copy(alpha = 0.28f)
}

fun iconColorForLabel(label: String): Color = when (label) {
    "CPU" -> Color(0xFFF87171)
    "GPU" -> Color(0xFF60A5FA)
    "SKIN" -> Color(0xFFFBBF24)
    "NPU" -> Color(0xFFA78BFA)
    "BATTERY" -> Color(0xFF34D399)
    "JANK RATE" -> Color(0xFFFB7185)
    "TOP PROCESS" -> Color(0xFF22D3EE)
    else -> Color(0xFFF472B6)
}

fun iconForLabel(label: String): ImageVector = when (label) {
    "CPU" -> Icons.Default.Memory
    "GPU" -> Icons.Default.Speed
    "SKIN" -> Icons.Default.Thermostat
    "NPU" -> Icons.Default.DeveloperBoard
    "BATTERY" -> Icons.Default.BatteryChargingFull
    "JANK RATE" -> Icons.Default.Warning
    "TOP PROCESS" -> Icons.Default.Layers
    else -> Icons.Default.LocalFireDepartment
}

fun buildDiagnosticSummaryText(
    state: MetricsState,
    snapshots: List<MetricsEngine.MetricsSnapshot>
): String {
    val kpi = computeSessionKpiSummary(snapshots.ifEmpty {
        listOf(MetricsEngine.MetricsSnapshot(System.currentTimeMillis(), state))
    })
    val topProcess = state.topProcessName ?: "None"
    val statusText = getThermalStatusLabel(state.thermalStatus)

    return """
### FrameX Performance & Thermal Diagnostic Report
- **Device**: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})
- **System Thermal State**: $statusText
- **FPS (Max / Avg / Min / 1% Low)**: ${String.format(Locale.US, "%.0f / %.1f / %.0f / %.1f FPS", kpi.maxFps, kpi.avgFps, kpi.minFps, kpi.onePercentLowFps)}
- **Frame Jitter**: ${String.format(Locale.US, "%.1f%%", kpi.jitterPercent)} (Max Frame Time: ${kpi.maxFrameTimeMs} ms)
- **SoC Temp (Max / Avg)**: ${String.format(Locale.US, "%.1f°C / %.1f°C", kpi.maxSocTempC, kpi.avgSocTempC)}
- **Battery Peak / Avg Power**: ${String.format(Locale.US, "%.1f°C / %.2f W", kpi.maxBatteryTempC, kpi.avgPowerWatts)}
- **Top Process**: $topProcess (${String.format(Locale.US, "%.0f%%", state.topProcessCpuPercent)})
""".trimIndent()
}
