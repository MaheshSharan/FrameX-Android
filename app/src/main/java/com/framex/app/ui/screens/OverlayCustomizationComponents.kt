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
import com.framex.app.metrics.METRIC_MODULE_REGISTRY

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

            // Preview order always mirrors the live `modules` list state above, so what's
            // shown here matches exactly what the overlay will render once applied.
            val enabledModules = modules.filter { it.enabled }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(opacity))
                        .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                        .padding(if (selectedMode == "Minimal") (4 * textScale).dp else (8 * textScale).dp)
                ) {
                    if (selectedMode == "Expanded") {
                        Column(verticalArrangement = Arrangement.spacedBy((8 * textScale).dp)) {
                            enabledModules.forEach { module ->
                                val info = METRIC_MODULE_REGISTRY.getValue(module.id)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(info.icon, contentDescription = null, tint = accentColor, modifier = Modifier.size((16 * textScale).dp))
                                    Spacer(modifier = Modifier.width((8 * textScale).dp))
                                    Text(info.displayName, color = Color.Gray, fontSize = (10 * textScale).sp, fontFamily = fontFamily, modifier = Modifier.weight(1f))
                                    Text(
                                        info.previewSampleValue,
                                        color = Color.White,
                                        fontFamily = fontFamily,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = (12 * textScale).sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((12 * textScale).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = (4 * textScale).dp)
                        ) {
                            enabledModules.forEachIndexed { index, module ->
                                val info = METRIC_MODULE_REGISTRY.getValue(module.id)
                                if (selectedMode == "Minimal") {
                                    Text(
                                        info.previewSampleValue,
                                        color = Color.White,
                                        fontFamily = fontFamily,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = (14 * textScale).sp, fontWeight = FontWeight.Bold)
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(module.id.storageKey.uppercase(), color = Color.Gray, fontFamily = fontFamily, fontSize = (10 * textScale).sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            info.previewSampleValue,
                                            color = if (index == 0) accentColor else Color.White,
                                            fontFamily = fontFamily,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = (16 * textScale).sp, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                                if (index < enabledModules.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(if (selectedMode == "Minimal") (12 * textScale).dp else (24 * textScale).dp)
                                            .background(Color.DarkGray)
                                    )
                                }
                            }
                            if (enabledModules.isEmpty()) {
                                Text("No modules selected", color = Color.Gray, fontFamily = fontFamily, fontSize = (12 * textScale).sp)
                            }
                        }
                    }
                }
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
    modifier: Modifier = Modifier
) {
    val info = METRIC_MODULE_REGISTRY.getValue(module.id)

    // "Held" treatment while dragging: a bolder accent border plus a lifted shadow make the
    // card read as physically picked up. Only the container changes — text and icon tint
    // logic below is untouched, so contrast/accessibility is unaffected by this state.
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
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Icon(info.icon, contentDescription = null, tint = if (module.enabled) accentColor else Color.Gray)
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

