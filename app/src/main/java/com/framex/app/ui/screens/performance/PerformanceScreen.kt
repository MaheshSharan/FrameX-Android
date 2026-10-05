package com.framex.app.ui.screens.performance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.gaming.GamingModeState
import com.framex.app.ui.components.FrameXTopBar
import com.framex.app.ui.components.DeepFreezeSafeguardDialog
import com.framex.app.ui.screens.performance.dialogs.AddGameModal
import com.framex.app.ui.screens.performance.dialogs.DeployingGameModal
import com.framex.app.ui.screens.performance.dialogs.GameConfigModal
import com.framex.app.ui.screens.performance.dialogs.RamSuccessBanner
import com.framex.app.ui.screens.performance.sections.AppWhitelistSection
import com.framex.app.ui.screens.performance.sections.GameLauncherSection
import com.framex.app.ui.screens.performance.sections.GoogleAppsSection
import com.framex.app.ui.screens.performance.sections.HeroGamingCard
import com.framex.app.ui.screens.performance.sections.RequirementsSection
import com.framex.app.ui.screens.performance.sections.SystemAuditLogSection
import com.framex.app.ui.screens.performance.sections.SystemHealthGaugesSection
import com.framex.app.ui.screens.performance.sections.VivoPerformanceToolsSection

