package com.framex.app.ui.screens.performance.sections

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.permissions.AppPermission
import com.framex.app.ui.screens.performance.components.RequirementRow
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing

/** One line of the read-only requirements card. */
private class Requirement(
    @StringRes val titleRes: Int,
    @StringRes val okStateRes: Int,
    @StringRes val missingStateRes: Int,
    @StringRes val okDescriptionRes: Int,
    @StringRes val missingDescriptionRes: Int,
    val satisfied: Boolean
)

private fun requirements(privilegeReady: Boolean, permissions: Map<AppPermission, Boolean>) = listOf(
    Requirement(
        R.string.perf_req_privilege, R.string.perf_state_connected, R.string.perf_state_offline,
        R.string.perf_req_privilege_ok, R.string.perf_req_privilege_missing, privilegeReady
    ),
    Requirement(
        R.string.perf_req_write_settings, R.string.perf_state_authorized, R.string.perf_state_denied,
        R.string.perf_req_write_settings_ok, R.string.perf_req_write_settings_missing,
        permissions[AppPermission.WRITE_SETTINGS] == true
    ),
    Requirement(
        R.string.perf_req_dnd, R.string.perf_state_available, R.string.perf_state_missing,
        R.string.perf_req_dnd_ok, R.string.perf_req_dnd_missing,
        permissions[AppPermission.DND] == true
    ),
    Requirement(
        R.string.perf_req_listener, R.string.perf_state_active, R.string.perf_state_disabled,
        R.string.perf_req_listener_ok, R.string.perf_req_listener_missing,
        permissions[AppPermission.NOTIF_LISTENER] == true
    )
)

/**
 * Read-only System Status card. Granting happens on the System Status screen only, so this
 * card never opens a system dialog; it just links there.
 */
@Composable
fun RequirementsSection(
    privilegeReady: Boolean,
    permissions: Map<AppPermission, Boolean>,
    onOpenSystemStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }
    val items = remember(privilegeReady, permissions) { requirements(privilegeReady, permissions) }
    val activeCount = items.count { it.satisfied }
    val allReady = activeCount == items.size
    val accentGreen = Color(0xFF10B981)
    val warningAmber = Color(0xFFF59E0B)

    Column(modifier = modifier.padding(horizontal = FrameXSpacing.XLarge)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.perm_screen_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button) { showDetails = !showDetails }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (showDetails) R.string.perf_req_hide else R.string.perf_req_details),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FrameXShapes.Card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(FrameXBorders.ActiveBorderWidth, FrameXBorders.CardStroke)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(FrameXSpacing.Large)) {
                RequirementsSummary(allReady, activeCount, items.size, accentGreen, warningAmber, onOpenSystemStatus)
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    items.forEach { item ->
                        CompactStatusColumnItem(
                            title = stringResource(item.titleRes),
                            subtitle = stringResource(if (item.satisfied) item.okStateRes else item.missingStateRes),
                            isSatisfied = item.satisfied,
                            accentGreen = accentGreen,
                            warningAmber = warningAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                AnimatedVisibility(visible = showDetails) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        items.forEach { item ->
                            RequirementRow(
                                label = stringResource(item.titleRes),
                                description = stringResource(
                                    if (item.satisfied) item.okDescriptionRes else item.missingDescriptionRes
                                ),
                                satisfied = item.satisfied
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun RequirementsSummary(
    allReady: Boolean,
    activeCount: Int,
    total: Int,
    accentGreen: Color,
    warningAmber: Color,
    onOpenSystemStatus: () -> Unit
) {
    val tint = if (allReady) accentGreen else warningAmber
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (allReady) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(if (allReady) R.string.perf_req_ready else R.string.perf_req_incomplete),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (allReady) {
                    stringResource(R.string.perf_req_all_active, total)
                } else {
                    stringResource(R.string.perf_req_n_active, activeCount, total)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        // Granting lives on System Status, so the card links there instead of prompting itself.
        IconButton(onClick = onOpenSystemStatus) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.perf_open_system_status),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CompactStatusColumnItem(
    title: String,
    subtitle: String,
    isSatisfied: Boolean,
    accentGreen: Color,
    warningAmber: Color,
    modifier: Modifier = Modifier
) {
    val iconColor = if (isSatisfied) accentGreen else warningAmber

    Column(
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f))
                .border(1.dp, iconColor.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSatisfied) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(13.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = iconColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
