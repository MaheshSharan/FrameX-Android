package com.framex.app.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingRoute(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val selectedMode by viewModel.selectedMode.collectAsState()

    OnboardingScreen(
        selectedMode = selectedMode,
        onSelectPrivilege = { mode, onResult ->
            viewModel.selectPrivilege(mode, onResult)
        },
        onFinishOnboarding = {
            viewModel.completeOnboarding()
            onFinishOnboarding()
        },
        modifier = modifier
    )
}
