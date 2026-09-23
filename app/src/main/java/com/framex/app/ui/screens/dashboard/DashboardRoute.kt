package com.framex.app.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful Route wrapper for Dashboard that collects state in a lifecycle-aware manner,
 * observes ON_RESUME to refresh permissions, and delegates to the stateless [DashboardScreen].
 */
@Composable
fun DashboardRoute(
    onNavigateToAppearance: () -> Unit,
    onNavigateToOverlayCustomization: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToThermalDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DashboardScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToAppearance = onNavigateToAppearance,
        onNavigateToOverlayCustomization = onNavigateToOverlayCustomization,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToPerformance = onNavigateToPerformance,
        onNavigateToThermalDiagnostics = onNavigateToThermalDiagnostics,
        modifier = modifier
    )
}
