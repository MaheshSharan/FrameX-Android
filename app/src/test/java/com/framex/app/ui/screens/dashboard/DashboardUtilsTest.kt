package com.framex.app.ui.screens.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardUtilsTest {

    @Test
    fun computeFpsStats_withEmptyHistory_returnsZeros() {
        val stats = DashboardUtils.computeFpsStats(emptyList())
        assertEquals(0, stats.currentFps)
        assertEquals(0, stats.avgFps)
        assertEquals(0, stats.onePercentLow)
        assertEquals(0, stats.frametimeMs)
    }

    @Test
    fun computeFpsStats_withSingleValue_returnsConsistentStats() {
        val stats = DashboardUtils.computeFpsStats(listOf(60))
        assertEquals(60, stats.currentFps)
        assertEquals(60, stats.avgFps)
        assertEquals(60, stats.onePercentLow)
        assertEquals(16, stats.frametimeMs) // 1000 / 60 = 16
    }

    @Test
    fun computeFpsStats_withMultipleValues_calculatesAccurately() {
        val history = listOf(60, 58, 60, 55, 30, 60, 60, 59, 60, 60)
        val stats = DashboardUtils.computeFpsStats(history)
        assertEquals(60, stats.currentFps)
        assertEquals(56, stats.avgFps) // (60*6 + 58 + 55 + 30 + 59)/10 = 562/10 = 56
        assertEquals(30, stats.onePercentLow) // lowest in bottom 10%
        assertEquals(16, stats.frametimeMs)
    }

    @Test
    fun computeFpsStats_withZeroFps_handlesFrametimeGracefully() {
        val stats = DashboardUtils.computeFpsStats(listOf(0, 0))
        assertEquals(0, stats.currentFps)
        assertEquals(0, stats.frametimeMs)
    }
}
