package com.framex.app.metrics

internal data class CpuTimes(val total: Long, val idle: Long)

internal object CpuStatParser {
    fun parseTotalCpuLine(lines: List<String>): CpuTimes? {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("cpu ")) {
                return CpuTopologyParser.parseCpuTimeLine(trimmed)
            }
        }
        return null
    }

    fun calculateUsage(previous: CpuTimes, current: CpuTimes): Int? {
        return CpuTopologyParser.calculateUsagePercent(previous, current)
    }
}
