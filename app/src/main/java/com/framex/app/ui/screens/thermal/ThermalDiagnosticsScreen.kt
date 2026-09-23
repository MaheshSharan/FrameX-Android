package com.framex.app.ui.screens.thermal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import com.framex.app.ui.screens.thermal.components.GraphLegend
import com.framex.app.ui.screens.thermal.components.ModernInteractiveGraph
import com.framex.app.ui.screens.thermal.components.ThermalDetailsSection
import com.framex.app.ui.screens.thermal.components.ThermalExportSection
import com.framex.app.ui.screens.thermal.components.ThermalGraphControls
import com.framex.app.ui.screens.thermal.components.ThermalReadingsGrid
import com.framex.app.ui.screens.thermal.components.ThermalStatusBanner
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing

/**
 * Pure, stateless Thermal Diagnostics screen.
 * Telemetry rendering and Canvas Bezier timeline graph.
 */
@Composable
fun ThermalDiagnosticsScreen(
    metricsState: MetricsState,
    snapshotHistory: List<MetricsEngine.MetricsSnapshot>,
    isRecording: Boolean,
    sampleCount: Int,
    isShizukuAvailable: Boolean,
    hasShizukuPermission: Boolean,
    selectedWindow: TimeWindow,
    onWindowSelected: (TimeWindow) -> Unit,
    selectedGraphMode: GraphMetricMode,
    onGraphModeSelected: (GraphMetricMode) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onToggleRecording: () -> Unit,
    onExportCsv: () -> Unit,
    onCopyReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSensorDetailsExpanded by remember { mutableStateOf(false) }

    val filteredSnapshots = remember(snapshotHistory, selectedWindow) {
        if (selectedWindow == TimeWindow.FULL) {
            snapshotHistory
        } else {
            snapshotHistory.takeLast(selectedWindow.seconds)
        }
    }

    // 30s Trend calculation
    val snapshot30sAgo = remember(snapshotHistory) {
        if (snapshotHistory.size >= 30) snapshotHistory[snapshotHistory.size - 30] else snapshotHistory.firstOrNull()
    }

    val cpuDelta = remember(metricsState.thermalCpuC, snapshot30sAgo) {
        if (snapshot30sAgo != null && snapshot30sAgo.state.thermalCpuC > 0 && metricsState.thermalCpuC > 0) {
            metricsState.thermalCpuC - snapshot30sAgo.state.thermalCpuC
        } else 0f
    }
    val skinDelta = remember(metricsState.thermalSkinC, snapshot30sAgo) {
        if (snapshot30sAgo != null && snapshot30sAgo.state.thermalSkinC > 0 && metricsState.thermalSkinC > 0) {
            metricsState.thermalSkinC - snapshot30sAgo.state.thermalSkinC
        } else 0f
    }
    val batteryDelta = remember(metricsState.batteryTempC, snapshot30sAgo) {
        if (snapshot30sAgo != null && snapshot30sAgo.state.batteryTempC > 0 && metricsState.batteryTempC > 0) {
            metricsState.batteryTempC - snapshot30sAgo.state.batteryTempC
        } else 0f
    }

    // Window Peaks and Averages
    val (cpuPeak, cpuAvg) = remember(filteredSnapshots) {
        val valid = filteredSnapshots.map { it.state.thermalCpuC }.filter { it > 0 }
        Pair(valid.maxOrNull() ?: 0f, if (valid.isNotEmpty()) valid.average().toFloat() else 0f)
    }
    val (gpuPeak, gpuAvg) = remember(filteredSnapshots) {
        val valid = filteredSnapshots.map { it.state.thermalGpuC }.filter { it > 0 }
        Pair(valid.maxOrNull() ?: 0f, if (valid.isNotEmpty()) valid.average().toFloat() else 0f)
    }
    val (skinPeak, skinAvg) = remember(filteredSnapshots) {
        val valid = filteredSnapshots.map { it.state.thermalSkinC }.filter { it > 0 }
        Pair(valid.maxOrNull() ?: 0f, if (valid.isNotEmpty()) valid.average().toFloat() else 0f)
    }
    val (batteryPeak, batteryAvg) = remember(filteredSnapshots) {
        val valid = filteredSnapshots.map { it.state.batteryTempC }.filter { it > 0 }
        Pair(valid.maxOrNull() ?: 0f, if (valid.isNotEmpty()) valid.average().toFloat() else 0f)
    }

    val likelyCause = remember(snapshotHistory, metricsState) {
        evaluateLikelyCause(
            snapshots = snapshotHistory,
            currentState = metricsState
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FrameXSpacing.Standard, vertical = FrameXSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text("Thermal Diagnostics", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Real-time hardware telemetry & root cause analysis", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FrameXSpacing.Large),
            ) {
                // Status Banner & Likely Cause Card
                ThermalStatusBanner(
                    metricsState = metricsState,
                    cpuDelta = cpuDelta,
                    skinDelta = skinDelta,
                    likelyCause = likelyCause,
                    isShizukuAvailable = isShizukuAvailable,
                    hasShizukuPermission = hasShizukuPermission,
                    onOpenShizuku = onOpenShizuku,
                    onRequestShizukuPermission = onRequestShizukuPermission
                )

                Spacer(modifier = Modifier.height(FrameXSpacing.Standard))

                // Readings Grid
                ThermalReadingsGrid(
                    metricsState = metricsState,
                    cpuDelta = cpuDelta,
                    cpuPeak = cpuPeak,
                    cpuAvg = cpuAvg,
                    gpuPeak = gpuPeak,
                    gpuAvg = gpuAvg,
                    skinDelta = skinDelta,
                    skinPeak = skinPeak,
                    skinAvg = skinAvg,
                    batteryDelta = batteryDelta,
                    batteryPeak = batteryPeak,
                    batteryAvg = batteryAvg
                )

                Spacer(modifier = Modifier.height(FrameXSpacing.XLarge))

                // Interactive Graph Section
                Text("Performance & Thermal Timeline", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Select timeframe and metrics, then drag across the canvas to scrub telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(14.dp))

                ThermalGraphControls(
                    selectedWindow = selectedWindow,
                    onWindowSelected = onWindowSelected,
                    selectedMode = selectedGraphMode,
                    onModeSelected = onGraphModeSelected
                )

                Spacer(modifier = Modifier.height(14.dp))

                val activeSeries = remember(selectedGraphMode, filteredSnapshots) {
                    val fpsRef = (filteredSnapshots.maxOfOrNull { it.state.fps } ?: 60).coerceAtLeast(30).toFloat()
                    val tempRef = (filteredSnapshots.map { it.state.thermalCpuC } + filteredSnapshots.map { it.state.thermalSkinC })
                        .maxOrNull()?.coerceAtLeast(40f) ?: 80f
                    val jankRef = (filteredSnapshots.maxOfOrNull { it.state.jankyFrames.toFloat() } ?: 10f).coerceAtLeast(10f)
                    val hasGpu = filteredSnapshots.any { it.state.hasThermalGpu || it.state.thermalGpuC > 0f }
                    seriesForMode(selectedGraphMode, fpsRef, tempRef, jankRef, hasGpu = hasGpu)
                }
                val hasThrottlingLegend = remember(filteredSnapshots) {
                    filteredSnapshots.any { it.state.isThrottling || it.state.thermalStatus >= 2 }
                }
                GraphLegend(
                    series = activeSeries,
                    modifier = Modifier.padding(bottom = 10.dp),
                    hasThrottling = hasThrottlingLegend
                )

                // Canvas Bezier Graph with Touch Scrubbing
                ModernInteractiveGraph(
                    snapshots = filteredSnapshots,
                    series = activeSeries,
                    window = selectedWindow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(FrameXShapes.Large)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            )
                        )
                        .border(FrameXBorders.ActiveBorderWidth, FrameXBorders.CardStroke, FrameXShapes.Large)
                        .padding(FrameXSpacing.Medium)
                )

                Spacer(modifier = Modifier.height(FrameXSpacing.XLarge))

                // Expandable Sensor Details
                ThermalDetailsSection(
                    metricsState = metricsState,
                    isExpanded = isSensorDetailsExpanded,
                    onToggleExpand = { isSensorDetailsExpanded = !isSensorDetailsExpanded }
                )

                Spacer(modifier = Modifier.height(FrameXSpacing.XXLarge))

                // Action Buttons / Export Section
                ThermalExportSection(
                    isRecording = isRecording,
                    sampleCount = sampleCount,
                    onToggleRecording = onToggleRecording,
                    onExportCsv = onExportCsv,
                    onCopyReport = onCopyReport
                )

                Spacer(modifier = Modifier.height(FrameXSpacing.Huge))
            }
        }
    }
}
