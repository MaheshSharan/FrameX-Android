package com.framex.app.ui.screens.overlay

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import com.framex.app.metrics.MetricModuleId

/** Exact rendered height every module row must occupy — see ReorderableList contract. */
val MODULE_ROW_HEIGHT = 84.dp

/** Vertical gap below each module row. */
val MODULE_ROW_SPACING = 12.dp

val OVERLAY_MODES = listOf("Minimal", "Compact", "Expanded")

@Immutable
data class ModuleRowState(
    val id: MetricModuleId,
    val enabled: Boolean,
    val showIcon: Boolean = true
)

@Immutable
data class OverlayCustomizationUiState(
    val selectedMode: String = "Compact",
    val modules: List<ModuleRowState> = emptyList(),
    val opacity: Float = 0.75f,
    val overlayScale: Float = 1.0f,
    val useMonospace: Boolean = false,
    val colorIndex: Int = 0,
    val hasChanges: Boolean = false
)

sealed interface OverlayCustomizationUiEvent {
    data class SelectMode(val mode: String) : OverlayCustomizationUiEvent
    data class ReorderModules(val from: Int, val to: Int) : OverlayCustomizationUiEvent
    data class ToggleModuleEnabled(val id: MetricModuleId, val enabled: Boolean) : OverlayCustomizationUiEvent
    data class ToggleModuleIcon(val id: MetricModuleId) : OverlayCustomizationUiEvent
    object ResetToDefaultOrder : OverlayCustomizationUiEvent
    object SaveSettings : OverlayCustomizationUiEvent
}

sealed interface OverlayCustomizationEffect {
    data class ShowToast(val messageRes: Int) : OverlayCustomizationEffect
}
