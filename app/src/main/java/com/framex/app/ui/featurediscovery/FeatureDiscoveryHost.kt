package com.framex.app.ui.featurediscovery

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.framex.app.ui.featurediscovery.components.FeatureDiscoveryOverlay
import kotlinx.coroutines.delay

val LocalFeatureDiscoveryViewModel = staticCompositionLocalOf<FeatureDiscoveryViewModel> {
    error("FeatureDiscoveryViewModel not provided")
}

@Composable
fun FeatureDiscoveryHost(
    viewModel: FeatureDiscoveryViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
    content: @Composable () -> Unit
) {
    val guideState by viewModel.guideState.collectAsState()
    val completionEvent by viewModel.completionEvent.collectAsState()

    CompositionLocalProvider(LocalFeatureDiscoveryViewModel provides viewModel) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            FeatureDiscoveryOverlay(
                guideState = guideState,
                completionEvent = completionEvent,
                onComplete = viewModel::completeGuide,
                onSkip = viewModel::skipAll,
                onDismissOutside = viewModel::dismissOverlay,
                onLaunchAction = viewModel::launchAction,
                onCompletionDismiss = viewModel::consumeCompletionEvent
            )
        }
    }
}

@Composable
fun ScreenDiscoveryEffect(
    screenId: DiscoveryScreenId,
    viewModel: FeatureDiscoveryViewModel = LocalFeatureDiscoveryViewModel.current
) {
    LaunchedEffect(screenId) {
        delay(SCREEN_ENTER_DELAY_MS)
        viewModel.onScreenEntered(screenId)
    }
}

private const val SCREEN_ENTER_DELAY_MS = 400L
