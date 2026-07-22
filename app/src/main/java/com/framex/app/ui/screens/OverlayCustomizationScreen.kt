package com.framex.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.framex.app.metrics.DEFAULT_METRIC_MODULE_ORDER
import com.framex.app.metrics.MetricModuleId
import com.framex.app.metrics.resolveMetricModuleOrder
import com.framex.app.repository.SettingsRepository
import com.framex.app.ui.components.ReorderableList
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Editable row state for one metric module: identity plus whether it's shown in the overlay. */
internal data class ModuleRowState(val id: MetricModuleId, val enabled: Boolean)

/** Exact rendered height every module row must occupy — see ReorderableList's itemHeight contract. */
private val MODULE_ROW_HEIGHT = 84.dp

/** Vertical gap below each module row. */
private val MODULE_ROW_SPACING = 12.dp

@HiltViewModel
class OverlayCustomizationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val savedMode = settingsRepository.overlayMode
    val savedModules = settingsRepository.enabledModules
    val savedModuleOrder = settingsRepository.moduleOrder
    val opacity = settingsRepository.overlayOpacity
    val textSize = settingsRepository.overlayTextSize
    val overlayScale = settingsRepository.overlayScale
    val useMonospace = settingsRepository.overlayUseMonospace
    val colorIndex = settingsRepository.overlayColorIndex

    internal fun saveSettings(mode: String, orderedModules: List<ModuleRowState>) {
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
    val overlayScale by viewModel.overlayScale.collectAsState()
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
    val textScale = overlayScale

    val savedOrderIds = remember(savedModuleOrder) {
        val resolved = resolveMetricModuleOrder(savedModuleOrder)
        val withoutFps = resolved.filter { it != MetricModuleId.FPS }
        listOf(MetricModuleId.FPS) + withoutFps
    }

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

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Active Modules", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        "Drag to reorder. Toggle to show in overlay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                IconButton(
                    onClick = {
                        val enabledById = modules.associate { it.id to it.enabled }
                        modules = DEFAULT_METRIC_MODULE_ORDER.map { id ->
                            ModuleRowState(id = id, enabled = enabledById[id] ?: false)
                        }
                        Toast.makeText(context, "Order reset to default", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset to default order", tint = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            ReorderableList(
                items = modules,
                key = { it.id.storageKey },
                itemHeight = MODULE_ROW_HEIGHT,
                itemSpacing = MODULE_ROW_SPACING,
                minReorderIndex = 1,
                onMove = { from, to ->
                    modules = modules.toMutableList().apply { add(to, removeAt(from)) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) { module, dragHandleModifier, isDragging ->
                ModuleRow(
                    module = module,
                    accentColor = accentColor,
                    isDragging = isDragging,
                    dragHandleModifier = dragHandleModifier,
                    onEnabledChanged = { isChecked ->
                        modules = modules.map { if (it.id == module.id) it.copy(enabled = isChecked) else it }
                    }
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

