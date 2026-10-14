package com.framex.app.ui.screens.performance.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeToActivateMathTest {

    @Test
    fun normalizeProgress_withinBounds_returnsAccurateRatio() {
        val maxDrag = 200f
        assertEquals(0.0f, SliderEffects.calculateProgress(0f, maxDrag), 0.001f)
        assertEquals(0.5f, SliderEffects.calculateProgress(100f, maxDrag), 0.001f)
        assertEquals(1.0f, SliderEffects.calculateProgress(200f, maxDrag), 0.001f)
    }

    @Test
    fun normalizeProgress_outOfBounds_clampsCorrectly() {
        val maxDrag = 200f
        assertEquals(0.0f, SliderEffects.calculateProgress(-50f, maxDrag), 0.001f)
        assertEquals(1.0f, SliderEffects.calculateProgress(250f, maxDrag), 0.001f)
        assertEquals(0.0f, SliderEffects.calculateProgress(100f, 0f), 0.001f)
    }

    @Test
    fun particleWrapping_advancesAndCycles() {
        val width = 300f
        val wrapWidth = width + 20f

        val xInitial = SliderEffects.computeParticleX(
            originX = 150f,
            elapsedSec = 0.0,
            speed = 30.0,
            wrapWidth = wrapWidth
        )
        assertEquals(150f, xInitial, 0.01f)

        val xAdvanced = SliderEffects.computeParticleX(
            originX = 150f,
            elapsedSec = 2.0,
            speed = 30.0,
            wrapWidth = wrapWidth
        )
        // 150 - (2.0 * 30.0) = 90
        assertEquals(90f, xAdvanced, 0.01f)

        val xWrapped = SliderEffects.computeParticleX(
            originX = 10f,
            elapsedSec = 1.0,
            speed = 40.0,
            wrapWidth = wrapWidth
        )
        // 10 - 40 = -30 (< -10 -> wrapped)
        assertTrue("Wrapped x should remain in visible span", xWrapped >= 0f && xWrapped <= wrapWidth)
    }

    @Test
    fun particleTwinkleAlpha_staysWithinNormalizedAlphaRange() {
        val minAlpha = 0.08f
        val maxAlpha = 0.85f

        for (angleDeg in 0..360 step 15) {
            val radians = Math.toRadians(angleDeg.toDouble())
            val alpha = SliderEffects.computeTwinkleAlpha(radians, minAlpha, maxAlpha)
            assertTrue("Alpha $alpha must be >= minAlpha", alpha >= minAlpha - 0.001f)
            assertTrue("Alpha $alpha must be <= maxAlpha", alpha <= maxAlpha + 0.001f)
        }
    }

    @Test
    fun calculateTrailRightEdge_interpolatesSmoothlyAndReachesEnd() {
        val knobCenter = 30f
        val trackWidth = 300f

        // Progress 0%: trail stops at knob center
        val edge0 = SliderEffects.calculateTrailRightEdge(0.0f, knobCenter, trackWidth)
        assertEquals(knobCenter, edge0, 0.001f)

        // Progress 50%: halfway between knob center and full width
        val edge50 = SliderEffects.calculateTrailRightEdge(0.5f, knobCenter, trackWidth)
        assertEquals(knobCenter + (trackWidth - knobCenter) * 0.5f, edge50, 0.001f)

        // Progress 100%: reaches exactly trackWidth
        val edge100 = SliderEffects.calculateTrailRightEdge(1.0f, knobCenter, trackWidth)
        assertEquals(trackWidth, edge100, 0.001f)
    }
}
