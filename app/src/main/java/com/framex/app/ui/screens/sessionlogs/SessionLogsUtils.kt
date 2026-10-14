package com.framex.app.ui.screens.sessionlogs

import com.framex.app.gaming.LogStatus
import com.framex.app.gaming.SystemAuditLog
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LogStatus.tag: String
    get() = when (this) {
        LogStatus.SUCCESS -> "OK"
        LogStatus.FAILED -> "ERR"
        LogStatus.INFO -> "INFO"
    }

val LogStatus.color: Color
    get() = when (this) {
        LogStatus.SUCCESS -> Color(0xFF10B981)
        LogStatus.FAILED -> Color(0xFFEF4444)
        LogStatus.INFO -> Color(0xFF06B6D4)
    }

val LogStatus.containerColor: Color
    get() = color.copy(alpha = 0.12f)

/**
 * Pure calculation and string formatting utilities for Session Logs.
 */
object SessionLogsUtils {

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /**
     * Formats an epoch millisecond timestamp into a human-readable HH:mm:ss.SSS string.
     */
    fun formatTimestamp(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(timeFormatter)
    }

    /**
     * Formats a single log entry into a standardized monospace diagnostic string.
     */
    fun formatSingleLog(log: SystemAuditLog): String {
        val timeStr = formatTimestamp(log.timestamp)
        return "[$timeStr] [${log.status.tag}] ${log.action} - ${log.details}"
    }

    /**
     * Exports the given list of audit logs as a structured telemetry report.
     */
    fun formatAllLogs(logs: List<SystemAuditLog>): String {
        return buildString {
            appendLine("=== FrameX Session Audit Report ===")
            appendLine("Exported: ${Instant.now()}")
            appendLine("Total Entries: ${logs.size}")
            appendLine("----------------------------------------")
            logs.forEach { log ->
                val timeStr = formatTimestamp(log.timestamp)
                appendLine("[$timeStr] [${log.status.tag}] ${log.action}")
                if (log.details.isNotBlank()) {
                    appendLine("  Details: ${log.details}")
                }
            }
            appendLine("----------------------------------------")
            appendLine("End of Report")
        }
    }

    /**
     * Calculates the percentage of successful operations in the log stream.
     */
    fun calculateSuccessRate(totalCount: Int, successCount: Int): Int {
        if (totalCount <= 0) return 100
        return ((successCount.toDouble() / totalCount.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }
}
