package com.framex.app.ui.screens.sessionlogs

import androidx.compose.runtime.Immutable
import com.framex.app.gaming.SystemAuditLog

/**
 * Filter categories for session audit log records.
 */
enum class SessionLogFilter {
    ALL,
    SUCCESS,
    FAILED,
    INFO
}

/**
 * Immutable UI State for the Session Logs screen.
 */
@Immutable
data class SessionLogsUiState(
    val isLoggingEnabled: Boolean = false,
    val logs: List<SystemAuditLog> = emptyList(),
    val filteredLogs: List<SystemAuditLog> = emptyList(),
    val filter: SessionLogFilter = SessionLogFilter.ALL,
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val infoCount: Int = 0,
    val successRate: Int = 100
)

/**
 * User interactions and lifecycle actions on the Session Logs screen.
 */
sealed interface SessionLogsUiEvent {
    data class ToggleLogging(val enabled: Boolean) : SessionLogsUiEvent
    data object ClearLogs : SessionLogsUiEvent
    data class SelectFilter(val filter: SessionLogFilter) : SessionLogsUiEvent
    data object CopyAllLogs : SessionLogsUiEvent
    data class CopySingleLog(val log: SystemAuditLog) : SessionLogsUiEvent
}

/**
 * One-shot UI effects for snackbars, toasts, or clipboard feedback.
 */
sealed interface SessionLogsUiEffect {
    data class ShowToast(val message: String) : SessionLogsUiEffect
    data class CopyToClipboard(val text: String, val label: String) : SessionLogsUiEffect
}
