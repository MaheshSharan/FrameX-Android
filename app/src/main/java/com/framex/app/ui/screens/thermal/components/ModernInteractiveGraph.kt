package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.framex.app.metrics.MetricsEngine
import com.framex.app.ui.screens.thermal.GraphSeries
import com.framex.app.ui.screens.thermal.TimeWindow
import com.framex.app.ui.screens.thermal.formatSecondsAgo
import java.util.Locale
import kotlin.math.roundToInt

// Raised from 0.07f: too faint against the dark background per issue #55 feedback.
private const val GRID_LINE_ALPHA = 0.16f

@Composable
fun GraphLegend(
    series: List<GraphSeries>,
    modifier: Modifier = Modifier,
    hasThrottling: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        series.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(entry.color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(entry.label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (hasThrottling) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Throttled", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val FULL_SESSION_AXIS_CAP_SECONDS = 6 * 3600

@Composable
fun ModernInteractiveGraph(
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    series: List<GraphSeries>,
    window: TimeWindow,
    modifier: Modifier = Modifier
) {
    var touchX by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { offset -> touchX = offset.x },
                onDragEnd = { touchX = null },
                onDragCancel = { touchX = null },
                onHorizontalDrag = { change, _ -> touchX = change.position.x }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        if (snapshots.size < 2) {
            Text("Collecting telemetry samples...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        } else {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val gridDashEffect = remember {
                PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            }

            val leftPaddingDp = 34.dp
            val rightPaddingDp = 12.dp
            var canvasWidthPx by remember { mutableStateOf(0f) }
            val scrubIndex = remember(touchX, snapshots.size, canvasWidthPx) {
                val x = touchX ?: return@remember null
                val leftPaddingPx = with(density) { leftPaddingDp.toPx() }
                val rightPaddingPx = with(density) { rightPaddingDp.toPx() }
                val graphWidthPx = canvasWidthPx - leftPaddingPx - rightPaddingPx
                if (graphWidthPx <= 0f || x !in leftPaddingPx..(leftPaddingPx + graphWidthPx)) return@remember null
                val stepXPx = graphWidthPx / (snapshots.size - 1).coerceAtLeast(1)
                ((x - leftPaddingPx) / stepXPx).roundToInt().coerceIn(0, snapshots.size - 1)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasWidthPx = it.width.toFloat() }
            ) {
                val leftPadding = leftPaddingDp.toPx()
                val rightPadding = rightPaddingDp.toPx()
                val bottomPadding = 22.dp.toPx()
                val topPadding = 12.dp.toPx()

                val graphWidth = size.width - leftPadding - rightPadding
                val graphHeight = size.height - topPadding - bottomPadding
                if (graphWidth <= 0f || graphHeight <= 0f) return@Canvas

                val gridSteps = 4
                val labelStyle = TextStyle(color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Medium)

                // 1. Dashed horizontal grid
                val primaryScale = series.first().maxScale
                val primaryUnit = series.first().unitSuffix
                for (i in 0..gridSteps) {
                    val ratio = i.toFloat() / gridSteps
                    val y = topPadding + graphHeight * (1f - ratio)

                    drawLine(
                        color = Color.White.copy(alpha = GRID_LINE_ALPHA),
                        start = Offset(leftPadding, y),
                        end = Offset(leftPadding + graphWidth, y),
                        strokeWidth = 1f,
                        pathEffect = gridDashEffect
                    )

                    val tickVal = (primaryScale * ratio).roundToInt()
                    val tickLayout = textMeasurer.measure("$tickVal$primaryUnit", labelStyle)
                    drawText(tickLayout, topLeft = Offset(leftPadding - tickLayout.size.width - 6.dp.toPx(), y - tickLayout.size.height / 2f))
                }

                // 2. X-axis Time Labels
                val actualSpanSeconds = if (window == TimeWindow.FULL) {
                    (snapshots.size - 1).coerceAtMost(FULL_SESSION_AXIS_CAP_SECONDS)
                } else {
                    (snapshots.size - 1).coerceAtMost(window.seconds)
                }
                val xAxisLabelCount = 4
                for (labelIndex in 0 until xAxisLabelCount) {
                    val ratio = labelIndex.toFloat() / (xAxisLabelCount - 1)
                    val x = leftPadding + graphWidth * ratio
                    val secondsAgo = (actualSpanSeconds * (1f - ratio)).roundToInt()
                    val label = formatSecondsAgo(secondsAgo)
                    val labelLayout = textMeasurer.measure(label, labelStyle)
                    val labelX = (x - labelLayout.size.width / 2f).coerceIn(0f, size.width - labelLayout.size.width)
                    drawText(labelLayout, topLeft = Offset(labelX, size.height - bottomPadding + 6.dp.toPx()))
                }

                // 3. Bezier Curves and Area Fills
                val stepX = graphWidth / (snapshots.size - 1).coerceAtLeast(1)

                // 3a. Throttling Regions Highlight along the Timeline (Issue #59)
                snapshots.forEachIndexed { i, s ->
                    if (s.state.isThrottling || s.state.thermalStatus >= 2) {
                        val x = leftPadding + i * stepX
                        val halfBand = (stepX / 2f).coerceAtLeast(1.5f)
                        drawRect(
                            color = Color(0xFFEF4444).copy(alpha = 0.16f),
                            topLeft = Offset((x - halfBand).coerceAtLeast(leftPadding), topPadding),
                            size = androidx.compose.ui.geometry.Size(
                                (halfBand * 2f).coerceAtMost((leftPadding + graphWidth) - (x - halfBand)),
                                graphHeight
                            )
                        )
                        drawLine(
                            color = Color(0xFFEF4444),
                            start = Offset(x, topPadding),
                            end = Offset(x, topPadding + 4.dp.toPx()),
                            strokeWidth = 2f
                        )
                    }
                }

                fun pointsFor(entry: GraphSeries): List<Offset> =
                    snapshots.mapIndexed { i, snapshot ->
                        val v = entry.valueOf(snapshot)
                        Offset(
                            leftPadding + i * stepX,
                            topPadding + graphHeight - (v / entry.maxScale).coerceIn(0f, 1f) * graphHeight
                        )
                    }

                fun drawSeries(entry: GraphSeries, points: List<Offset>) {
                    if (points.size < 2) return
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val midX = p1.x + (p2.x - p1.x) / 2f
                            val segmentMinY = minOf(p1.y, p2.y)
                            val segmentMaxY = maxOf(p1.y, p2.y)
                            val control1 = Offset(midX, p1.y.coerceIn(segmentMinY, segmentMaxY))
                            val control2 = Offset(midX, p2.y.coerceIn(segmentMinY, segmentMaxY))
                            cubicTo(control1.x, control1.y, control2.x, control2.y, p2.x, p2.y)
                        }
                    }

                    if (entry.fillArea) {
                        val areaPath = Path().apply {
                            addPath(path)
                            lineTo(points.last().x, topPadding + graphHeight)
                            lineTo(points.first().x, topPadding + graphHeight)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(entry.color.copy(alpha = 0.28f), entry.color.copy(alpha = 0.0f)),
                                startY = topPadding,
                                endY = topPadding + graphHeight
                            )
                        )
                    }

                    drawPath(
                        path = path,
                        color = entry.color.copy(alpha = 0.25f),
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = entry.color,
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                    )
                }

                val seriesPoints = series.map { it to pointsFor(it) }
                clipRect(leftPadding, topPadding, leftPadding + graphWidth, topPadding + graphHeight) {
                    seriesPoints.forEach { (entry, points) -> drawSeries(entry, points) }
                }

                // 4. Touch Scrubbing Crosshair
                val index = scrubIndex
                if (index != null) {
                    val posX = leftPadding + index * stepX

                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(posX, topPadding),
                        end = Offset(posX, topPadding + graphHeight),
                        strokeWidth = 1.5f,
                        pathEffect = gridDashEffect
                    )

                    seriesPoints.forEach { (entry, points) ->
                        val point = points.getOrNull(index) ?: return@forEach
                        drawCircle(color = entry.color.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = point)
                        drawCircle(color = entry.color, radius = 3.5.dp.toPx(), center = point)
                        drawCircle(color = Color.White, radius = 1.4.dp.toPx(), center = point)
                    }
                }
            }

            val readoutIndex = scrubIndex
            if (readoutIndex != null) {
                val readoutSnapshot = snapshots.getOrNull(readoutIndex)
                if (readoutSnapshot != null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            series.forEach { entry ->
                                val v = entry.valueOf(readoutSnapshot)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(entry.color))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${entry.label}: ${String.format(Locale.US, "%.1f", v)}${entry.unitSuffix}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (readoutSnapshot.state.isThrottling || readoutSnapshot.state.thermalStatus >= 2) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "THROTTLED",
                                        color = Color(0xFFEF4444),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
