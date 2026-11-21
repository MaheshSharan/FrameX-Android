package com.framex.app.ui.screens.performance.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.gaming.AppInfo
import com.framex.app.ui.components.WovenNetBackground
import com.framex.app.ui.screens.performance.components.AppWhitelistRow
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing

fun LazyListScope.GoogleAppsSection(
    googleApps: List<AppInfo>,
    whitelist: Set<String>,
    onToggleWhitelist: (String) -> Unit,
    deepFreezeEnabled: Boolean,
    onToggleDeepFreeze: (Boolean) -> Unit
) {
    if (googleApps.isEmpty()) return

    item(key = "google_apps_section") {
        GoogleAppsContent(
            googleApps = googleApps,
            whitelist = whitelist,
            onToggleWhitelist = onToggleWhitelist,
            deepFreezeEnabled = deepFreezeEnabled,
            onToggleDeepFreeze = onToggleDeepFreeze
        )
    }
}

@Composable
private fun GoogleAppsContent(
    googleApps: List<AppInfo>,
    whitelist: Set<String>,
    onToggleWhitelist: (String) -> Unit,
    deepFreezeEnabled: Boolean,
    onToggleDeepFreeze: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Pin whitelisted/active apps to the top, then sort alphabetically.
    val sortedApps = remember(googleApps, whitelist) {
        googleApps.sortedWith(
            compareByDescending<AppInfo> { whitelist.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
        )
    }

    val visibleApps = if (isExpanded) sortedApps else sortedApps.take(4)
    val protectedCount = googleApps.count { whitelist.contains(it.packageName) }

    Column(modifier = modifier.padding(horizontal = FrameXSpacing.XLarge)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DEEP FREEZE & GOOGLE APPS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = if (deepFreezeEnabled) "$protectedCount protected" else "Safe Mode (Off)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (deepFreezeEnabled) Color(0xFF38BDF8) else Color(0xFF10B981)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "When enabled, Gaming Mode suspends Google background daemons for maximum RAM.",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        // Master Deep Freeze toggle card.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FrameXShapes.Medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                FrameXBorders.ActiveBorderWidth,
                if (deepFreezeEnabled) Color(0xFF38BDF8).copy(alpha = 0.4f) else FrameXBorders.CardStroke
            )
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

        // Compressed list of Google apps if Deep Freeze is enabled.
        if (deepFreezeEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleApps.forEach { app ->
                    key(app.packageName) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = FrameXShapes.Medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                FrameXBorders.ActiveBorderWidth,
                                if (whitelist.contains(app.packageName)) Color(0xFF38BDF8).copy(alpha = 0.35f) else FrameXBorders.CardStroke
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                WovenNetBackground(modifier = Modifier.matchParentSize())
                                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
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
            }

            // Expand / Collapse action button.
            if (sortedApps.size > 4) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { isExpanded = !isExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Show Less" else "Show All (${sortedApps.size} apps)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
