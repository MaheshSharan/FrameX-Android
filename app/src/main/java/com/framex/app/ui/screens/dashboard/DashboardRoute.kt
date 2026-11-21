package com.framex.app.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful Route wrapper for Dashboard that collects state in a lifecycle-aware manner
 * and delegates to the stateless [DashboardScreen].
 */
@Composable
fun DashboardRoute(
    onNavigateToAppearance: () -> Unit,
    onNavigateToOverlayCustomization: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToThermalDiagnostics: () -> Unit,
    onNavigateToSessionLogs: () -> Unit,
    onNavigateToHardwareTuning: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToAppearance = onNavigateToAppearance,
        onNavigateToOverlayCustomization = onNavigateToOverlayCustomization,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateToAbout = onNavigateToAbout,
        onNavigateToPerformance = onNavigateToPerformance,
        onNavigateToThermalDiagnostics = onNavigateToThermalDiagnostics,
        onNavigateToSessionLogs = onNavigateToSessionLogs,
        onNavigateToHardwareTuning = onNavigateToHardwareTuning,
        modifier = modifier
    )
}
