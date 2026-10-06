package com.framex.app.ui.screens.sessionlogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.framex.app.R
import com.framex.app.ui.components.FrameXTopBar
import com.framex.app.ui.screens.sessionlogs.components.ClearLogsConfirmDialog
import com.framex.app.ui.screens.sessionlogs.components.SessionLogEntryCard
import com.framex.app.ui.screens.sessionlogs.components.SessionLogsEmptyView
import com.framex.app.ui.screens.sessionlogs.components.SessionLogsFilterBar
import com.framex.app.ui.screens.sessionlogs.components.SessionLogsHeaderCard

/**
 * Pure, stateless screen for Session Logs and hardware optimization audit trails.
 */
@Composable
fun SessionLogsScreen(
    uiState: SessionLogsUiState,
    onEvent: (SessionLogsUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        FrameXTopBar(
            title = stringResource(R.string.session_logs_title),
            onNavigateBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header & Live Recorder Status Card
            item(key = "header_status_card") {
                SessionLogsHeaderCard(
                    isLoggingEnabled = uiState.isLoggingEnabled,
                    totalCount = uiState.totalCount,
                    successRate = uiState.successRate,
                    failedCount = uiState.failedCount,
                    onToggleLogging = { onEvent(SessionLogsUiEvent.ToggleLogging(it)) }
                )
            }

            // 2. Filter & Actions Bar
            item(key = "filter_actions_bar") {
                SessionLogsFilterBar(
                    selectedFilter = uiState.filter,
                    totalCount = uiState.totalCount,
                    successCount = uiState.successCount,
                    failedCount = uiState.failedCount,
                    infoCount = uiState.infoCount,
                    onSelectFilter = { onEvent(SessionLogsUiEvent.SelectFilter(it)) },
                    onCopyAll = { onEvent(SessionLogsUiEvent.CopyAllLogs) },
                    onClearClick = { showClearConfirmDialog = true }
                )
            }

            // 3. Log Items or Empty View
            if (uiState.filteredLogs.isEmpty()) {
                item(key = "empty_logs_view") {
                    SessionLogsEmptyView(isLoggingEnabled = uiState.isLoggingEnabled)
                }
            } else {
                items(
                    items = uiState.filteredLogs,
                    key = { "${it.timestamp}_${it.action.hashCode()}_${it.status.name}" }
                ) { log ->
                    SessionLogEntryCard(
                        log = log,
                        onCopy = { onEvent(SessionLogsUiEvent.CopySingleLog(log)) }
                    )
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        ClearLogsConfirmDialog(
            onConfirm = {
                showClearConfirmDialog = false
                onEvent(SessionLogsUiEvent.ClearLogs)
            },
            onDismiss = {
                showClearConfirmDialog = false
            }
        )
    }
}
