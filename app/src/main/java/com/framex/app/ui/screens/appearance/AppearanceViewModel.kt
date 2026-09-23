package com.framex.app.ui.screens.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.R
import com.framex.app.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SavedAppearance(
    val opacity: Float,
    val textSize: Int,
    val scale: Float,
    val useMonospace: Boolean,
    val colorIndex: Int
)

private data class SavedContainer(
    val bgColorIndex: Int,
    val borderColorIndex: Int,
    val textColorIndex: Int,
    val cpuHotWarning: Boolean
)

private data class SavedModules(
    val mode: String,
    val enabledModules: Set<String>,
    val enabledModuleIcons: Set<String>,
    val moduleOrder: List<String>
)

private data class DraftAppearance(
    val opacity: Float,
    val textSize: Int,
    val scale: Float,
    val useMonospace: Boolean,
    val colorIndex: Int,
    val bgColorIndex: Int,
    val borderColorIndex: Int,
    val textColorIndex: Int,
    val cpuHotWarning: Boolean
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _draftState = MutableStateFlow<DraftAppearance?>(null)

    private val _effectChannel = Channel<AppearanceUiEffect>(Channel.BUFFERED)
    val effect = _effectChannel.receiveAsFlow()

    private val appearanceStream = combine(
        settingsRepository.overlayOpacity,
        settingsRepository.overlayTextSize,
        settingsRepository.overlayScale,
        settingsRepository.overlayUseMonospace,
        settingsRepository.overlayColorIndex
    ) { opacity, textSize, scale, useMonospace, colorIndex ->
        SavedAppearance(opacity, textSize, scale, useMonospace, colorIndex)
    }

    private val containerStream = combine(
        settingsRepository.overlayBgColorIndex,
        settingsRepository.overlayBorderColorIndex,
        settingsRepository.overlayTextColorIndex,
        settingsRepository.cpuHotWarningEnabled
    ) { bgColorIndex, borderColorIndex, textColorIndex, cpuHotWarning ->
        SavedContainer(bgColorIndex, borderColorIndex, textColorIndex, cpuHotWarning)
    }

    private val modulesStream = combine(
        settingsRepository.overlayMode,
        settingsRepository.enabledModules,
        settingsRepository.enabledModuleIcons,
        settingsRepository.moduleOrder
    ) { mode, enabledModules, enabledModuleIcons, moduleOrder ->
        SavedModules(mode, enabledModules, enabledModuleIcons, moduleOrder)
    }

    val uiState: StateFlow<AppearanceUiState> = combine(
        appearanceStream,
        containerStream,
        modulesStream,
        _draftState
    ) { app, cont, mods, draft ->
        AppearanceUiState(
            savedOpacity = app.opacity,
            savedTextSize = app.textSize,
            savedScale = app.scale,
            savedUseMonospace = app.useMonospace,
            savedColorIndex = app.colorIndex,
            savedBgColorIndex = cont.bgColorIndex,
            savedBorderColorIndex = cont.borderColorIndex,
            savedTextColorIndex = cont.textColorIndex,
            savedCpuHotWarning = cont.cpuHotWarning,

            opacity = draft?.opacity ?: app.opacity,
            textSize = draft?.textSize ?: app.textSize,
            scale = draft?.scale ?: app.scale,
            useMonospace = draft?.useMonospace ?: app.useMonospace,
            colorIndex = draft?.colorIndex ?: app.colorIndex,
            bgColorIndex = draft?.bgColorIndex ?: cont.bgColorIndex,
            borderColorIndex = draft?.borderColorIndex ?: cont.borderColorIndex,
            textColorIndex = draft?.textColorIndex ?: cont.textColorIndex,
            cpuHotWarning = draft?.cpuHotWarning ?: cont.cpuHotWarning,

            mode = mods.mode,
            enabledModules = mods.enabledModules,
            enabledModuleIcons = mods.enabledModuleIcons,
            moduleOrder = mods.moduleOrder
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppearanceUiState()
    )

    fun onEvent(event: AppearanceUiEvent) {
        when (event) {
            is AppearanceUiEvent.SetOpacity -> updateDraft { it.copy(opacity = event.opacity) }
            is AppearanceUiEvent.SetScale -> {
                val matchedSize = when {
                    kotlin.math.abs(event.scale - 0.8f) < 0.05f -> 0
                    kotlin.math.abs(event.scale - 1.0f) < 0.05f -> 1
                    kotlin.math.abs(event.scale - 1.2f) < 0.05f -> 2
                    else -> -1
                }
                updateDraft { it.copy(scale = event.scale, textSize = if (matchedSize != -1) matchedSize else it.textSize) }
            }
            is AppearanceUiEvent.SetTextSize -> {
                val targetScale = when (event.index) {
                    0 -> 0.8f
                    2 -> 1.2f
                    else -> 1.0f
                }
                updateDraft { it.copy(textSize = event.index, scale = targetScale) }
            }
            is AppearanceUiEvent.SetMonospace -> updateDraft { it.copy(useMonospace = event.enabled) }
            is AppearanceUiEvent.SetColorIndex -> updateDraft { it.copy(colorIndex = event.index) }
            is AppearanceUiEvent.SetBgColorIndex -> updateDraft { it.copy(bgColorIndex = event.index) }
            is AppearanceUiEvent.SetBorderColorIndex -> updateDraft { it.copy(borderColorIndex = event.index) }
            is AppearanceUiEvent.SetTextColorIndex -> updateDraft { it.copy(textColorIndex = event.index) }
            is AppearanceUiEvent.SetCpuHotWarning -> updateDraft { it.copy(cpuHotWarning = event.enabled) }
            AppearanceUiEvent.ResetChanges -> _draftState.value = null
            AppearanceUiEvent.SaveChanges -> saveCurrentDraft()
        }
    }

    private fun updateDraft(transform: (DraftAppearance) -> DraftAppearance) {
        val current = _draftState.value ?: getCurrentDraftFromState()
        _draftState.value = transform(current)
    }

    private fun getCurrentDraftFromState(): DraftAppearance {
        val state = uiState.value
        return DraftAppearance(
            opacity = state.opacity,
            textSize = state.textSize,
            scale = state.scale,
            useMonospace = state.useMonospace,
            colorIndex = state.colorIndex,
            bgColorIndex = state.bgColorIndex,
            borderColorIndex = state.borderColorIndex,
            textColorIndex = state.textColorIndex,
            cpuHotWarning = state.cpuHotWarning
        )
    }

    private fun saveCurrentDraft() {
        val draft = _draftState.value ?: return
        viewModelScope.launch {
            settingsRepository.setOverlayOpacity(draft.opacity)
            settingsRepository.setOverlayScale(draft.scale)
            if (draft.textSize != -1) {
                settingsRepository.setOverlayTextSize(draft.textSize)
            }
            settingsRepository.setOverlayUseMonospace(draft.useMonospace)
            settingsRepository.setOverlayColorIndex(draft.colorIndex)
            settingsRepository.setOverlayBgColorIndex(draft.bgColorIndex)
            settingsRepository.setOverlayBorderColorIndex(draft.borderColorIndex)
            settingsRepository.setOverlayTextColorIndex(draft.textColorIndex)
            settingsRepository.setCpuHotWarningEnabled(draft.cpuHotWarning)

            _draftState.value = null
            _effectChannel.send(AppearanceUiEffect.ShowToast(R.string.appearance_saved_toast))
        }
    }
}
