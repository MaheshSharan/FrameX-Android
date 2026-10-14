package com.framex.app.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuTopologyParserTest {

    @Test
    fun formatCoreRangeLabel_formatsSingleAndContiguousCores() {
        assertEquals("CPU 7", CpuTopologyParser.formatCoreRangeLabel(listOf(7)))
        assertEquals("CPU 0~3", CpuTopologyParser.formatCoreRangeLabel(listOf(0, 1, 2, 3)))
        assertEquals("CPU 4~6", CpuTopologyParser.formatCoreRangeLabel(listOf(4, 5, 6)))
        assertEquals("CPU 0,2,4", CpuTopologyParser.formatCoreRangeLabel(listOf(0, 2, 4)))
    }

    @Test
    fun parseTelemetryPayload_handlesThreeClusterSoCWithPerClusterUsage() {
        val sample1 = """
            STAT:cpu  1000 0 500 8500 0 0 0 0
            STAT:cpu0 100 0 50 850 0 0 0 0
            STAT:cpu1 100 0 50 850 0 0 0 0
            STAT:cpu2 100 0 50 850 0 0 0 0
            STAT:cpu3 100 0 50 850 0 0 0 0
            STAT:cpu4 200 0 100 700 0 0 0 0
            STAT:cpu5 200 0 100 700 0 0 0 0
            STAT:cpu6 200 0 100 700 0 0 0 0
            STAT:cpu7 300 0 150 550 0 0 0 0
            POLICY:0:0 1 2 3:1800000:1200000
            POLICY:4:4 5 6:2800000:2400000
            POLICY:7:7:3200000:3050000
        """.trimIndent()

        val (firstSnap, prevMap) = CpuTopologyParser.parseTelemetryPayload(sample1, emptyMap())
        assertEquals(3050, firstSnap.peakMhz)
        assertEquals(1200, firstSnap.effMhz)
        assertEquals(2400, firstSnap.perfMhz)
        assertEquals(3050, firstSnap.ultraMhz)
        assertEquals(3, firstSnap.clusters.size)
        assertNull(firstSnap.totalUsagePercent)

        val sample2 = """
            STAT:cpu  1500 0 700 8800 0 0 0 0
            STAT:cpu0 120 0 60 920 0 0 0 0
            STAT:cpu1 120 0 60 920 0 0 0 0
            STAT:cpu2 120 0 60 920 0 0 0 0
            STAT:cpu3 120 0 60 920 0 0 0 0
            STAT:cpu4 260 0 120 720 0 0 0 0
            STAT:cpu5 260 0 120 720 0 0 0 0
            STAT:cpu6 260 0 120 720 0 0 0 0
            STAT:cpu7 380 0 170 550 0 0 0 0
            POLICY:0:0 1 2 3:1800000:1400000
            POLICY:4:4 5 6:2800000:2600000
            POLICY:7:7:3200000:3150000
        """.trimIndent()

        val (secondSnap, _) = CpuTopologyParser.parseTelemetryPayload(sample2, prevMap)
        assertEquals(3150, secondSnap.peakMhz)
        assertEquals(70, secondSnap.totalUsagePercent)
        assertEquals("CPU 0~3", secondSnap.clusters[0].label)
        assertEquals(30, secondSnap.clusters[0].usagePercent)
        assertEquals("CPU 4~6", secondSnap.clusters[1].label)
        assertEquals(80, secondSnap.clusters[1].usagePercent)
        assertEquals("CPU 7", secondSnap.clusters[2].label)
        assertEquals(100, secondSnap.clusters[2].usagePercent)
        assertEquals(MetricReadStatus.Ok, secondSnap.readStatus)
    }

    @Test
    fun parseTelemetryPayload_handlesFourClusterSnapdragonTopology() {
        val payload = """
            POLICY:0:0 1:1950000:1100000
            POLICY:2:2 3 4:2750000:2100000
            POLICY:5:5 6:2950000:2500000
            POLICY:7:7:3300000:3200000
        """.trimIndent()

        val (snap, _) = CpuTopologyParser.parseTelemetryPayload(payload, emptyMap())
        assertEquals(4, snap.clusters.size)
        assertEquals(1100, snap.effMhz)
        assertEquals(2500, snap.perfMhz)
        assertEquals(3200, snap.ultraMhz)
        assertTrue(snap.clusters.any { it.label == "CPU 5~6" })
    }
}
