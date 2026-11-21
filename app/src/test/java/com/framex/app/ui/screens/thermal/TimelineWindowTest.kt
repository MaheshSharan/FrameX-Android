package com.framex.app.ui.screens.thermal

import com.framex.app.metrics.MetricsEngine.MetricsSnapshot
import com.framex.app.metrics.MetricsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineWindowTest {

    private fun samples(count: Int) = List(count) { MetricsSnapshot(it * 1000L, MetricsState()) }

    @Test
    fun allWindowReturnsSameList() {
        val list = samples(10)
        assertSame(list, list.lastWindow(null))
    }

    @Test
    fun windowKeepsNewestSamplesInOrder() {
        val windowed = samples(600).lastWindow(120)
        assertEquals(121, windowed.size)
        assertEquals(479_000L, windowed.first().timestampMs)
        assertEquals(599_000L, windowed.last().timestampMs)
    }

    @Test
    fun windowLargerThanHistoryKeepsEverything() {
        assertEquals(30, samples(30).lastWindow(600).size)
    }

    @Test
    fun emptyHistoryStaysEmpty() {
        assertTrue(emptyList<MetricsSnapshot>().lastWindow(120).isEmpty())
    }
}
