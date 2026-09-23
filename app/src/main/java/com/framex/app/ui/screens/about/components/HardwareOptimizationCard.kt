package com.framex.app.ui.screens.about.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.components.WovenNetBackground

@Composable
fun HardwareOptimizationCard(
    isVivoDevice: Boolean,
    isVivoOptActive: Boolean,
    onToggleVivoOpt: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "HARDWARE OPTIMIZATIONS",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray,
            letterSpacing = 0.06.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                WovenNetBackground(modifier = Modifier.matchParentSize())

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2FBF9F).copy(alpha = 0.14f))
                                .border(1.dp, Color(0xFF2FBF9F).copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isVivoDevice) Color(0xFF4FDCB8) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vivo / iQOO Hardware Suite",
                                color = if (isVivoDevice) Color.White else Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (isVivoDevice) {
                                Text(
                                    text = "Applies power, touch, gyro and scheduler overrides during Gaming Mode. Off = only app suspension, RAM purge and DND.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enabling takes effect at the next activation.",
                                    color = Color(0xFF4FDCB8).copy(alpha = 0.8f),
                                    fontSize = 11.5.sp
                                )
                            } else {
                                Text(
                                    text = "Not applicable on this device",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }

                    Switch(
                        checked = isVivoDevice && isVivoOptActive,
                        enabled = isVivoDevice,
                        onCheckedChange = onToggleVivoOpt
                    )
                }
            }
        }
    }
}
