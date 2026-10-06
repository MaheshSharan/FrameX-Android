package com.framex.app.ui.screens.sessionlogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.gaming.LogStatus
import com.framex.app.gaming.SystemAuditLog
import com.framex.app.gaming.SystemAuditLogRepository
import com.framex.app.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State manager for the Session Logs screen.
 * Observes [SystemAuditLogRepository] and [SettingsRepository] to produce a single
 * immutable [SessionLogsUiState] stream with O(1) filter switching.
 */
@HiltViewModel
class SessionLogsViewModel @Inject constructor(
    private val auditLogRepository: SystemAuditLogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(SessionLogFilter.ALL)

    private val _uiEffect = MutableSharedFlow<SessionLogsUiEffect>()
    val uiEffect: SharedFlow<SessionLogsUiEffect> = _uiEffect.asSharedFlow()

    val uiState: StateFlow<SessionLogsUiState> = combine(
        auditLogRepository.logs,
        settingsRepository.auditLoggingEnabled,
        _selectedFilter
    ) { logs, isLoggingEnabled, filter ->
        buildUiState(logs, isLoggingEnabled, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionLogsUiState()
    )

    fun onEvent(event: SessionLogsUiEvent) {
        when (event) {
            is SessionLogsUiEvent.ToggleLogging -> {
                settingsRepository.setAuditLoggingEnabled(event.enabled)
            }
            SessionLogsUiEvent.ClearLogs -> {
                auditLogRepository.clear()
            }
            is SessionLogsUiEvent.SelectFilter -> {
                _selectedFilter.value = event.filter
            }
            SessionLogsUiEvent.CopyAllLogs -> {
                copyAllLogsToClipboard()
            }
            is SessionLogsUiEvent.CopySingleLog -> {
                copySingleLogToClipboard(event.log)
            }
        }
    }

    private fun copyAllLogsToClipboard() {
        val currentLogs = uiState.value.filteredLogs
        if (currentLogs.isEmpty()) return
        val exportText = SessionLogsUtils.formatAllLogs(currentLogs)
        viewModelScope.launch {
            _uiEffect.emit(SessionLogsUiEffect.CopyToClipboard(exportText, "Session Logs"))
        }
    }

    private fun copySingleLogToClipboard(log: SystemAuditLog) {
        val formatted = SessionLogsUtils.formatSingleLog(log)
        viewModelScope.launch {
            _uiEffect.emit(SessionLogsUiEffect.CopyToClipboard(formatted, "Log Entry"))
        }
    }

    private fun buildUiState(
        logs: List<SystemAuditLog>,
        isLoggingEnabled: Boolean,
        filter: SessionLogFilter
    ): SessionLogsUiState {
        val filteredLogs = when (filter) {
            SessionLogFilter.ALL -> logs
            SessionLogFilter.SUCCESS -> logs.filter { it.status == LogStatus.SUCCESS }
            SessionLogFilter.FAILED -> logs.filter { it.status == LogStatus.FAILED }
            SessionLogFilter.INFO -> logs.filter { it.status == LogStatus.INFO }
        }

        var successCount = 0
        var failedCount = 0
        var infoCount = 0
        for (log in logs) {
            when (log.status) {
                LogStatus.SUCCESS -> successCount++
                LogStatus.FAILED -> failedCount++
                LogStatus.INFO -> infoCount++
            }
        }

        val total = logs.size
        val rate = SessionLogsUtils.calculateSuccessRate(total, successCount)

        return SessionLogsUiState(
            isLoggingEnabled = isLoggingEnabled,
            logs = logs,
            filteredLogs = filteredLogs,
            filter = filter,
            totalCount = total,
            successCount = successCount,
            failedCount = failedCount,
            infoCount = infoCount,
            successRate = rate
        )
    }
}
