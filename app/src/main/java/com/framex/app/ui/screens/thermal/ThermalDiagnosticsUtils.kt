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
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.toCapability
import java.util.Locale

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
    fun sensorCapability(hasSensor: Boolean, tempC: Float): MetricCapability = when {
        state.thermalReadStatus == MetricReadStatus.NoShizuku ->
            MetricReadStatus.NoShizuku.toCapability(requiresShizuku = true)
        state.thermalReadStatus == MetricReadStatus.Loading ->
            MetricReadStatus.Loading.toCapability(requiresShizuku = true)
        hasSensor && tempC > 0f ->
            MetricReadStatus.Ok.toCapability(requiresShizuku = true)
        else ->
            MetricReadStatus.HardwareUnsupported.toCapability(requiresShizuku = false)
    }

    val batteryCap = if (state.batteryTempC > 0f) {
        MetricReadStatus.Ok.toCapability(requiresShizuku = false)
    } else {
        MetricReadStatus.NoData.toCapability(requiresShizuku = false)
    }

    return listOf(
        ThermalChannelState(StandardizedThermalChannel.SOC_COMPLEX, state.thermalCpuC, cpuPeak, cpuAvg, cpuDelta, sensorCapability(state.hasThermalCpu, state.thermalCpuC)),
        ThermalChannelState(StandardizedThermalChannel.GPU, state.thermalGpuC, gpuPeak, gpuAvg, 0f, sensorCapability(state.hasThermalGpu, state.thermalGpuC)),
        ThermalChannelState(StandardizedThermalChannel.CHASSIS_SKIN, state.thermalSkinC, skinPeak, skinAvg, skinDelta, sensorCapability(state.hasThermalSkin, state.thermalSkinC)),
        ThermalChannelState(StandardizedThermalChannel.BATTERY, state.batteryTempC, batteryPeak, batteryAvg, batteryDelta, batteryCap),
        ThermalChannelState(StandardizedThermalChannel.NPU, state.thermalNpuC, state.thermalNpuC, state.thermalNpuC, 0f, sensorCapability(state.hasThermalNpu, state.thermalNpuC))
    )
}

fun formatDurationMinSec(totalSec: Int): String {
    if (totalSec <= 0) return "0s"
    val mins = totalSec / 60
    return if (mins > 0) "${mins}m${String.format(Locale.US, "%02ds", totalSec % 60)}" else "${totalSec}s"
}

private data class ReadingCardStyle(val color: Color, val icon: ImageVector)

private fun styleForLabel(label: String): ReadingCardStyle = when (label) {
    "CPU" -> ReadingCardStyle(Color(0xFFF87171), Icons.Default.Memory)
    "GPU" -> ReadingCardStyle(Color(0xFF60A5FA), Icons.Default.Speed)
    "SKIN" -> ReadingCardStyle(Color(0xFFFBBF24), Icons.Default.Thermostat)
    "NPU" -> ReadingCardStyle(Color(0xFFA78BFA), Icons.Default.DeveloperBoard)
    "BATTERY" -> ReadingCardStyle(Color(0xFF34D399), Icons.Default.BatteryChargingFull)
    "JANK RATE" -> ReadingCardStyle(Color(0xFFFB7185), Icons.Default.Warning)
    "TOP PROCESS" -> ReadingCardStyle(Color(0xFF22D3EE), Icons.Default.Layers)
    else -> ReadingCardStyle(Color(0xFFF472B6), Icons.Default.LocalFireDepartment)
}

fun plateBgForLabel(label: String): Color = styleForLabel(label).color.copy(alpha = 0.14f)
fun plateBorderForLabel(label: String): Color = styleForLabel(label).color.copy(alpha = 0.28f)
fun iconColorForLabel(label: String): Color = styleForLabel(label).color
fun iconForLabel(label: String): ImageVector = styleForLabel(label).icon
