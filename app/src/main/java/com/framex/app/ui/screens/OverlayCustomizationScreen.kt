package com.framex.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.framex.app.metrics.METRIC_MODULE_REGISTRY
import com.framex.app.metrics.MetricModuleId
import com.framex.app.metrics.resolveMetricModuleOrder
import com.framex.app.repository.SettingsRepository
import com.framex.app.ui.components.ReorderableList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Editable row state for one metric module: identity plus whether it's shown in the overlay. */
private data class ModuleRowState(val id: MetricModuleId, val enabled: Boolean)

@HiltViewModel
class OverlayCustomizationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val savedMode = settingsRepository.overlayMode
    val savedModules = settingsRepository.enabledModules
    val savedModuleOrder = settingsRepository.moduleOrder
    val opacity = settingsRepository.overlayOpacity
    val textSize = settingsRepository.overlayTextSize
    val useMonospace = settingsRepository.overlayUseMonospace
    val colorIndex = settingsRepository.overlayColorIndex

    fun saveSettings(mode: String, orderedModules: List<ModuleRowState>) {
        settingsRepository.setOverlayMode(mode)
        settingsRepository.setEnabledModules(orderedModules.filter { it.enabled }.map { it.id.storageKey }.toSet())
        settingsRepository.setModuleOrder(orderedModules.map { it.id.storageKey })
    }
}

@Composable
fun OverlayCustomizationScreen(
    onNavigateBack: () -> Unit,
    viewModel: OverlayCustomizationViewModel = hiltViewModel()
) {
    val savedMode by viewModel.savedMode.collectAsState()
    val savedModules by viewModel.savedModules.collectAsState()
    val savedModuleOrder by viewModel.savedModuleOrder.collectAsState()
    val opacity by viewModel.opacity.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val useMonospace by viewModel.useMonospace.collectAsState()
    val colorIndex by viewModel.colorIndex.collectAsState()
    val context = LocalContext.current

    var selectedMode by remember(savedMode) { mutableStateOf(savedMode) }
    val modes = listOf("Minimal", "Compact", "Expanded")

    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF60A5FA),
        Color(0xFF34D399),
        Color(0xFF2DD4BF),
        Color(0xFFA78BFA),
        Color(0xFFFBBF24)
    )
    val accentColor = colors[colorIndex]
    val fontFamily = if (useMonospace) FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily
    val textScale = when (textSize) { 0 -> 0.8f; 2 -> 1.2f; else -> 1.0f }

    val savedOrderIds = remember(savedModuleOrder) { resolveMetricModuleOrder(savedModuleOrder) }

    var modules by remember(savedModules, savedModuleOrder) {
        mutableStateOf(
            savedOrderIds.map { id -> ModuleRowState(id = id, enabled = savedModules.contains(id.storageKey)) }
        )
    }

    val hasChanges = selectedMode != savedMode ||
        modules.map { it.id } != savedOrderIds ||
        modules.filter { it.enabled }.map { it.id.storageKey }.toSet() != savedModules

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = Color.White)
                }
                Text("Overlay Config", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            ModeSelector(
                modes = modes,
                selectedMode = selectedMode,
                accentColor = accentColor,
                onModeSelected = { selectedMode = it },
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            OverlayPreviewCard(
                modules = modules,
                selectedMode = selectedMode,
                opacity = opacity,
                accentColor = accentColor,
                fontFamily = fontFamily,
                textScale = textScale,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Active Modules", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "Drag to reorder. Toggle to show in overlay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            ReorderableList(
                items = modules,
                key = { it.id.storageKey },
                onMove = { from, to ->
                    modules = modules.toMutableList().apply { add(to, removeAt(from)) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) { module, dragHandleModifier, dragOffsetPx ->
                ModuleRow(
                    module = module,
                    accentColor = accentColor,
                    dragHandleModifier = dragHandleModifier,
                    onEnabledChanged = { isChecked ->
                        modules = modules.map { if (it.id == module.id) it.copy(enabled = isChecked) else it }
                    },
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .graphicsLayer { translationY = dragOffsetPx }
                        .zIndex(if (dragOffsetPx != 0f) 1f else 0f)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(0.95f))
                .padding(24.dp)
        ) {
            Button(
                onClick = {
                    if (hasChanges) {
                        viewModel.saveSettings(selectedMode, modules)
                        Toast.makeText(context, "Overlay configuration saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = hasChanges,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White,
                    disabledContainerColor = Color.DarkGray.copy(alpha = 0.5f),
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (hasChanges) "Apply Changes" else "Applied", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ModeSelector(
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
private fun OverlayPreviewCard(
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
private fun ModuleRow(
    module: ModuleRowState,
    accentColor: Color,
    dragHandleModifier: Modifier,
    onEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val info = METRIC_MODULE_REGISTRY.getValue(module.id)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
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
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = "Drag to reorder",
            tint = Color.Gray,
            modifier = dragHandleModifier
        )
    }
}
