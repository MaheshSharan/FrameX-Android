package com.framex.app.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingRoute(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    OnboardingScreen(
        onFinishOnboarding = {
            viewModel.completeOnboarding()
            onFinishOnboarding()
        },
        modifier = modifier
    )
}
