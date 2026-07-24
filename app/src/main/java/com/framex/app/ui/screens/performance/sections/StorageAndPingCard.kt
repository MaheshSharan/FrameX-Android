package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StorageAndPingCard(
    storageInfo: Triple<Long, Long, Long>,
    currentPing: Int,
    isOptimizingNet: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            com.framex.app.ui.components.WovenNetBackground(modifier = Modifier.matchParentSize())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("STORAGE", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${storageInfo.first} GB", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("/ ${storageInfo.second} GB", color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("${storageInfo.third} GB FREE", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (storageInfo.second > 0) storageInfo.first.toFloat() / storageInfo.second else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(0.05f)
                )
            }

            Box(modifier = Modifier.width(1.dp).height(80.dp).background(Color.White.copy(0.08f)).padding(horizontal = 12.dp))

            Column(modifier = Modifier.weight(0.8f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PING", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentPing > 0) "$currentPing ms" else "-- ms",
                    color = if (isOptimizingNet) Color.Gray else Color(0xFF10B981),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val pingQualitative = when {
                    currentPing == 0 -> "OFFLINE"
                    currentPing < 30 -> "EXCELLENT"
                    currentPing < 75 -> "GOOD"
                    else -> "POOR"
                }
                Text(pingQualitative, color = if (currentPing < 75 && currentPing > 0) Color(0xFF10B981) else Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val bars = when {
                        currentPing == 0 -> 0
                        currentPing < 30 -> 4
                        currentPing < 60 -> 3
                        currentPing < 100 -> 2
                        else -> 1
                    }
                    for (i in 1..4) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((4 * i).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i <= bars) Color(0xFF10B981) else Color.White.copy(0.08f))
                        )
                    }
                }
            }
            }
        }
    }
}
