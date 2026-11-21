package com.framex.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.repository.SettingsRepository
import com.framex.app.ui.screens.permissions.components.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val privilegeManager: PrivilegeManager
) : ViewModel() {

    private val _toasts = Channel<Int>(Channel.BUFFERED)

    /** String resources to show as one-shot toasts. */
    val toasts: Flow<Int> = _toasts.receiveAsFlow()

    val uiState: StateFlow<OnboardingUiState> = combine(
        privilegeManager.currentMode,
        privilegeManager.isReady
    ) { mode, ready -> OnboardingUiState(mode, ready) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = OnboardingUiState(privilegeManager.currentMode.value, privilegeManager.isReady.value)
        )

    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            OnboardingUiEvent.CompleteOnboarding -> settingsRepository.setOnboardingCompleted(true)
            is OnboardingUiEvent.SelectMode -> selectMode(event.mode)
        }
    }

    private fun selectMode(mode: PrivilegeMode) {
        viewModelScope.launch {
            privilegeManager.selectMode(mode).messageRes()?.let(_toasts::trySend)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
