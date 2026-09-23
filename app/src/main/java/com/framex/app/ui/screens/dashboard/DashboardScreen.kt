package com.framex.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framex.app.ui.screens.dashboard.components.DashboardHeader
import com.framex.app.ui.screens.dashboard.components.HeroStatusCard
import com.framex.app.ui.screens.dashboard.components.QuickAccessGrid

/**
 * Pure, stateless screen for Dashboard telemetry and navigation.
 * Accepts immutable [DashboardUiState] and dispatches [DashboardUiEvent] actions.
 */
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onEvent: (DashboardUiEvent) -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToOverlayCustomization: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToThermalDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DashboardHeader(
            onNavigateToAbout = onNavigateToAbout
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 6 },
                label = "heroStatusAnim"
            ) {
                HeroStatusCard(
                    uiState = uiState,
                    onStartOverlay = { onEvent(DashboardUiEvent.StartOverlay) },
                    onStopOverlay = { onEvent(DashboardUiEvent.StopOverlay) },
                    onNavigateToPermissions = onNavigateToPermissions
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(tween(500, delayMillis = 100)) { it / 6 },
                label = "quickAccessAnim"
            ) {
                QuickAccessGrid(
                    isShizukuReady = uiState.isShizukuReady,
                    onNavigateToOverlayCustomization = onNavigateToOverlayCustomization,
                    onNavigateToAppearance = onNavigateToAppearance,
                    onNavigateToPerformance = onNavigateToPerformance,
                    onNavigateToPermissions = onNavigateToPermissions,
                    onNavigateToThermalDiagnostics = onNavigateToThermalDiagnostics
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
