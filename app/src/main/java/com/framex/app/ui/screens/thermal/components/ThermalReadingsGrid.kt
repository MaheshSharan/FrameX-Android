package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framex.app.metrics.MetricsState
import com.framex.app.ui.screens.thermal.getBatteryDisplayValue
import com.framex.app.ui.screens.thermal.getThermalDisplayValue
import com.framex.app.ui.theme.FrameXSpacing
import java.util.Locale

@Composable
fun ThermalReadingsGrid(
    metricsState: MetricsState,
    cpuDelta: Float,
    cpuPeak: Float,
    cpuAvg: Float,
    gpuPeak: Float,
    gpuAvg: Float,
    skinDelta: Float,
    skinPeak: Float,
    skinAvg: Float,
    batteryDelta: Float,
    batteryPeak: Float,
    batteryAvg: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // CPU & GPU row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(FrameXSpacing.Medium)
        ) {
            ReadingCard(
                label = "CPU",
                value = getThermalDisplayValue(metricsState.thermalCpuC, metricsState.hasThermalCpu, metricsState.thermalReadStatus),
                delta30s = cpuDelta,
                peakVal = cpuPeak,
                avgVal = cpuAvg,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ReadingCard(
                label = "GPU",
                value = getThermalDisplayValue(metricsState.thermalGpuC, metricsState.hasThermalGpu, metricsState.thermalReadStatus),
                delta30s = 0f,
                peakVal = gpuPeak,
                avgVal = gpuAvg,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Spacer(modifier = Modifier.height(FrameXSpacing.Medium))

        // SKIN & NPU row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(FrameXSpacing.Medium)
        ) {
            ReadingCard(
                label = "SKIN",
                value = getThermalDisplayValue(metricsState.thermalSkinC, metricsState.hasThermalSkin, metricsState.thermalReadStatus),
                delta30s = skinDelta,
                peakVal = skinPeak,
                avgVal = skinAvg,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ReadingCard(
                label = "NPU",
                value = getThermalDisplayValue(metricsState.thermalNpuC, metricsState.hasThermalNpu, metricsState.thermalReadStatus),
                delta30s = 0f,
                peakVal = 0f,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Spacer(modifier = Modifier.height(FrameXSpacing.Medium))

        // BATTERY & JANK RATE row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(FrameXSpacing.Medium)
        ) {
            ReadingCard(
                label = "BATTERY",
                value = getBatteryDisplayValue(metricsState.batteryTempC),
                delta30s = batteryDelta,
                peakVal = batteryPeak,
                avgVal = batteryAvg,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            val jankRate = String.format(Locale.US, "%.1f/s", metricsState.jankyFrames / 3.0f)
            val jankLabel = if (metricsState.jankyFrames == 0) "Smooth" else if (metricsState.jankyFrames < 5) "Minor" else "Stutter"
            ReadingCard(
                label = "JANK RATE",
                value = "$jankRate ($jankLabel)",
                delta30s = 0f,
                peakVal = 0f,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Spacer(modifier = Modifier.height(FrameXSpacing.Medium))

        // Top Processes
        TopProcessCard(
            topProcesses = metricsState.topProcesses,
            readStatus = metricsState.topProcessReadStatus,
            topProcessName = metricsState.topProcessName,
            topProcessCpuPercent = metricsState.topProcessCpuPercent,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
