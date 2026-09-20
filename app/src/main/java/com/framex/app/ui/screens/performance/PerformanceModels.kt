package com.framex.app.ui.screens.performance

import androidx.compose.runtime.Immutable
import com.framex.app.gaming.ledger.LedgerSummary

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
