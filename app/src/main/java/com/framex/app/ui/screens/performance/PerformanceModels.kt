package com.framex.app.ui.screens.performance

import androidx.compose.runtime.Immutable
import com.framex.app.gaming.AppInfo
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.SystemAuditLog
import com.framex.app.gaming.ledger.LedgerSummary
import com.framex.app.metrics.MetricsState

/**
 * Immutable UI model representing the live state of an active gaming session,
 * backed dynamically by real command executions in ExecutionLedger.
 */
@Immutable
data class ActiveGamingSession(
    val title: String,
    val isVivoDevice: Boolean,
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
    val isVivoSuiteEnabled: Boolean = false,
    val rawPerfGameList: String? = null,
    val vivoPerfGameList: List<String> = emptyList(),
    val vivoAuditLogs: List<SystemAuditLog> = emptyList(),
    val auditLoggingEnabled: Boolean = false,
    val maxRefreshRate: Int = 60,
    val safeToSuspendList: List<String> = emptyList(),
    val gamingDaemonsList: List<String> = emptyList(),

    // Action execution states
    val isBoostingRam: Boolean = false,
    val isOptimizingNet: Boolean = false,
    val isResettingDefaults: Boolean = false,
    val bannerMessage: String? = null,
    val activeLatencyDiagnostic: Int? = null,
    val showRamResult: Boolean = false,
    val showPingResult: Boolean = false,
    val showResetResult: Boolean = false,

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
    data class ToggleFixedPerformanceMode(val enabled: Boolean) : PerformanceUiEvent
    data class ToggleDeepFreeze(val enabled: Boolean) : PerformanceUiEvent
    data object DismissDeepFreezeNotice : PerformanceUiEvent

    data object EnableGamingMode : PerformanceUiEvent
    data object DisableGamingMode : PerformanceUiEvent
    data class LaunchGame(val packageName: String) : PerformanceUiEvent

    data object BoostRam : PerformanceUiEvent
    data object CheckPing : PerformanceUiEvent
    data object ResetDefaults : PerformanceUiEvent

    data object RefreshVivoPerfList : PerformanceUiEvent
    data class AddAllToPerfList(val packages: Set<String>) : PerformanceUiEvent
    data class RemoveAllFromPerfList(val packages: Set<String>) : PerformanceUiEvent
    data class CompileAllSpeed(val packages: Set<String>) : PerformanceUiEvent

    data class ToggleAuditLogging(val enabled: Boolean) : PerformanceUiEvent
    data object ClearAuditLogs : PerformanceUiEvent

    data object RefreshInstalledApps : PerformanceUiEvent
    data class SetAddGameSheetVisible(val visible: Boolean) : PerformanceUiEvent
    data class SetConfigGamePkg(val packageName: String?) : PerformanceUiEvent
    data class SetDeployingGamePkg(val packageName: String?) : PerformanceUiEvent
    data class SetGameConfigBoostRam(val packageName: String, val enabled: Boolean) : PerformanceUiEvent
    data class ToggleMemc(val packageName: String, val enabled: Boolean, val onComplete: (Boolean) -> Unit) : PerformanceUiEvent
}

/**
 * One-shot UI effects.
 */
sealed interface PerformanceUiEffect {
    data class ShowToast(val message: String) : PerformanceUiEffect
}
