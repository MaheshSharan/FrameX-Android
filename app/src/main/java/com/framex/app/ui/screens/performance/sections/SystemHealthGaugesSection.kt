package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.framex.app.ui.screens.performance.components.CircularGauge

@Composable
fun SystemHealthGaugesSection(
    ramPercentage: Float,
    cpuPercentage: Float?
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            "SYSTEM HEALTH",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularGauge(
                percentage = ramPercentage,
                label = "RAM",
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            CircularGauge(
                percentage = cpuPercentage,
                label = "CPU",
                accentColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
