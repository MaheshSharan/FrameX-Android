package com.framex.app.ui.screens.dashboard

/**
 * Pure calculation logic for Dashboard telemetry metrics.
 * Keeps heavy sorting and mathematical algorithms off Compose UI threads.
 */
object DashboardUtils {

    fun computeFpsStats(history: List<Int>): FpsStatsSummary {
        if (history.isEmpty()) return FpsStatsSummary()

        val current = history.last()
        val avg = history.average().toInt()
        val sorted = history.sorted()
        val count1Percent = (history.size * 0.1).toInt().coerceAtLeast(1)
        val low1 = sorted.take(count1Percent).firstOrNull() ?: 0
        val frametime = if (current > 0) (1000f / current).toInt() else 0

        return FpsStatsSummary(
            currentFps = current,
            avgFps = avg,
            onePercentLow = low1,
            frametimeMs = frametime
        )
    }
}
