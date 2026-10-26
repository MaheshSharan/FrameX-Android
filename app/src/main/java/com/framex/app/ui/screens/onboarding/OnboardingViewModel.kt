package com.framex.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    val privilegeManager: PrivilegeManager
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(privilegeManager.currentMode.value)
    val selectedMode: StateFlow<PrivilegeMode> = _selectedMode.asStateFlow()

    private val _rootRequestFailed = MutableStateFlow(false)
    val rootRequestFailed: StateFlow<Boolean> = _rootRequestFailed.asStateFlow()

    fun selectPrivilege(mode: PrivilegeMode, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (mode == PrivilegeMode.ROOT) {
                val previous = privilegeManager.currentMode.value
                privilegeManager.switchMode(PrivilegeMode.ROOT)
                privilegeManager.requestAccess()
                val hasRoot = privilegeManager.isReady.value
                if (hasRoot) {
                    _selectedMode.value = PrivilegeMode.ROOT
                    _rootRequestFailed.value = false
                    onResult(true)
                } else {
                    privilegeManager.switchMode(previous)
                    _rootRequestFailed.value = true
                    onResult(false)
                }
            } else {
                _selectedMode.value = mode
                privilegeManager.switchMode(mode)
                _rootRequestFailed.value = false
                onResult(true)
            }
        }
    }

    fun completeOnboarding() {
        settingsRepository.setOnboardingCompleted(true)
    }

    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            OnboardingUiEvent.CompleteOnboarding -> completeOnboarding()
        }
    }
}
