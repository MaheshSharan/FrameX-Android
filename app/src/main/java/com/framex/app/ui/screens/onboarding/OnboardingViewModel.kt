package com.framex.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import com.framex.app.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    fun completeOnboarding() {
        settingsRepository.setOnboardingCompleted(true)
    }

    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            OnboardingUiEvent.CompleteOnboarding -> completeOnboarding()
        }
    }
}
