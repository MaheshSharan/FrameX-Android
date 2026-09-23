package com.framex.app.ui.screens.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.framex.app.update.DownloadState

@Composable
fun AboutRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle One-shot effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AboutUiEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is AboutUiEffect.OpenUnknownSourcesSettings -> {
                    viewModel.updateInstaller.openUnknownAppSourcesSettings()
                }
                is AboutUiEffect.OpenBrowser -> {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                        context.startActivity(intent)
                    }
                }
                is AboutUiEffect.ShareLogIntent -> {
                    // Handled internally in CrashHandler.shareCrashLog
                }
            }
        }
    }

    // Auto-install on download completed
    LaunchedEffect(state.downloadState) {
        val currentDownload = state.downloadState
        if (currentDownload is DownloadState.Completed) {
            viewModel.onEvent(AboutUiEvent.InstallDownloadedApk(currentDownload.apkFile))
        }
    }

    // Check install permission when returning to app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(AboutUiEvent.OnResumeCheckInstallPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AboutScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        onOpenUrl = { url ->
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        },
        canInstallPackages = { viewModel.updateInstaller.canInstallPackages() },
        deviceModelInfo = viewModel.deviceDiagnosticManager.getDeviceModelInfo(),
        modifier = modifier
    )
}
