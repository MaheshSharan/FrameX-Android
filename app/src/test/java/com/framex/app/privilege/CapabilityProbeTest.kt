package com.framex.app.privilege

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityProbeTest {

    private fun backend(uid: String, sysfsWorks: Boolean, telemetry: String): FakePrivilegeBackend =
        FakePrivilegeBackend(PrivilegeMode.SHIZUKU).apply {
            handler = { command ->
                when {
                    command == "id -u" -> PrivilegedOutcome.Success(uid)
                    command == "id -Z" -> PrivilegedOutcome.Success("u:r:test:s0")
                    command == FakePrivilegeBackend.CPU_KEY -> PrivilegedOutcome.Success(telemetry)
                    sysfsWorks -> PrivilegedOutcome.Success("")
                    else -> PrivilegedOutcome.NonZeroExit(1, "Permission denied")
                }
            }
            start()
        }

    @Test
    fun shellUser_withRejectedSysfsWrite_cannotTune() = runBlocking {
        val caps = CapabilityProbe.run(backend("2000", sysfsWorks = false, telemetry = "STAT:cpu 1 2 3"))
        assertEquals(2000, caps.uid)
        assertEquals("u:r:test:s0", caps.seContext)
        assertTrue(caps.canReadProcStat)
        assertFalse(caps.canWriteSysfs)
    }

    @Test
    fun root_withAcceptedSysfsWrite_canTune() = runBlocking {
        val caps = CapabilityProbe.run(backend("0", sysfsWorks = true, telemetry = "STAT:cpu 1 2 3"))
        assertEquals(0, caps.uid)
        assertTrue(caps.canWriteSysfs)
    }

    @Test
    fun missingTelemetry_meansProcStatIsNotReadable() = runBlocking {
        val caps = CapabilityProbe.run(backend("2000", sysfsWorks = false, telemetry = ""))
        assertFalse(caps.canReadProcStat)
    }

    @Test
    fun unparsableUid_isReportedAsUnknown() = runBlocking {
        val caps = CapabilityProbe.run(backend("not-a-number", sysfsWorks = false, telemetry = ""))
        assertEquals(-1, caps.uid)
    }
}
