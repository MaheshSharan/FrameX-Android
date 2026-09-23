package com.framex.app.ui.screens.performance

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.ledger.LedgerSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceStateTest {

    @Test
    fun defaultPerformanceUiStateHasExpectedDefaults() {
        val state = PerformanceUiState()
        assertEquals(GamingModeState.Idle, state.gamingState)
        assertFalse(state.isShizukuAvailable)
        assertFalse(state.hasShizukuPermission)
        assertTrue(state.whitelist.isEmpty())
        assertTrue(state.launcherGames.isEmpty())
        assertFalse(state.fixedPerformanceMode)
        assertFalse(state.deepFreezeEnabled)
        assertNull(state.bannerMessage)
    }

    @Test
    fun activeGamingSessionModelHoldsSummary() {
        val summary = LedgerSummary(
            stages = emptyList(),
            totalApplied = 5,
            totalFailed = 0,
            totalSkipped = 0,
            totalOps = 5
        )
        val session = ActiveGamingSession(
            title = "Gaming Mode Active",
            isVivoDevice = true,
            activeGamePackage = "com.test.game",
            suspendedAppsCount = 3,
            summary = summary
        )
        assertEquals("Gaming Mode Active", session.title)
        assertTrue(session.isVivoDevice)
        assertEquals("com.test.game", session.activeGamePackage)
        assertEquals(3, session.suspendedAppsCount)
        assertEquals(5, session.summary.totalApplied)
        assertEquals(5, session.summary.totalOps)
    }

    @Test
    fun appIconCachePutAndGet() {
        val dummyBitmap = object : ImageBitmap {
            override val width: Int get() = 1
            override val height: Int get() = 1
            override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
            override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888
            override val hasAlpha: Boolean get() = true
            override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) {}
            override fun prepareToDraw() {}
        }
        val pkg = "com.example.game"
        AppIconCache.put(pkg, dummyBitmap)
        assertEquals(dummyBitmap, AppIconCache.get(pkg))

        AppIconCache.clear()
        assertNull(AppIconCache.get(pkg))
    }
}
