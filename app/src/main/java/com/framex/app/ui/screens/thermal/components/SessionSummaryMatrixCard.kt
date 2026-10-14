package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.metrics.SessionRecordingConfig
import com.framex.app.ui.screens.performance.AppIconCache
import com.framex.app.ui.screens.thermal.SessionKpiSummary
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import java.util.Locale

/**
 * 8-KPI Session Summary Matrix Card matching the reference profiler layout:
 * Row 1: MAX FPS | MIN FPS | AVG FPS | JITTER (%)
 * Row 2: 1% LOW FPS | AVG SOC (°C) | MAX Battery (°C) | AVG Power (W)
 */
@Composable
fun SessionSummaryMatrixCard(
    config: SessionRecordingConfig,
    kpiSummary: SessionKpiSummary,
    sessionTimestamp: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var appIcon by remember(config.targetPackageName) {
        mutableStateOf<ImageBitmap?>(
            config.targetPackageName?.let { AppIconCache.get(it) }
        )
    }

    LaunchedEffect(config.targetPackageName) {
        val pkg = config.targetPackageName
        appIcon = if (!pkg.isNullOrBlank()) {
            AppIconCache.loadIcon(context, pkg)
        } else {
            null
        }
    }

    val titleText = remember(config.targetAppLabel, config.targetAppVersion) {
        if (config.targetAppVersion.isNotBlank()) {
            "${config.targetAppLabel} (${config.targetAppVersion})"
        } else {
            config.targetAppLabel
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = FrameXShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(FrameXBorders.ActiveBorderWidth, FrameXBorders.CardStroke)
    ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val iconBitmap = appIcon
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = config.targetAppLabel,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SportsEsports,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = sessionTimestamp,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(12.dp))

            // Row 1: MAX FPS | MIN FPS | AVG FPS | JITTER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                KpiMetricCell(
                    badge = "MAX",
                    value = String.format(Locale.US, "%.1f", kpiSummary.maxFps),
                    unit = "FPS",
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCell(
                    badge = "MIN",
                    value = String.format(Locale.US, "%.1f", kpiSummary.minFps),
                    unit = "FPS",
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCell(
                    badge = "AVG",
                    value = String.format(Locale.US, "%.1f", kpiSummary.avgFps),
                    unit = "FPS",
                    valueColor = Color(0xFF34D399),
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCell(
                    badge = "JITTER",
                    value = String.format(Locale.US, "%.1f%%", kpiSummary.jitterPercent),
                    unit = "FPS",
                    valueColor = if (kpiSummary.jitterPercent >= 12f) Color(0xFFEF4444) else Color.White,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 2: 1% LOW FPS | AVG SOC (°C) | MAX Battery (°C) | AVG Power (W)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                KpiMetricCell(
                    badge = "1% LOW",
                    value = String.format(Locale.US, "%.1f", kpiSummary.onePercentLowFps),
                    unit = "FPS",
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCell(
                    badge = "AVG",
                    value = String.format(Locale.US, "%.1f", kpiSummary.avgSocTempC),
                    unit = "SOC(°C)",
                    valueColor = if (kpiSummary.avgSocTempC >= 70f) Color(0xFFF59E0B) else Color.White,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCell(
                    badge = "MAX",
                    value = String.format(Locale.US, "%.1f", kpiSummary.maxBatteryTempC),
                    unit = "Battery(°C)",
                    valueColor = if (kpiSummary.maxBatteryTempC >= 42f) Color(0xFFEF4444) else Color.White,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCell(
                    badge = "AVG",
                    value = String.format(Locale.US, "%.2f", kpiSummary.avgPowerWatts),
                    unit = "Power(W)",
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun KpiMetricCell(
    badge: String,
    value: String,
    unit: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = badge,
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = unit,
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
