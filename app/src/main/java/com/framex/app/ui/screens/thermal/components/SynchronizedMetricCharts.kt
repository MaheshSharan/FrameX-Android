package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.RecordableMetric
import com.framex.app.ui.screens.thermal.SessionKpiSummary
import com.framex.app.ui.theme.PrimaryRed
import java.util.Locale

@Composable
fun SynchronizedMetricCharts(
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    selectedMetrics: Set<RecordableMetric>,
    kpiSummary: SessionKpiSummary,
    maxRefreshRateHz: Int,
    modifier: Modifier = Modifier
) {
    val maxFpsScale = maxRefreshRateHz.toFloat().coerceAtLeast(60f)
    val clusterLabels = remember(snapshots) {
        snapshots.lastOrNull()?.state?.cpuClusters?.map { it.label }.orEmpty()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (RecordableMetric.FPS in selectedMetrics || RecordableMetric.TEMPERATURES in selectedMetrics) {
            val fpsTempSeries = remember(maxFpsScale) {
                listOf(
                    TelemetryLineSeries("FPS", PrimaryRed, maxFpsScale, fillArea = true) { it.state.fps.toFloat() },
                    TelemetryLineSeries("TEMP(°C)", Color(0xFFF59E0B), 100f) {
                        if (it.state.thermalCpuC > 0f) it.state.thermalCpuC else it.state.batteryTempC
                    },
                    TelemetryLineSeries("CPU(%)", Color(0xFFA78BFA), 100f) {
                        (it.state.cpuPercentage ?: 0).toFloat()
                    }
                )
            }
            val fpsYLabels = remember(maxRefreshRateHz) {
                listOf(
                    "$maxRefreshRateHz",
                    "${(maxRefreshRateHz * 3) / 4}",
                    "${maxRefreshRateHz / 2}",
                    "${maxRefreshRateHz / 4}",
                    "0"
                )
            }
            val tempRightLabels = remember { listOf("100°", "75°", "50°", "25°", "0°") }
            val footer = remember(kpiSummary.maxFps, kpiSummary.minFps, kpiSummary.avgFps) {
                String.format(
                    Locale.US,
                    "MAX: %.1f FPS    MIN: %.1f FPS    AVG: %.1f FPS",
                    kpiSummary.maxFps,
                    kpiSummary.minFps,
                    kpiSummary.avgFps
                )
            }
            MetricChartCard(
                title = "FPS & Temperature (°C)",
                rightSubtitle = "Left: FPS · Right: °C / %",
                snapshots = snapshots,
                seriesList = fpsTempSeries,
                leftYLabels = fpsYLabels,
                rightYLabels = tempRightLabels,
                footerText = footer
            )
        }

        if (RecordableMetric.FRAME_TIME in selectedMetrics) {
            val maxFtScale = (kpiSummary.maxFrameTimeMs.toFloat().coerceAtLeast(60f) * 1.15f).coerceAtMost(200f)
            val ftTop = maxFtScale.toInt()
            val ftSeries = remember(maxFtScale) {
                listOf(
                    TelemetryLineSeries("Frame Time(ms)", Color(0xFF22D3EE), maxFtScale, fillArea = true) {
                        it.state.frameTimeMs.toFloat()
                    }
                )
            }
            val ftYLabels = remember(ftTop) {
                listOf("${ftTop}ms", "${(ftTop * 3) / 4}ms", "${ftTop / 2}ms", "${ftTop / 4}ms", "0ms")
            }
            val footer = remember(kpiSummary.maxFrameTimeMs) {
                "MAX: ${kpiSummary.maxFrameTimeMs} ms    Dashed Line = 16.6 ms (60 FPS)"
            }
            MetricChartCard(
                title = "Frame Time (ms)",
                rightSubtitle = "Frame Pacing Spikes",
                snapshots = snapshots,
                seriesList = ftSeries,
                leftYLabels = ftYLabels,
                referenceLineRatio = (16.6f / maxFtScale).coerceIn(0f, 1f),
                footerText = footer
            )
        }

        if (RecordableMetric.CPU_USAGE in selectedMetrics) {
            val cpuUsageSeries = remember(clusterLabels) {
                val colors = listOf(Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFF34D399), Color(0xFFF59E0B))
                buildList(clusterLabels.size + 1) {
                    add(
                        TelemetryLineSeries("Total", PrimaryRed, 100f, fillArea = true) {
                            (it.state.cpuPercentage ?: 0).toFloat()
                        }
                    )
                    clusterLabels.forEachIndexed { idx, label ->
                        add(
                            TelemetryLineSeries(label, colors[idx % colors.size], 100f) { snap ->
                                (snap.state.cpuClusters.getOrNull(idx)?.usagePercent ?: 0).toFloat()
                            }
                        )
                    }
                }
            }
            val pctYLabels = remember { listOf("100%", "75%", "50%", "25%", "0%") }
            MetricChartCard(
                title = "CPU Usage (%)",
                rightSubtitle = "Total & Per-Cluster Load",
                snapshots = snapshots,
                seriesList = cpuUsageSeries,
                leftYLabels = pctYLabels,
                footerText = null
            )
        }

        if (RecordableMetric.CPU_FREQUENCY in selectedMetrics) {
            val cpuFreqSeries = remember(clusterLabels) {
                val colors = listOf(Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFF34D399), Color(0xFFFBBF24))
                if (clusterLabels.isNotEmpty()) {
                    clusterLabels.mapIndexed { idx, label ->
                        TelemetryLineSeries(label, colors[idx % colors.size], 3600f) { snap ->
                            (snap.state.cpuClusters.getOrNull(idx)?.currentMhz ?: 0).toFloat()
                        }
                    }
                } else {
                    listOf(
                        TelemetryLineSeries("Peak MHz", Color(0xFF38BDF8), 3600f, fillArea = true) {
                            it.state.cpuMhz.toFloat()
                        }
                    )
                }
            }
            val mhzYLabels = remember { listOf("3600", "2700", "1800", "900", "0") }
            MetricChartCard(
                title = "CPU Frequency (MHz)",
                rightSubtitle = "Per-Cluster Clock (MHz)",
                snapshots = snapshots,
                seriesList = cpuFreqSeries,
                leftYLabels = mhzYLabels,
                footerText = null
            )
        }

        if (RecordableMetric.POWER in selectedMetrics) {
            val powerScale = (kpiSummary.maxPowerWatts.coerceAtLeast(12f) * 1.15f).coerceAtMost(30f)
            val pTop = powerScale.toInt().coerceAtLeast(12)
            val powerSeries = remember(powerScale) {
                listOf(
                    TelemetryLineSeries("Power(W)", Color(0xFF3B82F6), powerScale, fillArea = true) {
                        it.state.powerWatts
                    },
                    TelemetryLineSeries("Capacity(%)", Color(0xFF34D399), 100f) {
                        it.state.batteryLevel.coerceAtLeast(0).toFloat()
                    }
                )
            }
            val powerLeftLabels = remember(pTop) {
                listOf("${pTop}W", "${(pTop * 3) / 4}W", "${pTop / 2}W", "${pTop / 4}W", "0W")
            }
            val capRightLabels = remember { listOf("100%", "75%", "50%", "25%", "0%") }
            val footer = remember(kpiSummary.maxPowerWatts, kpiSummary.minPowerWatts, kpiSummary.avgPowerWatts) {
                String.format(
                    Locale.US,
                    "MAX: %.2fW    MIN: %.2fW    AVG: %.2fW",
                    kpiSummary.maxPowerWatts,
                    kpiSummary.minPowerWatts,
                    kpiSummary.avgPowerWatts
                )
            }
            MetricChartCard(
                title = "Power (W) & Capacity (%)",
                rightSubtitle = "Left: Watts · Right: Battery %",
                snapshots = snapshots,
                seriesList = powerSeries,
                leftYLabels = powerLeftLabels,
                rightYLabels = capRightLabels,
                footerText = footer
            )
        }

        if (RecordableMetric.TEMPERATURES in selectedMetrics) {
            val tempSeries = remember {
                listOf(
                    TelemetryLineSeries("SOC(°C)", PrimaryRed, 100f, fillArea = true) {
                        if (it.state.thermalCpuC > 0f) it.state.thermalCpuC else it.state.batteryTempC
                    },
                    TelemetryLineSeries("Battery(°C)", Color(0xFF10B981), 100f) {
                        it.state.batteryTempC
                    }
                )
            }
            val tempYLabels = remember { listOf("100°", "75°", "50°", "25°", "0°") }
            val footer = remember(kpiSummary.maxSocTempC, kpiSummary.minSocTempC, kpiSummary.avgSocTempC) {
                String.format(
                    Locale.US,
                    "MAX: %.1f°C    MIN: %.1f°C    AVG: %.1f°C",
                    kpiSummary.maxSocTempC,
                    kpiSummary.minSocTempC,
                    kpiSummary.avgSocTempC
                )
            }
            MetricChartCard(
                title = "CPU / SoC Temperature (°C)",
                rightSubtitle = "0–100°C Scale",
                snapshots = snapshots,
                seriesList = tempSeries,
                leftYLabels = tempYLabels,
                footerText = footer
            )
        }
    }
}
