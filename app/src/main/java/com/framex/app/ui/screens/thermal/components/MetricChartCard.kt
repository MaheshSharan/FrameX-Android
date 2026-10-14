package com.framex.app.ui.screens.thermal.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.metrics.MetricsEngine
import com.framex.app.ui.components.WovenNetBackground
import com.framex.app.ui.screens.thermal.formatDurationMinSec
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes

@Immutable
internal data class TelemetryLineSeries(
    val label: String,
    val color: Color,
    val maxScale: Float,
    val fillArea: Boolean = false,
    val selector: (MetricsEngine.MetricsSnapshot) -> Float
)

/**
 * Reusable allocation-free Canvas card for a multi-series telemetry chart with
 * Left Y-axis numbers, optional Right Y-axis numbers, and Bottom X-axis time markers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MetricChartCard(
    title: String,
    rightSubtitle: String,
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    seriesList: List<TelemetryLineSeries>,
    leftYLabels: List<String>,
    rightYLabels: List<String>? = null,
    referenceLineRatio: Float? = null,
    footerText: String? = null
) {
    val density = LocalDensity.current
    val strokePaths = remember(seriesList.size) { List(seriesList.size) { Path() } }
    val fillPaths = remember(seriesList.size) { List(seriesList.size) { Path() } }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }

    val leftAxisPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(175, 190, 195, 205)
            textSize = with(density) { 9.5.sp.toPx() }
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.LEFT
        }
    }
    val rightAxisPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(175, 190, 195, 205)
            textSize = with(density) { 9.5.sp.toPx() }
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }
    }

    val xLabels = remember(snapshots.size, snapshots.firstOrNull()?.timestampMs, snapshots.lastOrNull()?.timestampMs) {
        val totalSec = if (snapshots.size >= 2) {
            ((snapshots.last().timestampMs - snapshots.first().timestampMs) / 1000L).toInt().coerceAtLeast(snapshots.size)
        } else {
            60
        }
        List(5) { idx ->
            val sec = (totalSec * idx) / 4
            formatDurationMinSec(sec)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FrameXShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(FrameXBorders.ActiveBorderWidth, FrameXBorders.CardStroke)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            WovenNetBackground(modifier = Modifier.matchParentSize())

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = rightSubtitle,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    val leftGutter = 34.dp.toPx()
                    val rightGutter = if (rightYLabels != null) 34.dp.toPx() else 6.dp.toPx()
                    val topPad = 8.dp.toPx()
                    val bottomPad = 8.dp.toPx()
                    val plotW = (size.width - leftGutter - rightGutter).coerceAtLeast(10f)
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(10f)

                    val gridCol = Color.White.copy(alpha = 0.06f)
                    val steps = (leftYLabels.size - 1).coerceAtLeast(1)

                    for (i in leftYLabels.indices) {
                        val y = topPad + plotH * (i.toFloat() / steps)
                        drawLine(
                            color = gridCol,
                            start = Offset(leftGutter, y),
                            end = Offset(leftGutter + plotW, y),
                            strokeWidth = 1f
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            leftYLabels[i],
                            0f,
                            y + 3.dp.toPx(),
                            leftAxisPaint
                        )
                        if (rightYLabels != null && i < rightYLabels.size) {
                            drawContext.canvas.nativeCanvas.drawText(
                                rightYLabels[i],
                                size.width,
                                y + 3.dp.toPx(),
                                rightAxisPaint
                            )
                        }
                    }

                    if (referenceLineRatio != null && referenceLineRatio in 0f..1f) {
                        val refY = topPad + plotH * (1f - referenceLineRatio)
                        drawLine(
                            color = Color(0xFF34D399).copy(alpha = 0.35f),
                            start = Offset(leftGutter, refY),
                            end = Offset(leftGutter + plotW, refY),
                            strokeWidth = 1.5f,
                            pathEffect = dashEffect
                        )
                    }

                    val count = snapshots.size
                    if (count < 2) return@Canvas
                    val stepX = plotW / (count - 1).coerceAtLeast(1)
                    val plotBottom = topPad + plotH

                    for (sIdx in seriesList.indices) {
                        val series = seriesList[sIdx]
                        val linePath = strokePaths[sIdx].apply { reset() }
                        val areaPath = fillPaths[sIdx].apply { reset() }
                        val maxVal = series.maxScale.coerceAtLeast(1f)

                        for (i in 0 until count) {
                            val raw = series.selector(snapshots[i]).coerceIn(0f, maxVal)
                            val x = leftGutter + i * stepX
                            val y = plotBottom - (raw / maxVal) * plotH
                            if (i == 0) {
                                linePath.moveTo(x, y)
                                if (series.fillArea) {
                                    areaPath.moveTo(x, plotBottom)
                                    areaPath.lineTo(x, y)
                                }
                            } else {
                                linePath.lineTo(x, y)
                                if (series.fillArea) {
                                    areaPath.lineTo(x, y)
                                }
                            }
                        }

                        if (series.fillArea) {
                            areaPath.lineTo(leftGutter + (count - 1) * stepX, plotBottom)
                            areaPath.close()
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(series.color.copy(alpha = 0.22f), Color.Transparent),
                                    startY = topPad,
                                    endY = plotBottom
                                )
                            )
                        }

                        drawPath(
                            path = linePath,
                            color = series.color,
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 34.dp, end = if (rightYLabels != null) 34.dp else 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    xLabels.forEach { tLabel ->
                        Text(
                            text = tLabel,
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    seriesList.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(s.color)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(s.label, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (!footerText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = footerText,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
