package com.framex.app.ui.screens.thermal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.RecordableMetric
import com.framex.app.metrics.SessionRecordingConfig
import com.framex.app.ui.components.FrameXTopBar
import com.framex.app.ui.screens.thermal.components.CollapsibleRecorderCard
import com.framex.app.ui.screens.thermal.components.DevicePlatformStrip
import com.framex.app.ui.screens.thermal.components.SessionSummaryMatrixCard
import com.framex.app.ui.screens.thermal.components.SynchronizedMetricCharts
import com.framex.app.ui.screens.thermal.components.ThermalDetailsSection
import com.framex.app.ui.screens.thermal.components.ThermalReadingsGrid
import com.framex.app.ui.screens.thermal.components.ThermalStatusBanner
import com.framex.app.ui.screens.thermal.components.ThermalWarningBanner
import com.framex.app.ui.theme.FrameXSpacing
import com.framex.app.ui.theme.PrimaryRed

@Composable
fun ThermalDiagnosticsScreen(
    metricsState: MetricsState,
    snapshotHistory: List<MetricsEngine.MetricsSnapshot>,
    recordedSnapshots: List<MetricsEngine.MetricsSnapshot>,
    recordingConfig: SessionRecordingConfig,
    installedApps: List<TargetAppOption>,
    platformInfo: DevicePlatformInfo,
    maxRefreshRateHz: Int,
    sessionTimestamp: String,
    isRecording: Boolean,
    sampleCount: Int,
    isShizukuAvailable: Boolean,
    hasShizukuPermission: Boolean,
    onSelectApp: (TargetAppOption) -> Unit,
    onToggleMetric: (RecordableMetric) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onToggleRecording: () -> Unit,
    onExportCsv: () -> Unit,
    onCopyReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRecorderExpanded by remember { mutableStateOf(false) }
    var isSensorDetailsExpanded by remember { mutableStateOf(false) }

    val activeProfilerSnapshots = remember(recordedSnapshots, snapshotHistory) {
        if (recordedSnapshots.isNotEmpty()) recordedSnapshots else snapshotHistory
    }

    val kpiSummary = remember(activeProfilerSnapshots) {
        computeSessionKpiSummary(activeProfilerSnapshots)
    }

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

    val (cpuPeak, cpuAvg) = remember(activeProfilerSnapshots) {
        val valid = activeProfilerSnapshots.map { it.state.thermalCpuC }.filter { it > 0 }
        Pair(valid.maxOrNull() ?: 0f, if (valid.isNotEmpty()) valid.average().toFloat() else 0f)
    }
    val (gpuPeak, gpuAvg) = remember(activeProfilerSnapshots) {
        val valid = activeProfilerSnapshots.map { it.state.thermalGpuC }.filter { it > 0 }
        Pair(valid.maxOrNull() ?: 0f, if (valid.isNotEmpty()) valid.average().toFloat() else 0f)
    }
    val (skinPeak, skinAvg) = remember(activeProfilerSnapshots) {
        val valid = activeProfilerSnapshots.map { it.state.thermalSkinC }.filter { it > 0 }
        Pair(valid.maxOrNull() ?: 0f, if (valid.isNotEmpty()) valid.average().toFloat() else 0f)
    }
    val (batteryPeak, batteryAvg) = remember(activeProfilerSnapshots) {
        val valid = activeProfilerSnapshots.map { it.state.batteryTempC }.filter { it > 0 }
        Pair(valid.maxOrNull() ?: 0f, if (valid.isNotEmpty()) valid.average().toFloat() else 0f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FrameXTopBar(
                title = stringResource(R.string.thermal_diagnostics_title),
                onNavigateBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FrameXSpacing.Large)
            ) {
                ThermalStatusBanner(
                    isShizukuAvailable = isShizukuAvailable,
                    hasShizukuPermission = hasShizukuPermission,
                    onOpenShizuku = onOpenShizuku,
                    onRequestShizukuPermission = onRequestShizukuPermission
                )

                ThermalWarningBanner(
                    kpiSummary = kpiSummary,
                    isThrottling = metricsState.isThrottling,
                    thermalStatus = metricsState.thermalStatus,
                    modifier = Modifier.padding(bottom = FrameXSpacing.Medium)
                )

                DevicePlatformStrip(platformInfo = platformInfo)
                Spacer(modifier = Modifier.height(FrameXSpacing.Large))

                ThermalSectionHeader(
                    icon = Icons.Outlined.Analytics,
                    title = "SESSION STUDIO & OVERVIEW"
                )
                Spacer(modifier = Modifier.height(10.dp))

                CollapsibleRecorderCard(
                    config = recordingConfig,
                    installedApps = installedApps,
                    isExpanded = isRecorderExpanded,
                    isRecording = isRecording,
                    sampleCount = sampleCount,
                    onToggleExpand = { isRecorderExpanded = !isRecorderExpanded },
                    onSelectApp = onSelectApp,
                    onToggleMetric = onToggleMetric,
                    onToggleRecording = onToggleRecording,
                    onExportCsv = onExportCsv,
                    onCopyReport = onCopyReport
                )
                Spacer(modifier = Modifier.height(FrameXSpacing.Medium))

                SessionSummaryMatrixCard(
                    config = recordingConfig,
                    kpiSummary = kpiSummary,
                    sessionTimestamp = sessionTimestamp
                )
                Spacer(modifier = Modifier.height(FrameXSpacing.XLarge))

                ThermalSectionHeader(
                    icon = Icons.Outlined.ShowChart,
                    title = "TELEMETRY TIMELINES"
                )
                Spacer(modifier = Modifier.height(10.dp))

                SynchronizedMetricCharts(
                    snapshots = activeProfilerSnapshots,
                    selectedMetrics = recordingConfig.selectedMetrics,
                    kpiSummary = kpiSummary,
                    maxRefreshRateHz = maxRefreshRateHz
                )
                Spacer(modifier = Modifier.height(FrameXSpacing.XLarge))

                ThermalSectionHeader(
                    icon = Icons.Outlined.DeviceThermostat,
                    title = "LIVE HARDWARE SENSORS"
                )
                Spacer(modifier = Modifier.height(10.dp))

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

                Spacer(modifier = Modifier.height(FrameXSpacing.Large))
                ThermalDetailsSection(
                    metricsState = metricsState,
                    isExpanded = isSensorDetailsExpanded,
                    onToggleExpand = { isSensorDetailsExpanded = !isSensorDetailsExpanded }
                )
                Spacer(modifier = Modifier.height(FrameXSpacing.Huge))
            }
        }
    }
}

@Composable
private fun ThermalSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryRed,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}
