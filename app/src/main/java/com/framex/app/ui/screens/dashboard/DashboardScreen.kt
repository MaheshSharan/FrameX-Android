package com.framex.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.framex.app.ui.screens.dashboard.components.DashboardHeader
import com.framex.app.ui.screens.dashboard.components.HeroStatusCard
import com.framex.app.ui.screens.dashboard.components.LiveFpsSparklineCard
import com.framex.app.ui.screens.dashboard.components.QuickAccessGrid
import com.framex.app.ui.screens.dashboard.components.RecentSessionsSection
import com.framex.app.ui.screens.dashboard.components.ToolsSection
import com.framex.app.ui.screens.dashboard.components.WhatsNewDialog
import com.framex.app.ui.theme.FrameXMotion
import com.framex.app.ui.theme.FrameXSpacing

/**
 * Pure, stateless screen for Dashboard telemetry and navigation.
 * Accepts immutable [DashboardUiState] and dispatches [DashboardUiEvent] actions.
 * Employs LazyColumn with explicit stable keys for 120Hz smooth scrolling.
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
    onNavigateToSessionLogs: () -> Unit,
    onNavigateToHardwareTuning: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val reduceMotion = FrameXMotion.rememberReduceMotion()
    var contentVisible by remember(reduceMotion) { mutableStateOf(reduceMotion) }
    LaunchedEffect(reduceMotion) {
        contentVisible = true
    }

    val isWhatsNewVisible = uiState.whatsNewInfo != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = FrameXSpacing.MaxContentWidth)
                .fillMaxSize()
        ) {
            DashboardHeader(
                onNavigateToAbout = onNavigateToAbout
            )

            AnimatedVisibility(
                visible = contentVisible,
                enter = if (reduceMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 }
                },
                label = "dashboardContentAnim",
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // 1. Hero Status Card
                    item(key = "hero_status_card") {
                        HeroStatusCard(
                            uiState = uiState,
                            onStartOverlay = { onEvent(DashboardUiEvent.StartOverlay) },
                            onStopOverlay = { onEvent(DashboardUiEvent.StopOverlay) },
                            onNavigateToPermissions = onNavigateToPermissions
                        )
                    }

                    // 2. Live Metrics Section (Only FPS card with oscilloscope curve)
                    item(key = "live_metrics_card") {
                        LiveFpsSparklineCard(
                            fpsHistory = uiState.fpsHistory,
                            stats = uiState.fpsStats
                        )
                    }

                    // Hardware Tuning Banner Card
                    item(key = "hardware_tuning_card") {
                        com.framex.app.ui.screens.dashboard.components.HardwareTuningDashboardCard(
                            onClick = onNavigateToHardwareTuning
                        )
                    }

                    // 3. Quick Access 2x2 Grid (Metrics, Theme, Performance, Shizuku)
                    item(key = "quick_access_grid") {
                        QuickAccessGrid(
                            isShizukuReady = uiState.isShizukuReady,
                            onNavigateToOverlayCustomization = onNavigateToOverlayCustomization,
                            onNavigateToAppearance = onNavigateToAppearance,
                            onNavigateToPerformance = onNavigateToPerformance,
                            onNavigateToPermissions = onNavigateToPermissions
                        )
                    }

                    // 4. Tools Section (Thermal diagnostics, Session logs)
                    item(key = "tools_section") {
                        ToolsSection(
                            onNavigateToThermalDiagnostics = onNavigateToThermalDiagnostics,
                            onNavigateToSessionLogs = onNavigateToSessionLogs
                        )
                    }

                    // 5. Recent Sessions Section
                    item(key = "recent_sessions_section") {
                        RecentSessionsSection()
                    }
                }
            }
        }

        // Lightweight scrim replaces root blur when What's New dialog is visible (Plan 2.1)
        if (isWhatsNewVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        // Post-Update What's New Dialog
        uiState.whatsNewInfo?.let { info ->
            WhatsNewDialog(
                info = info,
                onDismiss = { onEvent(DashboardUiEvent.DismissWhatsNew) },
                onActionClick = { actionId ->
                    if (actionId == "execution_center") {
                        onEvent(DashboardUiEvent.DismissWhatsNew)
                        onNavigateToAbout()
                    }
                }
            )
        }
    }
}
