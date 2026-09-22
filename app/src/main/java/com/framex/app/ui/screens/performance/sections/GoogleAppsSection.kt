package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.gaming.AppInfo
import com.framex.app.ui.components.WovenNetBackground
import com.framex.app.ui.screens.performance.components.AppWhitelistRow

fun LazyListScope.GoogleAppsSection(
    googleApps: List<AppInfo>,
    whitelist: Set<String>,
    onToggleWhitelist: (String) -> Unit,
    deepFreezeEnabled: Boolean,
    onToggleDeepFreeze: (Boolean) -> Unit
) {
    if (googleApps.isEmpty()) return

    item {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DEEP FREEZE & GOOGLE APPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Text(
                    if (deepFreezeEnabled) "${googleApps.count { whitelist.contains(it.packageName) }} protected" else "Safe Mode (Off)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (deepFreezeEnabled) Color(0xFF4285F4) else Color(0xFF10B981)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "When enabled, Gaming Mode suspends Google and OEM system apps for maximum RAM. When disabled, only user-installed apps are frozen.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }
    }

    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, if (deepFreezeEnabled) Color(0xFF4285F4).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                WovenNetBackground(modifier = Modifier.matchParentSize())

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = null,
                                tint = if (deepFreezeEnabled) Color(0xFF38BDF8) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Deep Freeze (Google & OEM)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (deepFreezeEnabled) {
                                "Active: Google and OEM apps will be suspended during Gaming Mode unless protected below."
                            } else {
                                "Safe Mode: Google apps and OEM daemons will not be touched during Gaming Mode."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Switch(
                        checked = deepFreezeEnabled,
                        onCheckedChange = onToggleDeepFreeze,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF38BDF8),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF272730)
                        )
                    )
                }
            }
        }
    }

    if (deepFreezeEnabled) {
        items(
            items = googleApps,
            key = { app -> "google_${app.packageName}" }
        ) { app ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 3.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.2f))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    com.framex.app.ui.components.WovenNetBackground(modifier = Modifier.matchParentSize())
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        AppWhitelistRow(
                            app = app,
                            isWhitelisted = whitelist.contains(app.packageName),
                            onToggle = { onToggleWhitelist(app.packageName) }
                        )
                    }
                }
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(24.dp))
    }
}
