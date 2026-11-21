package com.framex.app.ui.screens.performance

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

@Composable
fun PerformanceRoute(
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PerformanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(PerformanceUiEvent.RefreshInstalledApps)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PerformanceUiEffect.ShowToast -> {
                    val text = if (effect.formatArgs.isEmpty()) {
                        context.getString(effect.messageResId)
                    } else {
                        context.getString(effect.messageResId, *effect.formatArgs.toTypedArray())
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PerformanceScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        getGameConfigBoostRam = viewModel::getGameConfigBoostRam,
        setGameConfigBoostRam = viewModel::setGameConfigBoostRam,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}
