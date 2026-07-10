package com.framex.app.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuStatParserTest {
    @Test
    fun parseTotalCpuLineReadsTotalAndIdleTimes() {
        val parsed = CpuStatParser.parseTotalCpuLine(
            listOf("cpu  100 20 30 400 50 6 7 8 0 0")
        )

        assertEquals(CpuTimes(total = 621L, idle = 450L), parsed)
    }

    @Test
    fun parseTotalCpuLineRejectsMalformedInput() {
        assertNull(CpuStatParser.parseTotalCpuLine(emptyList()))
        assertNull(CpuStatParser.parseTotalCpuLine(listOf("cpu0 1 2 3 4")))
        assertNull(CpuStatParser.parseTotalCpuLine(listOf("cpu 1 nope 3 4")))
    }

    @Test
    fun calculateUsageUsesDeltaBetweenSamples() {
        val previous = CpuTimes(total = 100L, idle = 40L)
        val current = CpuTimes(total = 200L, idle = 70L)

        assertEquals(70, CpuStatParser.calculateUsage(previous, current))
    }

    @Test
    fun calculateUsageRejectsInvalidDeltas() {
        assertNull(
            CpuStatParser.calculateUsage(
                previous = CpuTimes(total = 100L, idle = 50L),
                current = CpuTimes(total = 100L, idle = 50L)
            )
        )
        assertNull(
            CpuStatParser.calculateUsage(
                previous = CpuTimes(total = 100L, idle = 50L),
                current = CpuTimes(total = 110L, idle = 40L)
            )
        )
    }
}
