package com.framex.app.ui.screens.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.screens.dashboard.FpsStatsSummary

@Composable
fun LiveFpsSparklineCard(
    fpsHistory: List<Int>,
    stats: FpsStatsSummary,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = Color.White.copy(alpha = 0.05f)

    val animatedFps by animateIntAsState(
        targetValue = stats.currentFps,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "animatedFps"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "sparklineDotPulse")
    val dotGlowRadius by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotGlowRadius"
    )

    val sparklineDescription = stringResource(
        R.string.cd_sparkline_chart
    ) + ": ${stats.currentFps} FPS, Average: ${stats.avgFps}, 1% Low: ${stats.onePercentLow}, Frametime: ${stats.frametimeMs}ms"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0C0D), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = sparklineDescription
            }
    ) {
        // Header: Title and Live Counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dashboard_frame_rate),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.LightGray,
                letterSpacing = 0.5.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$animatedFps",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_fps_unit),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // High-performance Canvas using drawWithCache to avoid allocations during 60/120Hz frames
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val strokePx = 2.dp.toPx()
                    val gridStrokePx = 1.dp.toPx()
                    val dotRadiusPx = 4.dp.toPx()

                    val path = Path()
                    val fillPath = Path()
                    var lastX = 0f
                    var lastY = h

                    if (fpsHistory.size >= 2) {
                        val maxFps = fpsHistory.max().coerceAtLeast(1)
                        fpsHistory.forEachIndexed { i, fps ->
                            val x = w * i / (fpsHistory.size - 1).toFloat()
                            val y = h * (1f - fps.toFloat() / maxFps)
                            if (i == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                            if (i == fpsHistory.size - 1) {
                                lastX = x
                                lastY = y
                            }
                        }
                        fillPath.addPath(path)
                        fillPath.lineTo(w, h)
                        fillPath.lineTo(0f, h)
                        fillPath.close()
                    }

                    val gradientBrush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f))
                    )

                    onDrawBehind {
                        // Background grid lines
                        listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                            val y = h * frac
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = gridStrokePx
                            )
                        }

                        if (fpsHistory.size >= 2) {
                            drawPath(fillPath, brush = gradientBrush)
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(
                                    width = strokePx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                            // Animated pulsing end dot
                            drawCircle(
                                color = lineColor.copy(alpha = 0.35f),
                                radius = dotGlowRadius.dp.toPx(),
                                center = Offset(lastX, lastY)
                            )
                            drawCircle(
                                color = Color(0xFF0B0B0C),
                                radius = dotRadiusPx,
                                center = Offset(lastX, lastY)
                            )
                            drawCircle(
                                color = lineColor,
                                radius = dotRadiusPx,
                                center = Offset(lastX, lastY),
                                style = Stroke(width = strokePx)
                            )
                        } else {
                            drawLine(
                                color = lineColor.copy(alpha = 0.3f),
                                start = Offset(0f, h),
                                end = Offset(w, h),
                                strokeWidth = strokePx
                            )
                        }
                    }
                }
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(modifier = Modifier.height(10.dp))

        // Floor stats row: Avg, 1% Low, Frametime (Accessible merged row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatsColumn(
                label = stringResource(R.string.dashboard_avg_fps),
                value = "${stats.avgFps}"
            )
            StatsColumn(
                label = stringResource(R.string.dashboard_one_percent_low),
                value = "${stats.onePercentLow}"
            )
            StatsColumn(
                label = stringResource(R.string.dashboard_frametime),
                value = "${stats.frametimeMs}ms"
            )
        }
    }
}

@Composable
private fun StatsColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}
