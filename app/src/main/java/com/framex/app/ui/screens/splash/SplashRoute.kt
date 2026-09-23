package com.framex.app.ui.screens.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framex.app.update.DownloadState

@Composable
fun SplashRoute(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.onEvent(SplashUiEvent.StartInitialization)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SplashUiEffect.NavigateToDashboard -> onNavigateToDashboard()
                SplashUiEffect.NavigateToOnboarding -> onNavigateToOnboarding()
                SplashUiEffect.OpenUnknownSourcesSettings -> {
                    viewModel.updateInstaller.openUnknownAppSourcesSettings()
                }
            }
        }
    }

    // Auto-install completed download
    LaunchedEffect(state.downloadState) {
        val currentDownload = state.downloadState
        if (currentDownload is DownloadState.Completed) {
            viewModel.onEvent(SplashUiEvent.InstallDownloadedApk(currentDownload.apkFile))
        }
    }

    // Permission return check
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(SplashUiEvent.OnResumeCheckInstallPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SplashScreen(
        state = state,
        onEvent = viewModel::onEvent,
        canInstallPackages = { viewModel.updateInstaller.canInstallPackages() },
        modifier = modifier
    )
}
