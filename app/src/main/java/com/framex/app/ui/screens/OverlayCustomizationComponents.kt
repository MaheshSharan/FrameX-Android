package com.framex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import com.framex.app.metrics.METRIC_MODULE_REGISTRY
import com.framex.app.ui.components.OverlayPreviewContent

/**
 * Presentational sub-components for [OverlayCustomizationScreen]: the mode toggle, the live
 * overlay preview card, and a single draggable module row. Kept separate from the screen's
 * orchestration logic (state, persistence, view model) so each file stays focused.
 */

@Composable
internal fun ModeSelector(
    modes: List<String>,
    selectedMode: String,
    accentColor: Color,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp)
    ) {
        modes.forEach { mode ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (selectedMode == mode) accentColor else Color.Transparent)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode,
                    color = if (selectedMode == mode) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun OverlayPreviewCard(
    modules: List<ModuleRowState>,
    selectedMode: String,
    opacity: Float,
    accentColor: Color,
    colorIndex: Int,
    fontFamily: FontFamily?,
    textScale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(0.2f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("layout_v2.json", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OverlayPreviewContent(
                    mode = selectedMode,
                    enabledModules = modules.filter { it.enabled }.map { it.id.storageKey }.toSet(),
                    moduleOrder = modules.map { it.id.storageKey },
                    opacity = opacity,
                    overlayScale = textScale,
                    useMonospace = fontFamily == FontFamily.Monospace,
                    colorIndex = colorIndex,
                    enabledModuleIcons = modules.filter { it.showIcon }.map { it.id.storageKey }.toSet()
                )
            }
        }
    }
}

@Composable
internal fun ModuleRow(
    module: ModuleRowState,
    accentColor: Color,
    isDragging: Boolean,
    dragHandleModifier: Modifier?,
    onEnabledChanged: (Boolean) -> Unit,
    onToggleIcon: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info = METRIC_MODULE_REGISTRY.getValue(module.id)

    // "Held" treatment while dragging: a bolder accent border plus a lifted shadow make the
    // card read as physically picked up.
    val borderWidth = if (isDragging) 2.dp else 1.dp
    val borderColor = if (isDragging) accentColor else Color.White.copy(0.05f)
    val elevation = if (isDragging) 10.dp else 0.dp

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
                    color = if (isIconActive) accentColor.copy(alpha = 0.4f) else Color.White.copy(0.05f),
                    shape = CircleShape
                )
                .clickable(
                    role = Role.Switch,
                    onClick = onToggleIcon
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                info.icon,
                contentDescription = if (isIconActive) "Icon enabled (tap to disable)" else "Icon disabled (tap to enable)",
                tint = if (isIconActive) accentColor else Color.Gray.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(info.displayName, color = Color.White, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (module.enabled) accentColor.copy(0.1f) else MaterialTheme.colorScheme.background)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(info.previewSampleValue, color = if (module.enabled) accentColor else Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
        Switch(
            checked = module.enabled,
            onCheckedChange = onEnabledChanged,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
        )
        Spacer(modifier = Modifier.width(16.dp))
        if (dragHandleModifier != null) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = Color.Gray,
                modifier = dragHandleModifier
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

