package com.framex.app.ui.screens.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.R
import com.framex.app.metrics.DEFAULT_METRIC_MODULE_ORDER
import com.framex.app.metrics.MetricModuleId
import com.framex.app.metrics.isIconShown
import com.framex.app.metrics.resolveMetricModuleOrder
import com.framex.app.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OverlayCustomizationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverlayCustomizationUiState())
    val uiState: StateFlow<OverlayCustomizationUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<OverlayCustomizationEffect>()
    val effect: SharedFlow<OverlayCustomizationEffect> = _effect.asSharedFlow()

    // Baseline snapshots to compare for hasChanges
    private var savedMode = "Compact"
    private var savedModuleOrder = emptyList<MetricModuleId>()
    private var savedEnabledSet = emptySet<String>()
    private var savedIconSet = emptySet<String>()

    private data class ModuleSettingsSnapshot(
        val mode: String,
        val fullOrder: List<MetricModuleId>,
        val enabledModules: Set<String>,
        val enabledIcons: Set<String>
    )

    private data class StyleSettingsSnapshot(
        val opacity: Float,
        val scale: Float,
        val monospace: Boolean,
        val colorIndex: Int
    )

    init {
        val moduleSettingsFlow = combine(
            settingsRepository.overlayMode,
            settingsRepository.enabledModules,
            settingsRepository.enabledModuleIcons,
            settingsRepository.moduleOrder
        ) { mode, enabledModules, enabledIcons, orderList ->
            val resolvedOrder = resolveMetricModuleOrder(orderList)
            val withoutFps = resolvedOrder.filter { it != MetricModuleId.FPS }
            val fullOrder = listOf(MetricModuleId.FPS) + withoutFps
            ModuleSettingsSnapshot(mode, fullOrder, enabledModules, enabledIcons)
        }

        val styleSettingsFlow = combine(
            settingsRepository.overlayOpacity,
            settingsRepository.overlayScale,
            settingsRepository.overlayUseMonospace,
            settingsRepository.overlayColorIndex
        ) { opacity, scale, monospace, colorIndex ->
            StyleSettingsSnapshot(opacity, scale, monospace, colorIndex)
        }

        combine(moduleSettingsFlow, styleSettingsFlow) { modSnap, styleSnap ->
            savedMode = modSnap.mode
            savedModuleOrder = modSnap.fullOrder
            savedEnabledSet = modSnap.enabledModules
            savedIconSet = modSnap.enabledIcons

            val initialModules = modSnap.fullOrder.map { id ->
                ModuleRowState(
                    id = id,
                    enabled = modSnap.enabledModules.contains(id.storageKey),
                    showIcon = isIconShown(modSnap.enabledIcons, id.storageKey)
                )
            }

            _uiState.update { current ->
                if (current.modules.isEmpty()) {
                    current.copy(
                        selectedMode = modSnap.mode,
                        modules = initialModules,
                        opacity = styleSnap.opacity,
                        overlayScale = styleSnap.scale,
                        useMonospace = styleSnap.monospace,
                        colorIndex = styleSnap.colorIndex,
                        hasChanges = false
                    )
                } else {
                    current.copy(
                        opacity = styleSnap.opacity,
                        overlayScale = styleSnap.scale,
                        useMonospace = styleSnap.monospace,
                        colorIndex = styleSnap.colorIndex
                    )
                }
            }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: OverlayCustomizationUiEvent) {
        when (event) {
            is OverlayCustomizationUiEvent.SelectMode -> {
                _uiState.update { current ->
                    val updated = current.copy(selectedMode = event.mode)
                    updated.copy(hasChanges = computeHasChanges(updated))
                }
            }
            is OverlayCustomizationUiEvent.ReorderModules -> {
                _uiState.update { current ->
                    val list = current.modules.toMutableList()
                    if (event.from in list.indices && event.to in list.indices) {
                        val item = list.removeAt(event.from)
                        list.add(event.to, item)
                    }
                    val updated = current.copy(modules = list)
                    updated.copy(hasChanges = computeHasChanges(updated))
                }
            }
            is OverlayCustomizationUiEvent.ToggleModuleEnabled -> {
                _uiState.update { current ->
                    val updatedModules = current.modules.map {
                        if (it.id == event.id) it.copy(enabled = event.enabled) else it
                    }
                    val updated = current.copy(modules = updatedModules)
                    updated.copy(hasChanges = computeHasChanges(updated))
                }
            }
            is OverlayCustomizationUiEvent.ToggleModuleIcon -> {
                _uiState.update { current ->
                    val updatedModules = current.modules.map {
                        if (it.id == event.id) it.copy(showIcon = !it.showIcon) else it
                    }
                    val updated = current.copy(modules = updatedModules)
                    updated.copy(hasChanges = computeHasChanges(updated))
                }
            }
            OverlayCustomizationUiEvent.ResetToDefaultOrder -> {
                _uiState.update { current ->
                    val enabledById = current.modules.associate { it.id to it.enabled }
                    val showIconById = current.modules.associate { it.id to it.showIcon }
                    val resetModules = DEFAULT_METRIC_MODULE_ORDER.map { id ->
                        ModuleRowState(
                            id = id,
                            enabled = enabledById[id] ?: false,
                            showIcon = showIconById[id] ?: true
                        )
                    }
                    val updated = current.copy(modules = resetModules)
                    updated.copy(hasChanges = computeHasChanges(updated))
                }
                viewModelScope.launch {
                    _effect.emit(OverlayCustomizationEffect.ShowToast(R.string.overlay_order_reset_toast))
                }
            }
            OverlayCustomizationUiEvent.SaveSettings -> {
                val state = _uiState.value
                if (state.hasChanges) {
                    settingsRepository.setOverlayMode(state.selectedMode)
                    settingsRepository.setEnabledModules(
                        state.modules.filter { it.enabled }.map { it.id.storageKey }.toSet()
                    )
                    settingsRepository.setEnabledModuleIcons(
                        state.modules.filter { it.showIcon }.map { it.id.storageKey }.toSet()
                    )
                    settingsRepository.setModuleOrder(state.modules.map { it.id.storageKey })

                    savedMode = state.selectedMode
                    savedModuleOrder = state.modules.map { it.id }
                    savedEnabledSet = state.modules.filter { it.enabled }.map { it.id.storageKey }.toSet()
                    savedIconSet = state.modules.filter { it.showIcon }.map { it.id.storageKey }.toSet()

                    _uiState.update { it.copy(hasChanges = false) }
                    viewModelScope.launch {
                        _effect.emit(OverlayCustomizationEffect.ShowToast(R.string.overlay_saved_toast))
                    }
                }
            }
        }
    }

    private fun computeHasChanges(state: OverlayCustomizationUiState): Boolean {
        val currentEnabled = state.modules.filter { it.enabled }.map { it.id.storageKey }.toSet()
        val currentIcons = state.modules.filter { it.showIcon }.map { it.id.storageKey }.toSet()
        val currentOrder = state.modules.map { it.id }

        return state.selectedMode != savedMode ||
            currentOrder != savedModuleOrder ||
            currentEnabled != savedEnabledSet ||
            currentIcons != savedIconSet
    }
}
