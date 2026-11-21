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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.theme.FrameXAccessibility
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing

private val CardBackground = Color(0xFF0F1015)

/**
 * Application updates card with auto-update toggle, version indicator,
 * and manual update verification button.
 */
@Composable
fun AppUpdatesCard(
    autoUpdateEnabled: Boolean,
    isCheckingUpdate: Boolean,
    statusMessage: String?,
    versionName: String,
    versionCode: Long = 0L,
    onAutoUpdateToggled: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header: Dashboard style with 16dp pure white icon, 8dp spacer, titleSmall bold white, 0.5sp letter spacing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "APPLICATION UPDATES",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_download),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-check for updates",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Check for new releases from GitHub automatically on app startup.",
                                color = Color(0xFFA0A0A0),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF20222A),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.06f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                VersionCheckRow(
                    isCheckingUpdate = isCheckingUpdate,
                    statusMessage = statusMessage,
                    versionName = versionName,
                    versionCode = versionCode,
                    onCheckForUpdates = onCheckForUpdates
                )
            }
        }
    }
}

/** Version label with a full-width check button below it, so nothing drifts to one side. */
@Composable
private fun VersionCheckRow(
    isCheckingUpdate: Boolean,
    statusMessage: String?,
    versionName: String,
    versionCode: Long,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FrameXSpacing.Medium)) {
        Column {
            Text(
                text = stringResource(R.string.about_current_version),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            val versionDisplay = if (versionCode > 0) "v$versionName ($versionCode)" else "v$versionName"
            Text(
                text = versionDisplay,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Button(
            onClick = onCheckForUpdates,
            enabled = !isCheckingUpdate,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            shape = FrameXShapes.Large,
            modifier = Modifier.fillMaxWidth().heightIn(min = FrameXAccessibility.MinTouchTarget)
        ) {
            if (isCheckingUpdate) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(FrameXSpacing.Small))
            Text(
                text = stringResource(
                    if (isCheckingUpdate) R.string.about_checking_updates else R.string.about_check_updates
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
