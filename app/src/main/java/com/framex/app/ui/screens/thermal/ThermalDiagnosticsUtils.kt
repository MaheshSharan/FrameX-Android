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
import com.framex.app.metrics.MetricReadStatus
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import com.framex.app.ui.theme.Amber
import com.framex.app.ui.theme.MutedBlue
import com.framex.app.ui.theme.MutedGreen
import com.framex.app.ui.theme.PrimaryRed
import com.framex.app.ui.theme.Teal
import java.util.Locale

/** Single source of truth for human-readable thermal status text across UI and ViewModels. */
fun getThermalStatusLabel(status: Int): String {
    return when (status) {
        0 -> "Normal"
        1 -> "Light Throttling"
        2 -> "Moderate Throttling"
        3 -> "Severe Throttling"
        4 -> "Critical Throttling"
        5 -> "Emergency Mitigation"
        6 -> "System Shutdown"
        else -> "Level $status"
    }
}

/** Pure helper to evaluate thermal pressure level based on status code and delta thresholds. */
fun computeThermalPressure(status: Int, cpuDelta: Float, skinDelta: Float): String {
    return when (status) {
        0 -> if (cpuDelta > 1.5f || skinDelta > 1.0f) "Rising" else "Stable"
        1, 2 -> "Elevated"
        else -> "Critical"
    }
}

fun getThermalDisplayValue(value: Float, present: Boolean, readStatus: MetricReadStatus): String {
    return when (readStatus) {
        MetricReadStatus.NoShizuku -> "Needs Shizuku"
        MetricReadStatus.EmptyOutput -> "Not Supported"
        MetricReadStatus.ParseFailed -> "Parse Failed"
        MetricReadStatus.Loading -> "Waiting..."
        else -> if (present) String.format(Locale.US, "%.1f°C", value) else "Not Supported"
    }
}

fun getBatteryDisplayValue(tempC: Float): String {
    return if (tempC <= 0f) "Unavailable" else String.format(Locale.US, "%.1f°C", tempC)
}

fun getTopProcessDisplayValue(name: String?, percent: Float, readStatus: MetricReadStatus): String {
    return when (readStatus) {
        MetricReadStatus.NoShizuku -> "Needs Shizuku"
        MetricReadStatus.EmptyOutput -> "Unavailable"
        MetricReadStatus.ParseFailed -> "Parse Failed"
        MetricReadStatus.Loading -> "Waiting..."
        MetricReadStatus.NoData -> "No process data"
        MetricReadStatus.Ok -> {
            if (name != null) {
                "$name (${String.format(Locale.US, "%.0f", percent)}%)"
            } else {
                "—"
            }
        }
        else -> "—"
    }
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
    val isHot = maxCpuTemp >= 65f || maxSkinTemp >= 42f || currentState.thermalStatus > 0

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
