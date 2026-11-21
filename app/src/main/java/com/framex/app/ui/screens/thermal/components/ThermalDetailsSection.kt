package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.metrics.MetricsState

@Composable
fun ThermalDetailsSection(
    metricsState: MetricsState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Sensor Details & Provenance", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(0.08f))
                Spacer(modifier = Modifier.height(12.dp))
                SensorDetailRow("CPU Thermal", if (metricsState.hasThermalCpu) "HAL 2.0 / sysfs zone" else "Unavailable", metricsState.hasThermalCpu)
                SensorDetailRow("GPU Thermal", if (metricsState.hasThermalGpu) "HAL 2.0 / sysfs zone" else "Unavailable", metricsState.hasThermalGpu)
                SensorDetailRow("Skin Thermal", if (metricsState.hasThermalSkin) "HAL 2.0 / sysfs skin zone" else "Unavailable", metricsState.hasThermalSkin)
                SensorDetailRow("Battery Thermal", "Android Battery Manager (Intent)", metricsState.batteryTempC > 0f)
            }
        }
    }
}
