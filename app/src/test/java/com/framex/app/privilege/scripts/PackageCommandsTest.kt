package com.framex.app.privilege.scripts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageCommandsTest {

    @Test
    fun invalidPackageNamesNeverReachTheShell() {
        val valid = PackageCommands.validPackages(listOf("com.good.app", "bad;rm -rf /", "x y", ""))
        assertEquals(listOf("com.good.app"), valid)
    }

    @Test
    fun everyPackageIsHandledInOneCall() {
        var calls = 0
        val outcome = PackageCommands.applySuspend(listOf("a.b", "c.d"), suspended = true) { cmd ->
            calls++
            assertTrue(cmd.contains("cmd package suspend --user 0 a.b"))
            assertTrue(cmd.contains("cmd package suspend --user 0 c.d"))
            0 to "FXS|a.b|0|0\nFXS|c.d|0|0"
        }
        assertEquals(1, calls)
        assertEquals(2, outcome.successCount)
        assertTrue(outcome.failedPackages.isEmpty())
    }

    @Test
    fun rejectedPackagesAreReportedAndUninstalledOnesSkipped() {
        val report = "FXS|a.b|0|0\nFXS|c.d|1|1\nFXS|e.f|255|0"
        val outcome = PackageCommands.parseSuspendReport(listOf("a.b", "c.d", "e.f", "g.h"), report)
        assertEquals(1, outcome.successCount)
        assertEquals(listOf("e.f", "g.h"), outcome.failedPackages)
    }

    @Test
    fun emptyOrInvalidInputRunsNothing() {
        var calls = 0
        val outcome = PackageCommands.applySuspend(listOf("bad pkg"), suspended = true) { calls++; 0 to "" }
        assertEquals(0, calls)
        assertEquals(0, outcome.successCount)
    }

    @Test
    fun appOpBatchHasOneMarkedCommandPerPackage() {
        val cmd = PackageCommands.appOpBatch(listOf("a.b", "c.d"), opCode = 24, mode = 2)
        assertTrue(cmd.contains("cmd appops set a.b 24 deny"))
        assertTrue(cmd.contains("cmd appops set c.d 24 deny"))
        assertEquals(2, Regex("FXOP:").findAll(cmd).count())
        assertFalse(cmd.contains("bad"))
    }

    @Test
    fun appOpSuccessesAreCountedFromMarkers() {
        assertEquals(2, PackageCommands.countAppOpSuccesses("FXOP:0\nFXOP:1\nFXOP:0\n"))
    }
}
