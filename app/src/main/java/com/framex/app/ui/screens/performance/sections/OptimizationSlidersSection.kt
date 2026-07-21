package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.framex.app.ui.screens.performance.components.SwipeToActivate

@Composable
fun OptimizationSlidersSection(
    isBoostingRam: Boolean,
    showRamResult: Boolean,
    isOptimizingNet: Boolean,
    showPingResult: Boolean,
    onBoostRam: () -> Unit,
    onCheckPing: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            "OPTIMIZATION",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )

        SwipeToActivate(
            text = "SWIPE TO BOOST MEMORY",
            icon = Icons.Default.Bolt,
            accentColor = MaterialTheme.colorScheme.primary,
            isBusy = isBoostingRam,
            showResult = showRamResult,
            onActivated = onBoostRam
        )

        Spacer(modifier = Modifier.height(12.dp))

        SwipeToActivate(
            text = "SWIPE TO CHECK PING",
            icon = Icons.Default.SettingsInputAntenna,
            accentColor = Color(0xFF10B981),
            isBusy = isOptimizingNet,
            showResult = showPingResult,
            busyText = "CHECKING…",
            resultText = "CHECKED",
            onActivated = onCheckPing
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}
