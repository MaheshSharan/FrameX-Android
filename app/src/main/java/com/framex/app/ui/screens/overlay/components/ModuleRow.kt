package com.framex.app.ui.screens.overlay.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.metrics.METRIC_MODULE_REGISTRY
import com.framex.app.metrics.MetricModuleId
import com.framex.app.ui.screens.overlay.ModuleRowState

private fun getModuleCategoryColor(id: MetricModuleId): Color = when (id) {
    MetricModuleId.FPS -> Color(0xFFFF334B)
    MetricModuleId.CPU_FREQUENCY -> Color(0xFFF59E0B)
    MetricModuleId.CPU_CLUSTERS -> Color(0xFF06B6D4)
    MetricModuleId.RAM_USAGE -> Color(0xFF8B5CF6)
    MetricModuleId.BATTERY_TEMPERATURE -> Color(0xFF00E5FF)
    MetricModuleId.THERMAL_MONITOR -> Color(0xFFEF4444)
    MetricModuleId.BATTERY_LEVEL -> Color(0xFF10B981)
    MetricModuleId.CLOCK -> Color(0xFF3B82F6)
    MetricModuleId.SESSION_TIMER -> Color(0xFF6366F1)
    MetricModuleId.NETWORK_SPEED -> Color(0xFF14B8A6)
    MetricModuleId.PING -> Color(0xFFEC4899)
}

/**
 * Interactive row item for a single metric module with category-tinted squircle,
 * title/preview values, capability status badge, explicit [Icon] toggle pill, enable switch, and drag handle.
 */
@Composable
fun ModuleRow(
    module: ModuleRowState,
    accentColor: Color,
    isDragging: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onToggleIcon: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier? = null
) {
    val info = METRIC_MODULE_REGISTRY.getValue(module.id)
    val categoryColor = getModuleCategoryColor(module.id)

    val borderWidth by animateDpAsState(
        targetValue = if (isDragging) 2.dp else 1.dp,
        label = "moduleBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isDragging) accentColor else Color.White.copy(0.06f),
        label = "moduleBorderColor"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "moduleElevation"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .shadow(elevation, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (isDragging) Modifier.background(accentColor.copy(alpha = 0.08f)) else Modifier)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading category squircle (pure visual icon, non-clickable)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(categoryColor.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = categoryColor.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title, capability badge, and preview sample value / explanation
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = info.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!module.capability.isAvailable) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.16f))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = module.capability.shortBadge,
                            color = Color(0xFFFBBF24),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (module.capability.isAvailable) {
                    info.previewSampleValue
                } else {
                    module.capability.userExplanation
                },
                color = if (module.capability.isAvailable) {
                    Color(0xFF9E9E9E)
                } else {
                    Color(0xFFF59E0B).copy(alpha = 0.85f)
                },
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Dedicated Eye Icon Toggle Button (eye when visible, crossed eye when hidden)
        val isIconActive = module.showIcon
        val iconCd = stringResource(
            if (isIconActive) R.string.overlay_icon_enabled_cd else R.string.overlay_icon_disabled_cd
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = onToggleIcon
                )
                .semantics {
                    contentDescription = iconCd
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isIconActive) accentColor.copy(alpha = 0.15f)
                        else Color(0xFF14161E)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isIconActive) accentColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIconActive) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = if (isIconActive) accentColor else Color(0xFF757575),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Module Enable / Disable Switch
        Switch(
            checked = module.enabled,
            onCheckedChange = onEnabledChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedTrackColor = Color(0xFF232630),
                uncheckedThumbColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Drag Handle
        if (dragHandleModifier != null) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = stringResource(R.string.cd_drag_handle),
                tint = Color.Gray,
                modifier = dragHandleModifier
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}
