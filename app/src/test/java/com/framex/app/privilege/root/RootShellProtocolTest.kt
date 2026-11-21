package com.framex.app.privilege.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellProtocolTest {

    private val marker = "__FX_abc_1"

    @Test
    fun markerOnItsOwnLineIsParsed() {
        val hit = RootShellProtocol.matchMarker("$marker:0", marker)
        assertEquals(MarkerHit("", 0), hit)
    }

    @Test
    fun markerAfterOutputWithoutNewlineKeepsTheOutput() {
        val hit = RootShellProtocol.matchMarker("abc$marker:3", marker)
        assertEquals(MarkerHit("abc", 3), hit)
    }

    @Test
    fun otherSessionMarkerIsNotMatched() {
        assertNull(RootShellProtocol.matchMarker("__FX_other_1:0", marker))
    }

    @Test
    fun markerWithoutNumericExitCodeIsRejected() {
        assertNull(RootShellProtocol.matchMarker("$marker:oops", marker))
    }

    @Test
    fun payloadIsolatesStdinAndPutsMarkerAfterCommand() {
        val payload = RootShellProtocol.payload("cat /proc/stat", marker)
        assertTrue(payload.contains("</dev/null 2>&1"))
        assertTrue(payload.indexOf("cat /proc/stat") < payload.indexOf(marker))
        assertTrue(payload.endsWith("\n"))
    }
}
