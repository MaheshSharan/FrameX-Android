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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.components.WovenNetBackground
import com.framex.app.ui.theme.FrameXAccessibility
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing

@Composable
fun AppUpdatesCard(
    autoUpdateEnabled: Boolean,
    isCheckingUpdate: Boolean,
    statusMessage: String?,
    versionName: String,
    onAutoUpdateToggled: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "APPLICATION UPDATES",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray,
            letterSpacing = 0.06.sp,
            modifier = Modifier.padding(start = FrameXSpacing.XSmall, bottom = FrameXSpacing.Medium)
        )

        Card(
            shape = FrameXShapes.Card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(FrameXBorders.ActiveBorderWidth, FrameXBorders.CardStroke),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                WovenNetBackground(modifier = Modifier.matchParentSize())

                Column(modifier = Modifier.padding(FrameXSpacing.Large)) {
                    // Auto-update toggle row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = autoUpdateEnabled,
                                role = Role.Switch,
                                onValueChange = onAutoUpdateToggled
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = FrameXSpacing.Standard)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(FrameXShapes.Medium)
                                    .background(accentColor.copy(alpha = 0.14f))
                                    .border(FrameXBorders.ActiveBorderWidth, accentColor.copy(alpha = 0.28f), FrameXShapes.Medium),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-check for updates",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Check GitHub releases on app startup.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = null
                        )
                    }

                    HorizontalDivider(
                        color = FrameXBorders.SubtleStroke,
                        modifier = Modifier.padding(vertical = FrameXSpacing.Standard)
                    )

                    // Version status and Check button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusMessage ?: "Current Version: v$versionName",
                            fontSize = 12.5.sp,
                            color = if (statusMessage != null) accentColor else Color.Gray,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = onCheckForUpdates,
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor.copy(alpha = 0.15f),
                                contentColor = accentColor
                            ),
                            shape = FrameXShapes.Medium,
                            modifier = Modifier.height(FrameXAccessibility.MinTouchTarget)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    color = accentColor,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Check for Updates",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
