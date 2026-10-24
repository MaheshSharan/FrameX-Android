package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.theme.FrameXSpacing
import com.framex.app.ui.theme.StatusEmerald
import com.framex.app.ui.theme.StatusRed
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Live Performance executive telemetry dashboard.
 * Displays CPU and RAM with oscilloscope wave curves and circular progress rings,
 * alongside RAM allocation pool and Frame Time latency metrics.
 */
@Composable
fun SystemHealthGaugesSection(
    ramPercentage: Float,
    cpuPercentage: Float?,
    ramUsedGb: Float,
    ramTotalGb: Float,
    fps: Int = 0,
    maxRefreshRate: Int = 60,
    modifier: Modifier = Modifier
) {
    val cpuVal = (cpuPercentage ?: 0f).coerceIn(0f, 100f)
    val displayFps = if (fps > 0) fps else maxRefreshRate.coerceAtLeast(60)
    val frameTimeMs = 1000f / displayFps.toFloat()
    val frameTimeText = if (frameTimeMs < 10f) {
        String.format(Locale.US, "%.1f ms", frameTimeMs)
    } else {
        "${frameTimeMs.roundToInt()} ms"
    }

    Column(modifier = modifier.padding(horizontal = FrameXSpacing.XLarge)) {
        // Section header with title, info icon, and Real-time badge.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Live Performance",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(StatusEmerald)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Real-time",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.Gray
                )
            }
        }

        // Top Row: CPU & RAM with oscilloscope wave curves and circular progress rings.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OscilloscopeGaugeCard(
                label = "CPU",
                percentage = cpuVal,
                accentColor = StatusRed,
                modifier = Modifier.weight(1f)
            )

            OscilloscopeGaugeCard(
                label = "RAM",
                percentage = ramPercentage,
                accentColor = StatusEmerald,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Row: Detailed RAM Pool & Frame Time with equalizer bars.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // RAM Allocation Tile
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F1117))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "RAM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", ramUsedGb)} / ${String.format(Locale.US, "%.0f", ramTotalGb)} GB",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "${ramPercentage.toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (ramPercentage / 100f).coerceIn(0.02f, 1f))
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFA855F7), Color(0xFF8B5CF6))
                                    )
                                )
                        )
                    }
                }
            }

            // Frame Time Tile
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F1117))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Frame Time",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = frameTimeText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = StatusEmerald
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "≈ $displayFps FPS",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color.Gray
                        )
                    }

                    // Equalizer histogram vertical bars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(30.dp)
                    ) {
                        val barHeights = listOf(0.35f, 0.70f, 0.95f, 0.45f, 0.80f, 0.60f, 0.85f, 0.75f)
                        barHeights.forEach { fraction ->
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(30.dp * fraction)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        StatusEmerald.copy(alpha = (0.25f + fraction * 0.75f).coerceIn(0.2f, 1f))
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Oscilloscope sparkline gauge tile featuring label, wave curve, and circular progress ring with centered text.
 */
@Composable
private fun OscilloscopeGaugeCard(
    label: String,
    percentage: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val gaugeSummary = stringResource(
        R.string.cd_gauge_summary,
        label,
        "${percentage.toInt()} percent, minimum 0 percent, maximum 100 percent"
    )
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = gaugeSummary
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F1117))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glowing oscilloscope curve
                WaveformGraph(
                    accentColor = accentColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .padding(end = 8.dp)
                )

                // Circular gauge with value inside
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor.copy(alpha = 0.15f),
                        strokeWidth = 4.dp
                    )
                    CircularProgressIndicator(
                        progress = { (percentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        strokeWidth = 4.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "${percentage.toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Oscilloscope waveform sparkline drawn with Bezier curves and subtle underglow.
 */
@Composable
private fun WaveformGraph(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val width = size.width
        val height = size.height
        val midY = height * 0.55f

        val path = Path().apply {
            moveTo(0f, midY)
            lineTo(width * 0.12f, midY)
            cubicTo(
                width * 0.22f, midY - height * 0.40f,
                width * 0.32f, midY + height * 0.35f,
                width * 0.45f, midY - height * 0.55f
            )
            cubicTo(
                width * 0.56f, midY + height * 0.50f,
                width * 0.68f, midY - height * 0.25f,
                width * 0.80f, midY + height * 0.15f
            )
            lineTo(width, midY)
        }

        // Faint underglow gradient
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.25f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = height
            )
        )

        // Waveform stroke line
        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
