package com.framex.app.ui.screens.sessionlogs

import com.framex.app.gaming.LogStatus
import com.framex.app.gaming.SystemAuditLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogsTest {

    @Test
    fun defaultUiStateHasExpectedDefaults() {
        val state = SessionLogsUiState()
        assertFalse(state.isLoggingEnabled)
        assertTrue(state.logs.isEmpty())
        assertTrue(state.filteredLogs.isEmpty())
        assertEquals(SessionLogFilter.ALL, state.filter)
        assertEquals(0, state.totalCount)
        assertEquals(0, state.successCount)
        assertEquals(0, state.failedCount)
        assertEquals(0, state.infoCount)
        assertEquals(100, state.successRate)
    }

    @Test
    fun calculateSuccessRateHandlesBoundaryValues() {
        assertEquals(100, SessionLogsUtils.calculateSuccessRate(0, 0))
        assertEquals(100, SessionLogsUtils.calculateSuccessRate(10, 10))
        assertEquals(50, SessionLogsUtils.calculateSuccessRate(10, 5))
        assertEquals(0, SessionLogsUtils.calculateSuccessRate(10, 0))
    }

    @Test
    fun formatSingleLogContainsAllComponents() {
        val log = SystemAuditLog(
            timestamp = 1711929600000L,
            action = "Assert Vivo Monster Mode",
            details = "game_standard_promotion_mode=1",
            status = LogStatus.SUCCESS
        )
        val formatted = SessionLogsUtils.formatSingleLog(log)
        assertTrue(formatted.contains("[OK]"))
        assertTrue(formatted.contains("Assert Vivo Monster Mode"))
        assertTrue(formatted.contains("game_standard_promotion_mode=1"))
    }

    @Test
    fun formatAllLogsProducesStructuredReport() {
        val logs = listOf(
            SystemAuditLog(
                timestamp = 1711929600000L,
                action = "Init Daemon",
                details = "shizuku pid 123",
                status = LogStatus.INFO
            ),
            SystemAuditLog(
                timestamp = 1711929601000L,
                action = "Write Sysfs",
                details = "failed errno 13",
                status = LogStatus.FAILED
            )
        )
        val report = SessionLogsUtils.formatAllLogs(logs)
        assertTrue(report.contains("=== FrameX Session Audit Report ==="))
        assertTrue(report.contains("Total Entries: 2"))
        assertTrue(report.contains("[INFO] Init Daemon"))
        assertTrue(report.contains("[ERR] Write Sysfs"))
        assertTrue(report.contains("failed errno 13"))
        assertTrue(report.contains("End of Report"))
    }
}
