package com.framex.app.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framex.app.ui.components.ToastEffect

@Composable
fun OnboardingRoute(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ToastEffect(viewModel.toasts)

    OnboardingScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onFinishOnboarding = {
            viewModel.onEvent(OnboardingUiEvent.CompleteOnboarding)
            onFinishOnboarding()
        },
        modifier = modifier
    )
}
