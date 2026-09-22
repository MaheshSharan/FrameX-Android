package com.framex.app.ui.screens.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.components.WovenNetBackground

@Composable
fun ExecutionCenterSection(
    disableThermalThrottling: Boolean,
    onToggleDisableThermalThrottling: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "EXECUTION CENTER",
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

                Column(modifier = Modifier.padding(20.dp)) {
                    // Header Row (Clickable to Expand / Collapse without ripple hover effect)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isExpanded = !isExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.14f))
                                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Command Execution Center",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    if (isExpanded) "Tap to collapse options" else "Manage individual optimization commands",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(chevronRotation)
                        )
                    }

                    // Collapsible Content
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Text(
                                text = "Configure which system overrides execute during Gaming Mode activation. Safety overrides are enabled by default.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )

                            // Thermal Throttling Command Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF14141E)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (disableThermalThrottling) Color(0xFFEF4444).copy(alpha = 0.4f)
                                    else Color.White.copy(alpha = 0.06f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Thermostat,
                                                contentDescription = null,
                                                tint = if (disableThermalThrottling) Color(0xFFEF4444) else Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Bypass Thermal Throttling",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = if (disableThermalThrottling) "Override Active (Aggressive)" else "Safe Mode (Protected)",
                                                    color = if (disableThermalThrottling) Color(0xFFEF4444) else Color(0xFF10B981),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }

                                        Switch(
                                            checked = disableThermalThrottling,
                                            onCheckedChange = onToggleDisableThermalThrottling,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFFEF4444),
                                                uncheckedThumbColor = Color.Gray,
                                                uncheckedTrackColor = Color(0xFF272730)
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "• Universal: 'cmd thermalservice override-status 0'\n• Vivo/iQOO: Disables 'game_cube_temper_control'",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        lineHeight = 15.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Color(0xFFEF4444).copy(alpha = 0.08f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier.size(14.dp).padding(top = 1.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Bypassing thermal safety prevents CPU/GPU downclocking during heat buildup, which can increase device temperature and long-term hardware wear.",
                                            color = Color(0xFFFCA5A5),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
