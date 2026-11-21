package com.framex.app.ui.screens.performance

import androidx.compose.runtime.Immutable
import com.framex.app.gaming.AppInfo
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.ledger.LedgerSummary
import com.framex.app.metrics.MetricsState
import com.framex.app.permissions.AppPermission

/**
 * Immutable UI model representing the live state of an active gaming session,
 * backed dynamically by real command executions in ExecutionLedger.
 */
@Immutable
data class ActiveGamingSession(
    val title: String,
    val activeGamePackage: String?,
    val suspendedAppsCount: Int,
    val summary: LedgerSummary
)

/**
 * Consolidated Single Source of Truth for the Performance Screen ecosystem.
 */
@Immutable
data class PerformanceUiState(
    val gamingState: GamingModeState = GamingModeState.Idle,
    val isShizukuAvailable: Boolean = false,
    val hasShizukuPermission: Boolean = false,
    val whitelist: Set<String> = emptySet(),
    val launcherGames: Set<String> = emptySet(),
    val userApps: List<AppInfo> = emptyList(),
    val googleApps: List<AppInfo> = emptyList(),
    val metricsState: MetricsState = MetricsState(),
    val fixedPerformanceMode: Boolean = false,
    val deepFreezeEnabled: Boolean = false,
    val hasSeenDeepFreezeNotice: Boolean = false,
    val activeGamingSession: ActiveGamingSession? = null,
    val maxRefreshRate: Int = 60,

    /** Read-only copy of the central permission state; granting happens on System Status. */
    val permissions: Map<AppPermission, Boolean> = emptyMap(),

    // Banner message
    val bannerMessage: String? = null,

    // Optimization Sliders state
    val isBoostingRam: Boolean = false,
    val showRamResult: Boolean = false,
    val isOptimizingNet: Boolean = false,
    val showPingResult: Boolean = false,
    val isResettingDefaults: Boolean = false,
    val showResetResult: Boolean = false,
    val activeLatencyDiagnostic: Int? = null,

    // Dialog & Modal visibility states
    val showAddGameSheet: Boolean = false,
    val configGamePkg: String? = null,
    val activeDeployingGamePkg: String? = null
)

/**
 * Unidirectional UI events for Performance screen interactions.
 */
sealed interface PerformanceUiEvent {
    data class ToggleWhitelist(val packageName: String) : PerformanceUiEvent
    data class ToggleLauncherGame(val packageName: String) : PerformanceUiEvent
    data class ToggleDeepFreeze(val enabled: Boolean) : PerformanceUiEvent
    data object DismissDeepFreezeNotice : PerformanceUiEvent

    data object EnableGamingMode : PerformanceUiEvent
    data object DisableGamingMode : PerformanceUiEvent
    data class LaunchGame(val packageName: String) : PerformanceUiEvent

    data object BoostRam : PerformanceUiEvent
    data object CheckPing : PerformanceUiEvent
    data object ResetDefaults : PerformanceUiEvent

    data object RefreshInstalledApps : PerformanceUiEvent
    data class SetAddGameSheetVisible(val visible: Boolean) : PerformanceUiEvent
    data class SetConfigGamePkg(val packageName: String?) : PerformanceUiEvent
    data class SetDeployingGamePkg(val packageName: String?) : PerformanceUiEvent
    data class SetGameConfigBoostRam(val packageName: String, val enabled: Boolean) : PerformanceUiEvent
}

/**
 * One-shot UI effects.
 */
sealed interface PerformanceUiEffect {
    data class ShowToast(
        @androidx.annotation.StringRes val messageResId: Int,
        val formatArgs: List<Any> = emptyList()
    ) : PerformanceUiEffect
}

data class SystemSettingsGroup(
    val fixedPerformanceMode: Boolean,
    val deepFreezeEnabled: Boolean,
    val hasSeenDeepFreezeNotice: Boolean
)

data class ShizukuAndGamingGroup(
    val gamingState: GamingModeState,
    val isShizukuAvailable: Boolean,
    val hasShizukuPermission: Boolean,
    val whitelist: Set<String>,
    val launcherGames: Set<String>
)

data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

data class DialogStateGroup(
    val showAddGameSheet: Boolean,
    val configGamePkg: String?,
    val activeDeployingGamePkg: String?
)

data class ActionStateGroup(
    val isBoostingRam: Boolean,
    val showRamResult: Boolean,
    val isOptimizingNet: Boolean,
    val showPingResult: Boolean,
    val isResettingDefaults: Boolean,
    val showResetResult: Boolean,
    val activeLatencyDiagnostic: Int?
)

@Immutable
data class IntermediateUiState(
    val userApps: List<AppInfo>,
    val googleApps: List<AppInfo>,
    val activeSession: ActiveGamingSession?,
    val bannerMessage: String?,
    val actions: ActionStateGroup,
    val dialogs: DialogStateGroup,
    val permissions: Map<AppPermission, Boolean>
)
