package com.framex.app.ui.screens.performance.components

import androidx.compose.ui.graphics.Color
import kotlin.math.sin

/**
 * Visual effects models and pure calculations for [SwipeToActivate].
 * Inspired by fluid particle tracks and multi-stop gradient illumination.
 */
object SliderEffects {

    data class SliderPalette(
        val startColor: Color,
        val midColor: Color,
        val endColor: Color,
        val thumbColor: Color = Color.White
    ) {
        val gradientColors: List<Color> = listOf(startColor, midColor, endColor)

        companion object {
            fun fromPrimary(primary: Color): SliderPalette {
                return SliderPalette(
                    startColor = primary.copy(alpha = 0.75f),
                    midColor = primary,
                    endColor = primary,
                    thumbColor = primary
                )
            }

            // Memory Boost: Fallback
            val MemoryBoost = SliderPalette(
                startColor = Color(0xFF0284C7),
                midColor = Color(0xFF0070F3),
                endColor = Color(0xFF00E5FF),
                thumbColor = Color(0xFF00E5FF)
            )

            // Network Ping: Emerald / Mint
            val NetworkPing = SliderPalette(
                startColor = Color(0xFF059669),
                midColor = Color(0xFF10B981),
                endColor = Color(0xFF34D399),
                thumbColor = Color(0xFF34D399)
            )

            // Reset Defaults: Amber / Warm Sun
            val ResetDefaults = SliderPalette(
                startColor = Color(0xFFD97706),
                midColor = Color(0xFFF59E0B),
                endColor = Color(0xFFFBBF24),
                thumbColor = Color(0xFFFBBF24)
            )
        }
    }

    data class ParticleSpec(
        val horizontalRatio: Float,
        val verticalRatio: Float,
        val radiusDp: Float,
        val speedPxPerSec: Float,
        val twinkleSpeed: Float,
        val phase: Float
    )

    // Pre-allocated particle distribution for the active track
    val DEFAULT_PARTICLES: List<ParticleSpec> = listOf(
        ParticleSpec(0.08f, 0.35f, 1.8f, 24f, 3.2f, 0.4f),
        ParticleSpec(0.18f, 0.70f, 2.2f, 32f, 2.7f, 1.2f),
        ParticleSpec(0.28f, 0.45f, 1.5f, 18f, 4.0f, 2.1f),
        ParticleSpec(0.38f, 0.25f, 2.5f, 38f, 2.1f, 3.0f),
        ParticleSpec(0.48f, 0.65f, 1.6f, 22f, 3.6f, 0.8f),
        ParticleSpec(0.58f, 0.40f, 2.0f, 30f, 2.9f, 2.5f),
        ParticleSpec(0.68f, 0.80f, 1.4f, 26f, 4.2f, 1.7f),
        ParticleSpec(0.78f, 0.30f, 2.3f, 35f, 2.4f, 0.1f),
        ParticleSpec(0.88f, 0.60f, 1.7f, 20f, 3.8f, 3.4f),
        ParticleSpec(0.96f, 0.45f, 2.1f, 28f, 3.1f, 1.9f)
    )

    fun calculateProgress(dragOffset: Float, maxDrag: Float): Float {
        if (maxDrag <= 0f) return 0f
        return (dragOffset / maxDrag).coerceIn(0f, 1f)
    }

    /**
     * Seamlessly interpolates the trail right edge from knob center (hidden behind knob at start)
     * all the way to 100% of the track container width when fully dragged/activated.
     */
    fun calculateTrailRightEdge(
        progress: Float,
        knobCenterPx: Float,
        trackWidthPx: Float
    ): Float {
        val clampedProgress = progress.coerceIn(0f, 1f)
        // Linear interpolation from knobCenterPx at 0% to the complete trackWidthPx at 100%
        return (knobCenterPx + (trackWidthPx - knobCenterPx) * clampedProgress).coerceAtMost(trackWidthPx)
    }

    fun computeParticleX(
        originX: Float,
        elapsedSec: Double,
        speed: Double,
        wrapWidth: Float
    ): Float {
        if (wrapWidth <= 0f) return originX
        val traveled = (elapsedSec * speed).toFloat()
        var current = (originX - traveled) % wrapWidth
        if (current < -10f) {
            current += wrapWidth
        }
        return current
    }

    fun computeTwinkleAlpha(
        phaseRad: Double,
        minAlpha: Float = 0.08f,
        maxAlpha: Float = 0.85f
    ): Float {
        val wave = 0.5f + 0.5f * sin(phaseRad).toFloat()
        return (minAlpha + (maxAlpha - minAlpha) * (wave * wave)).coerceIn(0f, 1f)
    }
}
