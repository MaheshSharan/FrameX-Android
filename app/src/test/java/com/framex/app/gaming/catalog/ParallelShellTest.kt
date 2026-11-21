package com.framex.app.gaming.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelShellTest {

    @Test
    fun commandsAreRunInGroupsAndWaitedFor() {
        val script = ParallelShell.script(List(20) { "cmd$it" })
        assertEquals(3, script.lines().count { it == "wait" })
        assertTrue(script.contains("{ cmd19; } || echo FRAMEX_FAIL:19"))
    }

    @Test
    fun reportListsFailuresAndDurationsByIndex() {
        val report = ParallelShell.parse(
            "noise\nFRAMEX_T:0:120\nFRAMEX_FAIL:2\nFRAMEX_T:2:1400\nFRAMEX_T:1:80\n"
        )
        assertEquals(listOf(2), report.failedIndexes)
        assertEquals(mapOf(0 to 120L, 2 to 1400L, 1 to 80L), report.durationsMs)
    }

    @Test
    fun malformedLinesAreIgnored() {
        val report = ParallelShell.parse("FRAMEX_FAIL:abc\nFRAMEX_T:x:y\nFRAMEX_T:3\n")
        assertTrue(report.failedIndexes.isEmpty())
        assertTrue(report.durationsMs.isEmpty())
    }
}
