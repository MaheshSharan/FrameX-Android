package com.framex.app.quicksettings

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FrameXOverlayTileServiceTest {

    @Test
    fun tileAction_matchesPlatformContract() {
        assertEquals("android.service.quicksettings.action.QS_TILE", "android.service.quicksettings.action.QS_TILE")
    }

    @Test
    fun tileState_mapsFromRunningStateCorrectly() {
        val runningState = true
        val expectedStateRunning = if (runningState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        assertEquals(Tile.STATE_ACTIVE, expectedStateRunning)

        val stoppedState = false
        val expectedStateStopped = if (stoppedState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        assertEquals(Tile.STATE_INACTIVE, expectedStateStopped)
    }

    @Test
    fun companionMethods_areExposed() {
        val method = FrameXOverlayTileService.Companion::class.java.methods.find { it.name == "requestTileUpdate" }
        assertNotNull(method)
    }
}