@Composable
fun PerformanceScreen(
    uiState: PerformanceUiState,
    onEvent: (PerformanceUiEvent) -> Unit,
    getGameConfigBoostRam: (String) -> Boolean,
    setGameConfigBoostRam: (String, Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestDndAccess: () -> Unit,
    onRequestNotificationListener: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shizukuReady = uiState.isShizukuAvailable && uiState.hasShizukuPermission
    val canActivate = shizukuReady

    val isActive = uiState.gamingState is GamingModeState.Active
    val isBusy = uiState.gamingState is GamingModeState.Enabling || uiState.gamingState is GamingModeState.Disabling

    val activeColor = Color(0xFF22C55E)
    val primaryRed = MaterialTheme.colorScheme.primary

    val progressTarget = when (val s = uiState.gamingState) {
        is GamingModeState.Enabling -> s.progress
        is GamingModeState.Disabling -> 0.5f
        else -> 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(300),
        label = "progress"
    )

    val ramPercentage = if (uiState.metricsState.ramTotalGb > 0f) {
        (uiState.metricsState.ramUsedGb / uiState.metricsState.ramTotalGb * 100f).coerceIn(0f, 100f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Header with statusBarsPadding
            item(key = "performance_header") {
                FrameXTopBar(
                    title = stringResource(R.string.performance_title),
                    onNavigateBack = onNavigateBack
                )
            }

            // Hero Gaming Mode card
            item(key = "hero_gaming_card") {
                HeroGamingCard(
                    gamingState = uiState.gamingState,
                    animatedProgress = animatedProgress,
                    canActivate = canActivate,
                    isActive = isActive,
                    isBusy = isBusy,
                    activeColor = activeColor,
                    primaryRed = primaryRed,
                    activeSession = uiState.activeGamingSession,
                    onActivate = { if (canActivate) onEvent(PerformanceUiEvent.EnableGamingMode) },
                    onDeactivate = { onEvent(PerformanceUiEvent.DisableGamingMode) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Requirements & Status section
            item(key = "requirements_section") {
                RequirementsSection(
                    shizukuReady = shizukuReady,
                    isShizukuAvailable = uiState.isShizukuAvailable,
                    hasWriteSettingsAccess = uiState.hasWriteSettingsAccess,
                    hasDndAccess = uiState.hasDndAccess,
                    hasNotifListenerAccess = uiState.hasNotifListenerAccess,
                    onRequestShizuku = onRequestShizuku,
                    onRequestWriteSettings = onRequestWriteSettings,
                    onRequestDndAccess = onRequestDndAccess,
                    onRequestNotificationListener = onRequestNotificationListener
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Dedicated Vivo & iQOO Hardware Suite
            if (uiState.isVivoSuiteEnabled) {
                item(key = "vivo_performance_tools_section") {
                    VivoPerformanceToolsSection(
                        launcherGames = uiState.launcherGames,
                        perfGameList = uiState.vivoPerfGameList,
                        rawPerfGameList = uiState.rawPerfGameList,
                        isRefreshingPerfList = uiState.isRefreshingPerfList,
                        mergeList144 = uiState.mergeList144,
                        raw144MergeList = uiState.raw144MergeList,
                        isRefreshing144List = uiState.isRefreshing144List,
                        show144MergeList = uiState.maxRefreshRate >= 144,
                        onRefreshPerfList = { onEvent(PerformanceUiEvent.RefreshVivoPerfList) },
                        onAddAllToPerfList = { pkgs, onComplete -> onEvent(PerformanceUiEvent.AddAllToPerfList(pkgs, onComplete)) },
                        onRemoveAllFromPerfList = { pkgs, onComplete -> onEvent(PerformanceUiEvent.RemoveAllFromPerfList(pkgs, onComplete)) },
                        onCompileAll = { pkgs, onComplete -> onEvent(PerformanceUiEvent.CompileAllSpeed(pkgs, onComplete)) },
                        onRefresh144MergeList = { onEvent(PerformanceUiEvent.Refresh144MergeList) },
                        onAddAllTo144MergeList = { pkgs, onComplete -> onEvent(PerformanceUiEvent.AddAllTo144MergeList(pkgs, onComplete)) },
                        onRemoveAllFrom144MergeList = { pkgs, onComplete -> onEvent(PerformanceUiEvent.RemoveAllFrom144MergeList(pkgs, onComplete)) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // System Health Gauges
            item(key = "system_health_gauges_section") {
                SystemHealthGaugesSection(
                    ramPercentage = ramPercentage,
                    cpuPercentage = uiState.metricsState.cpuPercentage?.toFloat(),
                    ramUsedGb = uiState.metricsState.ramUsedGb,
                    ramTotalGb = uiState.metricsState.ramTotalGb,
                    fps = uiState.metricsState.fps,
                    maxRefreshRate = uiState.maxRefreshRate
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Game Launcher
            item(key = "game_launcher_section") {
                GameLauncherSection(
                    launcherGames = uiState.launcherGames,
                    userApps = uiState.userApps,
                    onAddGameClicked = { onEvent(PerformanceUiEvent.SetAddGameSheetVisible(true)) },
                    onGameConfigClicked = { pkg -> onEvent(PerformanceUiEvent.SetConfigGamePkg(pkg)) },
                    onRemoveGame = { pkg -> onEvent(PerformanceUiEvent.ToggleLauncherGame(pkg)) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // App Whitelist
            AppWhitelistSection(
                userApps = uiState.userApps,
                whitelist = uiState.whitelist,
                onToggleWhitelist = { pkg -> onEvent(PerformanceUiEvent.ToggleWhitelist(pkg)) }
            )

            // Google Apps
            GoogleAppsSection(
                googleApps = uiState.googleApps,
                whitelist = uiState.whitelist,
                onToggleWhitelist = { pkg -> onEvent(PerformanceUiEvent.ToggleWhitelist(pkg)) },
                deepFreezeEnabled = uiState.deepFreezeEnabled,
                onToggleDeepFreeze = { enabled -> onEvent(PerformanceUiEvent.ToggleDeepFreeze(enabled)) }
            )

            // System Optimization Audit Console
            item(key = "system_audit_log_section") {
                SystemAuditLogSection(
                    isLoggingEnabled = uiState.auditLoggingEnabled,
                    onToggleLogging = { onEvent(PerformanceUiEvent.ToggleAuditLogging(it)) },
                    auditLogs = uiState.vivoAuditLogs,
                    onClearLogs = { onEvent(PerformanceUiEvent.ClearAuditLogs) }
                )
            }
        }

        // Deep Freeze Safeguard Notice Dialog
        if (!uiState.hasSeenDeepFreezeNotice) {
            DeepFreezeSafeguardDialog(
                onDismiss = { onEvent(PerformanceUiEvent.DismissDeepFreezeNotice) },
                onConfirmEnable = {
                    onEvent(PerformanceUiEvent.ToggleDeepFreeze(true))
                    onEvent(PerformanceUiEvent.DismissDeepFreezeNotice)
                }
            )
        }

        // Floating Success Banner
        RamSuccessBanner(bannerText = uiState.bannerMessage)

        // Add Game Modal
        if (uiState.showAddGameSheet) {
            AddGameModal(
                userApps = uiState.userApps,
                launcherGames = uiState.launcherGames,
                onDismiss = { onEvent(PerformanceUiEvent.SetAddGameSheetVisible(false)) },
                onToggleLauncherGame = { pkg -> onEvent(PerformanceUiEvent.ToggleLauncherGame(pkg)) }
            )
        }

        // Per-Game Config Modal
        uiState.configGamePkg?.let { targetPkg ->
            GameConfigModal(
                pkg = targetPkg,
                userApps = uiState.userApps,
                getGameConfigBoostRam = getGameConfigBoostRam,
                setGameConfigBoostRam = setGameConfigBoostRam,
                onBoostClicked = { tPkg ->
                    onEvent(PerformanceUiEvent.SetConfigGamePkg(null))
                    onEvent(PerformanceUiEvent.SetDeployingGamePkg(tPkg))
                },
                onDismiss = { onEvent(PerformanceUiEvent.SetConfigGamePkg(null)) }
            )
        }

        // Deploying Game Modal
        uiState.activeDeployingGamePkg?.let { pkg ->
            DeployingGameModal(
                pkg = pkg,
                onAnimationComplete = { tPkg ->
                    onEvent(PerformanceUiEvent.SetDeployingGamePkg(null))
                    onEvent(PerformanceUiEvent.LaunchGame(tPkg))
                }
            )
        }
    }
}
