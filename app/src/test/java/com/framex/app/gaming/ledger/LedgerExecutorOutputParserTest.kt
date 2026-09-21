package com.framex.app.gaming.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerExecutorOutputParserTest {

    @Test
    fun parseBatchOutput_allSuccessful() {
        val output = "result1\n__FX#0:0\nresult2\n__FX#1:0"
        val results = LedgerExecutor.parseBatchOutput(output, 2)

        assertEquals(2, results.size)
        assertEquals(0, results[0].exitCode)
        assertFalse(results[0].hasErrorIndicator)
        assertEquals(0, results[1].exitCode)
        assertFalse(results[1].hasErrorIndicator)
    }

    @Test
    fun parseBatchOutput_failureExitCode() {
        val output = "__FX#0:0\nfailed\n__FX#1:1"
        val results = LedgerExecutor.parseBatchOutput(output, 2)

        assertEquals(2, results.size)
        assertEquals(0, results[0].exitCode)
        assertEquals(1, results[1].exitCode)
    }

    @Test
    fun parseBatchOutput_exitCodeZeroWithErrorIndicator_detectedAsError() {
        val output = "[ERROR] syntax error in query\n__FX#0:0"
        val results = LedgerExecutor.parseBatchOutput(output, 1)

        assertEquals(1, results.size)
        assertEquals(0, results[0].exitCode)
        assertTrue("Exit 0 but contains [ERROR] must be detected as error", results[0].hasErrorIndicator)
    }

    @Test
    fun parseBatchOutput_truncatedOutput_fillsMissingWithFailure() {
        val output = "__FX#0:0"
        val results = LedgerExecutor.parseBatchOutput(output, 3)

        assertEquals(3, results.size)
        assertEquals(0, results[0].exitCode)
        assertEquals(-1, results[1].exitCode)
        assertTrue(results[1].hasErrorIndicator)
        assertEquals(-1, results[2].exitCode)
        assertTrue(results[2].hasErrorIndicator)
    }

    @Test
    fun parseBatchOutput_commandOutputContainsMarkerSubstring_noFalseCollision() {
        // Command output itself contains "#0:0", collision-proof "__FX#" prefix must prevent false collision
        val output = "debug trace: thread #0:0 active\n__FX#0:0"
        val results = LedgerExecutor.parseBatchOutput(output, 1)

        assertEquals(1, results.size)
        assertEquals(0, results[0].exitCode)
        assertFalse(results[0].hasErrorIndicator)
        assertEquals("debug trace: thread #0:0 active", results[0].outputChunk)
    }

    @Test
    fun parseBatchOutput_fakeMarkerInStdout_selectsLastMarker() {
        // Command stdout echoes a fake marker "__FX#0:0" before printing more logs and the real marker
        val output = "echoing fake __FX#0:0 inside output\nsecond line\n__FX#0:0"
        val results = LedgerExecutor.parseBatchOutput(output, 1)

        assertEquals(1, results.size)
        assertEquals(0, results[0].exitCode)
        assertFalse(results[0].hasErrorIndicator)
        assertEquals("echoing fake __FX#0:0 inside output\nsecond line", results[0].outputChunk)
    }

    @Test
    fun parseBatchOutput_exitCodeZeroWithSecurityException_detectedAsError() {
        val output = "java.lang.SecurityException: Permission denial: writing to settings requires WRITE_SECURE_SETTINGS\n__FX#0:0"
        val results = LedgerExecutor.parseBatchOutput(output, 1)

        assertEquals(1, results.size)
        assertEquals(0, results[0].exitCode)
        assertTrue(results[0].hasErrorIndicator)
    }

    @Test
    fun parseBatchOutput_exitCodeZeroWithErrorAccessingProvider_detectedAsError() {
        val output = "Error while accessing provider:settings\n__FX#0:0"
        val results = LedgerExecutor.parseBatchOutput(output, 1)

        assertEquals(1, results.size)
        assertEquals(0, results[0].exitCode)
        assertTrue(results[0].hasErrorIndicator)
    }

    @Test
    fun parseBatchOutput_benignText_notDetectedAsError() {
        val output = "Broadcast completed successfully: result=0\n__FX#0:0"
        val results = LedgerExecutor.parseBatchOutput(output, 1)

        assertEquals(1, results.size)
        assertEquals(0, results[0].exitCode)
        assertFalse(results[0].hasErrorIndicator)
    }
}
