package com.framex.app.ui.screens.thermal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.framex.app.metrics.SensorTemperatureAdvisory
import com.framex.app.metrics.ThermalSeverity
import com.framex.app.ui.screens.thermal.components.GraphLegend
import com.framex.app.ui.screens.thermal.components.ModernInteractiveGraph
import com.framex.app.ui.screens.thermal.components.ReadingCard
import com.framex.app.ui.screens.thermal.components.ThermalDetailsSection
import com.framex.app.ui.screens.thermal.components.ThermalExportSection
import com.framex.app.ui.screens.thermal.components.ThermalGraphControls
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThermalDiagnosticsViewModel = hiltViewModel()
) {
    val metricsState by viewModel.metricsState.collectAsState()
    val snapshotHistory by viewModel.snapshotHistory.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val hasShizukuPermission by viewModel.hasShizukuPermission.collectAsState()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShizukuState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val persistedTimeWindowName by viewModel.thermalTimeWindow.collectAsState()
    val persistedGraphModeName by viewModel.thermalGraphMode.collectAsState()
    var selectedWindow by remember(persistedTimeWindowName) {
        mutableStateOf(
            runCatching { TimeWindow.valueOf(persistedTimeWindowName) }.getOrDefault(TimeWindow.SEC_60)
        )
    }
    var selectedGraphMode by remember(persistedGraphModeName) {
        mutableStateOf(
            runCatching { GraphMetricMode.valueOf(persistedGraphModeName) }.getOrDefault(GraphMetricMode.FPS_THERMAL)
        )
    }
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
        val prev = snapshot30sAgo?.state?.thermalCpuC ?: metricsState.thermalCpuC
        metricsState.thermalCpuC - prev
    }

    val skinDelta = remember(metricsState.thermalSkinC, snapshot30sAgo) {
        val prev = snapshot30sAgo?.state?.thermalSkinC ?: metricsState.thermalSkinC
        metricsState.thermalSkinC - prev
    }

    val batteryDelta = remember(metricsState.batteryTempC, snapshot30sAgo) {
        val prev = snapshot30sAgo?.state?.batteryTempC ?: metricsState.batteryTempC
        metricsState.batteryTempC - prev
    }

    // Session peaks remain available for the diagnostic timeline. Card averages use a
    // timestamp-based recent window so they describe current thermal conditions.
    val cpuPeak = remember(snapshotHistory) { snapshotHistory.maxOfOrNull { it.state.thermalCpuC } ?: metricsState.thermalCpuC }
    val gpuPeak = remember(snapshotHistory) { snapshotHistory.maxOfOrNull { it.state.thermalGpuC } ?: metricsState.thermalGpuC }
    val skinPeak = remember(snapshotHistory) { snapshotHistory.maxOfOrNull { it.state.thermalSkinC } ?: metricsState.thermalSkinC }
    val batteryPeak = remember(snapshotHistory) { snapshotHistory.maxOfOrNull { it.state.batteryTempC } ?: metricsState.batteryTempC }

    val cpuAvg = remember(filteredSnapshots, metricsState.thermalCpuC) {
        val valid = filteredSnapshots.filter { it.state.thermalCpuC > 0f }
        if (valid.isNotEmpty()) valid.map { it.state.thermalCpuC }.average().toFloat() else metricsState.thermalCpuC
    }
    val gpuAvg = remember(filteredSnapshots, metricsState.thermalGpuC) {
        val valid = filteredSnapshots.filter { it.state.thermalGpuC > 0f }
        if (valid.isNotEmpty()) valid.map { it.state.thermalGpuC }.average().toFloat() else metricsState.thermalGpuC
    }
    val skinAvg = remember(filteredSnapshots, metricsState.thermalSkinC) {
        val valid = filteredSnapshots.filter { it.state.thermalSkinC > 0f }
        if (valid.isNotEmpty()) valid.map { it.state.thermalSkinC }.average().toFloat() else metricsState.thermalSkinC
    }
    val batteryAvg = remember(filteredSnapshots, metricsState.batteryTempC) {
        val valid = filteredSnapshots.filter { it.state.batteryTempC > 0f }
        if (valid.isNotEmpty()) valid.map { it.state.batteryTempC }.average().toFloat() else metricsState.batteryTempC
    }

    val likelyCause = remember(filteredSnapshots, metricsState) {
        evaluateLikelyCause(filteredSnapshots, metricsState)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    .padding(horizontal = 20.dp),
            ) {
                // Shizuku CTA Banner if needed
                if (!isShizukuAvailable || !hasShizukuPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (!isShizukuAvailable) "Shizuku Service Not Running" else "Shizuku Authorization Required",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (!isShizukuAvailable) {
                                    "Start Shizuku via wireless debugging or adb to view active CPU/GPU thermal sensors and top processes."
                                } else {
                                    "Authorize FrameX in the Shizuku app to read system thermal stats and CPU dumps."
                                },
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        if (intent != null) context.startActivity(intent)
                                        else Toast.makeText(context, "Shizuku app not found", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f), contentColor = Color.White),
                                    modifier = Modifier.height(36.dp),
                                    shape = CircleShape
                                ) {
                                    Text("Open Shizuku", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (isShizukuAvailable && !hasShizukuPermission) {
                                    Button(
                                        onClick = { viewModel.requestShizukuPermission() },
                                        modifier = Modifier.height(36.dp),
                                        shape = CircleShape
                                    ) {
                                        Text("Grant Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Thermal Status Banner (Primary system state from OS status + explicit sensor advisory)
                val systemSeverity = ThermalSeverity.fromStatus(metricsState.thermalStatus)
                val statusText = getThermalStatusLabel(metricsState.thermalStatus)
                val sensorAdvisory = SensorTemperatureAdvisory.fromTemperatures(metricsState.thermalCpuC, metricsState.thermalSkinC)
                val pressureText = if (metricsState.isThrottling) "CPU Throttled" else computeThermalPressure(metricsState.thermalStatus, cpuDelta, skinDelta)

                val bannerColor = when {
                    systemSeverity != ThermalSeverity.NONE -> systemSeverity.color
                    metricsState.isThrottling -> Color(0xFFEF4444)
                    else -> sensorAdvisory.color
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bannerColor.copy(alpha = 0.12f))
                        .border(1.dp, bannerColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(bannerColor))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "System Thermal State: $statusText",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sensor Advisory: ${sensorAdvisory.displayLabel} · Hardware Pressure: $pressureText",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Likely Cause Diagnostic Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = likelyCause.color.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, likelyCause.color.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(likelyCause.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = likelyCause.color, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = likelyCause.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = likelyCause.description,
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Smart Equal-Height Reading Cards Grid
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    com.framex.app.ui.screens.thermal.components.TopProcessCard(
                        topProcesses = metricsState.topProcesses,
                        readStatus = metricsState.topProcessReadStatus,
                        topProcessName = metricsState.topProcessName,
                        topProcessCpuPercent = metricsState.topProcessCpuPercent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. Interactive Graph Section with Exposed Dropdowns
                Text("Performance & Thermal Timeline", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Select timeframe and metrics, then drag across the canvas to scrub telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(14.dp))

                ThermalGraphControls(
                    selectedWindow = selectedWindow,
                    onWindowSelected = { window ->
                        selectedWindow = window
                        viewModel.setThermalTimeWindow(window)
                    },
                    selectedMode = selectedGraphMode,
                    onModeSelected = { mode ->
                        selectedGraphMode = mode
                        viewModel.setThermalGraphMode(mode)
                    }
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Expandable Sensor Details
                ThermalDetailsSection(
                    metricsState = metricsState,
                    isExpanded = isSensorDetailsExpanded,
                    onToggleExpand = { isSensorDetailsExpanded = !isSensorDetailsExpanded }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 6. Action Buttons Layout with Restored Green "Ready to Export" Banner
                val sampleCount = viewModel.recordedSampleCount(snapshotHistory)
                ThermalExportSection(
                    isRecording = isRecording,
                    sampleCount = sampleCount,
                    onToggleRecording = { viewModel.toggleRecording() },
                    onExportCsv = {
                        viewModel.exportAndShare(
                            onReady = { intent -> context.startActivity(Intent.createChooser(intent, "Share session log")) },
                            onEmpty = { Toast.makeText(context, "Nothing recorded yet — start a session first", Toast.LENGTH_SHORT).show() }
                        )
                    },
                    onCopyReport = {
                        val summary = viewModel.buildDiagnosticSummaryText(snapshotHistory)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("FrameX Diagnostic Summary", summary))
                        Toast.makeText(context, "Diagnostic summary copied to clipboard!", Toast.LENGTH_LONG).show()
                    }
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
