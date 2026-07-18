package com.framex.app.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CpuInfoTopParserTest {

    @Test
    fun blankReturnsNull() {
        assertNull(CpuInfoTopParser.parseTop(""))
        assertNull(CpuInfoTopParser.parseTop("   "))
    }

    @Test
    fun parsesBusiestProcessLine() {
        val dump = """
            Load: 4.2 / 3.1 / 2.0
            CPU usage from 1000ms to 0ms ago (2020-01-01 00:00:00.000 to 2020-01-01 00:00:01.000):
              23% 1234/com.example.game: 15% user + 8% kernel / faults: 10 minor 0 major
              12% 56/system_server: 10% user + 2% kernel
              5.5% 789/com.android.systemui: 4% user + 1.5% kernel
            +0% TOTAL: ...
        """.trimIndent()

        val top = CpuInfoTopParser.parseTop(dump)!!
        assertEquals("com.example.game", top.name)
        assertEquals(23f, top.cpuPercent, 0.01f)
    }

    @Test
    fun skipsTotalLineIfFirst() {
        val dump = """
              100% 0/TOTAL: 50% user + 50% kernel
              18% 42/com.android.chrome: 10% user + 8% kernel
        """.trimIndent()

        val top = CpuInfoTopParser.parseTop(dump)!!
        assertEquals("com.android.chrome", top.name)
        assertEquals(18f, top.cpuPercent, 0.01f)
    }

    @Test
    fun noMatchingLinesReturnsNull() {
        assertNull(CpuInfoTopParser.parseTop("Load: 1.0\nno process lines"))
    }
}
