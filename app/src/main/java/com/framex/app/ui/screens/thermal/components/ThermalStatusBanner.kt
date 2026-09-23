package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.SensorTemperatureAdvisory
import com.framex.app.metrics.ThermalSeverity
import com.framex.app.ui.screens.thermal.DiagnosticCause
import com.framex.app.ui.screens.thermal.computeThermalPressure
import com.framex.app.ui.screens.thermal.getThermalStatusLabel
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing

@Composable
fun ThermalStatusBanner(
    metricsState: MetricsState,
    cpuDelta: Float,
    skinDelta: Float,
    likelyCause: DiagnosticCause,
    isShizukuAvailable: Boolean,
    hasShizukuPermission: Boolean,
    onOpenShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Shizuku CTA Banner if needed
        if (!isShizukuAvailable || !hasShizukuPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = FrameXSpacing.Standard),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                border = BorderStroke(FrameXBorders.ActiveBorderWidth, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                shape = FrameXShapes.Medium
            ) {
                Column(modifier = Modifier.padding(FrameXSpacing.Standard)) {
                    Text(
                        text = if (!isShizukuAvailable) "Shizuku Service Not Running" else "Shizuku Authorization Required",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(FrameXSpacing.XSmall))
                    Text(
                        text = if (!isShizukuAvailable) {
                            "Start Shizuku via wireless debugging or adb to view active CPU/GPU thermal sensors and top processes."
                        } else {
                            "Authorize FrameX in the Shizuku app to read system thermal stats and CPU dumps."
                        },
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(FrameXSpacing.Medium))
                    Row(horizontalArrangement = Arrangement.spacedBy(FrameXSpacing.Small)) {
                        Button(
                            onClick = onOpenShizuku,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(36.dp),
                            shape = FrameXShapes.Pill
                        ) {
                            Text("Open Shizuku", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (isShizukuAvailable && !hasShizukuPermission) {
                            Button(
                                onClick = onRequestShizukuPermission,
                                modifier = Modifier.height(36.dp),
                                shape = FrameXShapes.Pill
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
                .clip(FrameXShapes.Large)
                .background(bannerColor.copy(alpha = 0.12f))
                .border(FrameXBorders.ActiveBorderWidth, bannerColor.copy(alpha = 0.3f), FrameXShapes.Large)
                .padding(FrameXSpacing.Standard),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(FrameXSpacing.Medium)
                    .clip(FrameXShapes.Pill)
                    .background(bannerColor)
            )
            Spacer(modifier = Modifier.width(FrameXSpacing.Medium))
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
            shape = FrameXShapes.Large,
            colors = CardDefaults.cardColors(containerColor = likelyCause.color.copy(alpha = 0.1f)),
            border = BorderStroke(FrameXBorders.ActiveBorderWidth, likelyCause.color.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(FrameXShapes.Pill)
                        .background(likelyCause.color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = likelyCause.color, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(FrameXSpacing.Medium))
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
    }
}
