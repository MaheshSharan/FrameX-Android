package com.framex.app.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.framex.app.ui.screens.dashboard.components.DashboardHeader
import com.framex.app.ui.screens.dashboard.components.HeroStatusCard
import com.framex.app.ui.screens.dashboard.components.LiveFpsSparklineCard
import com.framex.app.ui.screens.dashboard.components.QuickAccessGrid
import com.framex.app.ui.screens.dashboard.components.RecentSessionsSection
import com.framex.app.ui.screens.dashboard.components.ToolsSection
import com.framex.app.ui.screens.dashboard.components.WhatsNewDialog

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
    modifier: Modifier = Modifier
) {
    // Documented reason: Triggers entrance fade and slide animation after first composition pass.
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        contentVisible = true
    }

    val isWhatsNewVisible = uiState.whatsNewInfo != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (isWhatsNewVisible) Modifier.blur(10.dp) else Modifier)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header: Untouched per task specification
        DashboardHeader(
            onNavigateToAbout = onNavigateToAbout
        )

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 },
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
