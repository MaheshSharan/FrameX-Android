package com.framex.app.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.SessionLogger
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
    private val sessionLogger: SessionLogger
) : ViewModel() {

    val metricsState = metricsEngine.metricsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetricsState())

    val snapshotHistory = metricsEngine.snapshotHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRecording = sessionLogger.isRecording
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Force "thermal" and "temp" on for this screen — "temp" (battery) previously
        // wasn't forced here, so BATTERY read 0 unless the user separately had it
        // toggled on their overlay. Uses a distinct requester key so it can't collide
        // with SessionLogger's own override when a recording is also active.
        metricsEngine.setScreenOverrideModules(setOf("thermal", "temp"), requesterKey = "thermal_diagnostics_screen")
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

    fun recordedSampleCount(): Int = sessionLogger.currentRecordingCount()

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

private fun thermalStatusLabel(status: Int): String = when (status) {
    0 -> "NONE"; 1 -> "LIGHT"; 2 -> "MODERATE"; 3 -> "SEVERE"
    4 -> "CRITICAL"; 5 -> "EMERGENCY"; 6 -> "SHUTDOWN"; else -> "UNKNOWN"
}

private fun thermalStatusColor(status: Int): Color = when {
    status <= 1 -> Color(0xFF34D399) // green — none/light
    status == 2 -> Color(0xFFFBBF24) // amber — moderate (this is what tripped at your 40.6°C skin reading)
    status == 3 -> Color(0xFFF97316) // orange — severe
    else -> Color(0xFFEF4444)        // red — critical and above
}

@Composable
fun ThermalDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThermalDiagnosticsViewModel = hiltViewModel()
) {
    val metricsState by viewModel.metricsState.collectAsState()
    val snapshotHistory by viewModel.snapshotHistory.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                // Current status banner — the single most useful glance value.
                val status = metricsState.thermalStatus
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(thermalStatusColor(status).copy(alpha = 0.12f))
                        .border(1.dp, thermalStatusColor(status).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(thermalStatusColor(status)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Thermal Status: ${thermalStatusLabel(status)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (status >= 2) "Android is actively throttling performance right now"
                            else "No throttling active",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Readings grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingCard("CPU", String.format("%.1f°C", metricsState.thermalCpuC), Modifier.weight(1f))
                    ReadingCard("GPU", String.format("%.1f°C", metricsState.thermalGpuC), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingCard("SKIN", String.format("%.1f°C", metricsState.thermalSkinC), Modifier.weight(1f))
                    ReadingCard("BATTERY", String.format("%.1f°C", metricsState.batteryTempC), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingCard("JANKY FRAMES", "${metricsState.jankyFrames}", Modifier.weight(1f))
                    ReadingCard(
                        "TOP PROCESS",
                        metricsState.topProcessName?.let { "$it (${String.format("%.0f", metricsState.topProcessCpuPercent)}%)" } ?: "—",
                        Modifier.weight(1f)
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

                if (isRecording) {
                    val count = viewModel.recordedSampleCount()
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun CorrelationGraph(
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (snapshots.size < 2) return@Canvas

        val maxFps = (snapshots.maxOfOrNull { it.state.fps } ?: 1).coerceAtLeast(1)
        val maxTemp = (snapshots.maxOfOrNull { maxOf(it.state.thermalCpuC, it.state.thermalSkinC) } ?: 1f).coerceAtLeast(1f)

        val stepX = size.width / (snapshots.size - 1).coerceAtLeast(1)

        fun pointsFor(values: List<Float>, max: Float): List<Offset> =
            values.mapIndexed { i, v ->
                Offset(i * stepX, size.height - (v / max) * size.height)
            }

        val fpsPoints = pointsFor(snapshots.map { it.state.fps.toFloat() }, maxFps.toFloat())
        val cpuPoints = pointsFor(snapshots.map { it.state.thermalCpuC }, maxTemp)
        val skinPoints = pointsFor(snapshots.map { it.state.thermalSkinC }, maxTemp)

        fun drawLine(points: List<Offset>, color: Color) {
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

        drawLine(cpuPoints, Color(0xFFEF4444).copy(alpha = 0.8f))
        drawLine(skinPoints, Color(0xFFFBBF24).copy(alpha = 0.8f))
        drawLine(fpsPoints, Color(0xFF60A5FA))
    }
}
