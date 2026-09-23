package com.framex.app.ui.screens.overlay.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
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
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.metrics.METRIC_MODULE_REGISTRY
import com.framex.app.ui.screens.overlay.ModuleRowState

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
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isIconActive = module.showIcon
        val iconCd = stringResource(
            if (isIconActive) R.string.overlay_icon_enabled_cd else R.string.overlay_icon_disabled_cd
        )

        // Accessible Icon toggle button with proper Toggleable semantics
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isIconActive) accentColor.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.background
                )
                .border(
                    width = 1.dp,
                    color = if (isIconActive) accentColor.copy(alpha = 0.4f) else Color.White.copy(0.06f),
                    shape = CircleShape
                )
                .semantics {
                    contentDescription = iconCd
                }
                .toggleable(
                    value = isIconActive,
                    role = Role.Switch,
                    onValueChange = { onToggleIcon() }
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = if (isIconActive) accentColor else Color.Gray.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.displayName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (module.enabled) accentColor.copy(0.12f) else MaterialTheme.colorScheme.background)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = info.previewSampleValue,
                    color = if (module.enabled) accentColor else Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Switch(
            checked = module.enabled,
            onCheckedChange = onEnabledChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )

        Spacer(modifier = Modifier.width(16.dp))

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
