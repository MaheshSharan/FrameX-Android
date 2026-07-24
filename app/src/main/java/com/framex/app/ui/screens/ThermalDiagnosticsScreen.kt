package com.framex.app.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.metrics.MetricReadStatus
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.SessionLogger
import com.framex.app.metrics.ThermalSeverity
import kotlin.math.roundToInt
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class ThermalDiagnosticsViewModel @Inject constructor(
    private val metricsEngine: MetricsEngine,
    private val sessionLogger: SessionLogger,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    val metricsState = metricsEngine.metricsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetricsState())

    val snapshotHistory = metricsEngine.snapshotHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRecording = sessionLogger.isRecording
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isShizukuAvailable = shizukuManager.isShizukuAvailable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasShizukuPermission = shizukuManager.hasPermission
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Force "thermal", "temp", and "top_process" on for this screen.
        // Uses a distinct requester key so it can't collide
        // with SessionLogger's own override when a recording is also active.
        metricsEngine.setScreenOverrideModules(
            setOf("thermal", "temp", "top_process"),
            requesterKey = "thermal_diagnostics_screen"
        )
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "thermal_diagnostics_screen")
    }

    fun toggleRecording() {
        if (sessionLogger.isRecording.value) {
            sessionLogger.stopRecording()
        } else {
            sessionLogger.startRecording()
        }
    }

    fun recordedSampleCount(snapshots: List<MetricsEngine.MetricsSnapshot>): Int {
        val startIndex = sessionLogger.recordingStartIndex
        return (snapshots.size - startIndex).coerceAtLeast(0)
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun refreshShizukuState() {
        shizukuManager.refreshState()
    }

    fun exportAndShare(onReady: (Intent) -> Unit, onEmpty: () -> Unit) {
        viewModelScope.launch {
            val file = sessionLogger.exportToFile()
            if (file == null) {
                onEmpty()
            } else {
                onReady(sessionLogger.buildShareIntent(file))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------



private fun getThermalDisplayValue(value: Float, present: Boolean, readStatus: MetricReadStatus): String {
    return when (readStatus) {
        MetricReadStatus.NoShizuku -> "Needs Shizuku"
        MetricReadStatus.EmptyOutput -> "Hardware Not Supported"
        MetricReadStatus.ParseFailed -> "Parse Failed"
        MetricReadStatus.Loading -> "Waiting..."
        else -> {
            if (present) {
                String.format(java.util.Locale.US, "%.1f°C", value)
            } else {
                "Hardware Not Supported"
            }
        }
    }
}

private fun getBatteryDisplayValue(tempC: Float): String {
    return if (tempC <= 0f) "Unavailable" else String.format(java.util.Locale.US, "%.1f°C", tempC)
}

private fun getTopProcessDisplayValue(name: String?, percent: Float, readStatus: MetricReadStatus): String {
    return when (readStatus) {
        MetricReadStatus.NoShizuku -> "Needs Shizuku"
        MetricReadStatus.EmptyOutput -> "Unavailable"
        MetricReadStatus.ParseFailed -> "Parse Failed"
        MetricReadStatus.Loading -> "Waiting..."
        MetricReadStatus.NoData -> "No process data"
        MetricReadStatus.Ok -> {
            if (name != null) {
                "$name (${String.format(java.util.Locale.US, "%.0f", percent)}%)"
            } else {
                "—"
            }
        }
        else -> "—"
    }
}

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

    // Re-check Shizuku state on resume
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

    // Last 60 snapshots (~60s at 1s cadence) drive the correlation graph — same
    // window size as the Dashboard's FPS sparkline, so the two feel consistent.
    val recentSnapshots = remember(snapshotHistory) { snapshotHistory.takeLast(60) }

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
                    Text("Find what's causing frame drops", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Shizuku CTA Banner
                if (!isShizukuAvailable || !hasShizukuPermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
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
                                        if (intent != null) {
                                            context.startActivity(intent)
                                        } else {
                                            Toast.makeText(context, "Shizuku app not found", Toast.LENGTH_SHORT).show()
                                        }
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

                // Current status banner — the single most useful glance value.
                val severity = ThermalSeverity.fromStatus(metricsState.thermalStatus)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(severity.color.copy(alpha = 0.12f))
                        .border(1.dp, severity.color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(severity.color))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Thermal Status: ${severity.label}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            severity.description,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Readings grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingCard("CPU", getThermalDisplayValue(metricsState.thermalCpuC, metricsState.hasThermalCpu, metricsState.thermalReadStatus), Modifier.weight(1f))
                    ReadingCard("GPU", getThermalDisplayValue(metricsState.thermalGpuC, metricsState.hasThermalGpu, metricsState.thermalReadStatus), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingCard("SKIN", getThermalDisplayValue(metricsState.thermalSkinC, metricsState.hasThermalSkin, metricsState.thermalReadStatus), Modifier.weight(1f))
                    ReadingCard("NPU", getThermalDisplayValue(metricsState.thermalNpuC, metricsState.hasThermalNpu, metricsState.thermalReadStatus), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingCard("BATTERY", getBatteryDisplayValue(metricsState.batteryTempC), Modifier.weight(1f))
                    ReadingCard("JANKY FRAMES", "${metricsState.jankyFrames}", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    ReadingCard(
                        "TOP PROCESS",
                        getTopProcessDisplayValue(metricsState.topProcessName, metricsState.topProcessCpuPercent, metricsState.topProcessReadStatus),
                        Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("FPS vs Thermal — last 60s", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    "Look for the moment FPS drops and check what moved at the same second below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                CorrelationGraph(
                    snapshots = recentSnapshots,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(Color(0xFF60A5FA)); Text(" FPS   ", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    LegendDot(Color(0xFFEF4444)); Text(" CPU Temp   ", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    LegendDot(Color(0xFFFBBF24)); Text(" SKIN Temp", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text("Session Recording", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    "Record a full gaming session, then export and share the log — every metric, every second, even ones not shown on your overlay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))

                val count = viewModel.recordedSampleCount(snapshotHistory)
                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recording — ${count}s captured", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (count > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF34D399).copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Last Session — ${count}s ready to export", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.toggleRecording() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRecording) "Stop" else "Start Recording", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.exportAndShare(
                                onReady = { intent ->
                                    context.startActivity(Intent.createChooser(intent, "Share session log"))
                                },
                                onEmpty = {
                                    Toast.makeText(context, "Nothing recorded yet — start a session first", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export & Share")
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun ReadingCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            com.framex.app.ui.components.WovenNetBackground(modifier = Modifier.matchParentSize())

            Column(modifier = Modifier.padding(16.dp)) {
                val (plateBg, plateBorder, iconColor, iconVector) = when (label) {
                    "CPU" -> Quadruple(Color(0xFFEF4444).copy(alpha = 0.14f), Color(0xFFEF4444).copy(alpha = 0.28f), Color(0xFFF87171), Icons.Default.Memory)
                    "GPU" -> Quadruple(Color(0xFF3B82F6).copy(alpha = 0.14f), Color(0xFF3B82F6).copy(alpha = 0.28f), Color(0xFF60A5FA), Icons.Default.Speed)
                    "SKIN" -> Quadruple(Color(0xFFF59E0B).copy(alpha = 0.14f), Color(0xFFF59E0B).copy(alpha = 0.28f), Color(0xFFFBBF24), Icons.Default.Thermostat)
                    "NPU" -> Quadruple(Color(0xFF8B5CF6).copy(alpha = 0.14f), Color(0xFF8B5CF6).copy(alpha = 0.28f), Color(0xFFA78BFA), Icons.Default.DeveloperBoard)
                    "BATTERY" -> Quadruple(Color(0xFF10B981).copy(alpha = 0.14f), Color(0xFF10B981).copy(alpha = 0.28f), Color(0xFF34D399), Icons.Default.BatteryChargingFull)
                    else -> Quadruple(Color(0xFFEC4899).copy(alpha = 0.14f), Color(0xFFEC4899).copy(alpha = 0.28f), Color(0xFFF472B6), Icons.Default.LocalFireDepartment)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(plateBg)
                            .border(1.dp, plateBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconVector, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    value,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun CorrelationGraph(
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (snapshots.size < 2) {
            Text("Collecting samples...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        } else {
            val textMeasurer = rememberTextMeasurer()
            val hasCpu = snapshots.any { it.state.hasThermalCpu }
            val hasSkin = snapshots.any { it.state.hasThermalSkin }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxFps = (snapshots.maxOfOrNull { it.state.fps } ?: 60).coerceAtLeast(30)
                
                val cpuTemps = if (hasCpu) snapshots.map { it.state.thermalCpuC } else emptyList()
                val skinTemps = if (hasSkin) snapshots.map { it.state.thermalSkinC } else emptyList()
                val maxTemp = (cpuTemps + skinTemps).maxOrNull()?.coerceAtLeast(40f) ?: 80f

                val leftPadding = 32.dp.toPx()
                val rightPadding = 32.dp.toPx()
                val bottomPadding = 20.dp.toPx()
                val topPadding = 12.dp.toPx()

                val graphWidth = size.width - leftPadding - rightPadding
                val graphHeight = size.height - topPadding - bottomPadding

                if (graphWidth <= 0f || graphHeight <= 0f) return@Canvas

                val gridSteps = 3
                val labelStyle = TextStyle(
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )

                // 1. Draw Gridlines and Y-axis tick labels
                for (i in 0..gridSteps) {
                    val ratio = i.toFloat() / gridSteps
                    val y = topPadding + graphHeight * (1f - ratio)

                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(leftPadding, y),
                        end = Offset(leftPadding + graphWidth, y),
                        strokeWidth = 1f
                    )

                    // FPS Y-axis label (left)
                    val fpsVal = (maxFps * ratio).toInt()
                    val fpsLayout = textMeasurer.measure("$fpsVal", labelStyle)
                    drawText(
                        textLayoutResult = fpsLayout,
                        topLeft = Offset(leftPadding - fpsLayout.size.width - 4.dp.toPx(), y - fpsLayout.size.height / 2f)
                    )

                    // Temperature Y-axis label (right)
                    val tempVal = (maxTemp * ratio).toInt()
                    val tempLayout = textMeasurer.measure("${tempVal}°", labelStyle)
                    drawText(
                        textLayoutResult = tempLayout,
                        topLeft = Offset(leftPadding + graphWidth + 4.dp.toPx(), y - tempLayout.size.height / 2f)
                    )
                }

                // 2. Draw X-axis time labels
                val timeLabels = listOf("60s ago", "45s", "30s", "15s", "now")
                for (i in timeLabels.indices) {
                    val ratio = i.toFloat() / (timeLabels.size - 1)
                    val x = leftPadding + graphWidth * ratio
                    val labelLayout = textMeasurer.measure(timeLabels[i], labelStyle)
                    val labelX = (x - labelLayout.size.width / 2f).coerceIn(0f, size.width - labelLayout.size.width)
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(labelX, size.height - bottomPadding + 4.dp.toPx())
                    )
                }

                // 3. Draw Data Lines
                val stepX = graphWidth / (snapshots.size - 1).coerceAtLeast(1)

                fun pointsFor(values: List<Float>, max: Float): List<Offset> =
                    values.mapIndexed { i, v ->
                        Offset(
                            leftPadding + i * stepX,
                            topPadding + graphHeight - (v / max).coerceIn(0f, 1f) * graphHeight
                        )
                    }

                val fpsPoints = pointsFor(snapshots.map { it.state.fps.toFloat() }, maxFps.toFloat())

                fun drawPolyline(points: List<Offset>, color: Color) {
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = color,
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (hasCpu) {
                    val cpuPoints = pointsFor(cpuTemps, maxTemp)
                    drawPolyline(cpuPoints, Color(0xFFEF4444).copy(alpha = 0.8f))
                }
                if (hasSkin) {
                    val skinPoints = pointsFor(skinTemps, maxTemp)
                    drawPolyline(skinPoints, Color(0xFFFBBF24).copy(alpha = 0.8f))
                }
                drawPolyline(fpsPoints, Color(0xFF60A5FA))
            }
            
            if (!hasCpu && !hasSkin) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Thermal series unavailable", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
