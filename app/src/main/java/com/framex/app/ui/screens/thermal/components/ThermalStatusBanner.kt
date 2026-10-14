package com.framex.app.ui.screens.thermal.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing
import com.framex.app.ui.theme.PrimaryRed

/**
 * Conditional Shizuku authorization prompt banner.
 * Only renders when Shizuku is disconnected or lacks permission.
 */
@Composable
fun ThermalStatusBanner(
    isShizukuAvailable: Boolean,
    hasShizukuPermission: Boolean,
    onOpenShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isShizukuAvailable && hasShizukuPermission) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = FrameXSpacing.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
        border = BorderStroke(FrameXBorders.ActiveBorderWidth, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
        shape = FrameXShapes.Medium
    ) {
        Column(modifier = Modifier.padding(FrameXSpacing.Standard)) {
            Text(
                text = if (!isShizukuAvailable) "Shizuku Service Not Running" else "Shizuku Authorization Required",
                color = PrimaryRed,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(FrameXSpacing.XSmall))
            Text(
                text = if (!isShizukuAvailable) {
                    "Start Shizuku via wireless debugging or adb to read privileged CPU/GPU thermal zones and FPS."
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
                        containerColor = PrimaryRed,
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
