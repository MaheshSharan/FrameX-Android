package com.framex.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design system tokens for FrameX.
 * Standardizes spacing, shapes, motion, borders, and accessibility conventions across the app.
 */
object FrameXSpacing {
    val None: Dp = 0.dp
    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Standard: Dp = 16.dp
    val Large: Dp = 20.dp
    val XLarge: Dp = 24.dp
    val XXLarge: Dp = 32.dp
    val Huge: Dp = 40.dp
}

object FrameXShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val Card = RoundedCornerShape(20.dp)
    val CardLarge = RoundedCornerShape(24.dp)
    val Pill = CircleShape
}

object FrameXBorders {
    val SubtleStroke: Color = Color.White.copy(alpha = 0.05f)
    val CardStroke: Color = Color.White.copy(alpha = 0.08f)
    val FocusedStroke: Color = Color.White.copy(alpha = 0.15f)
    val ActiveBorderWidth: Dp = 1.dp
    val HighlightBorderWidth: Dp = 1.5.dp
}

object FrameXMotion {
    const val DurationFast: Int = 150
    const val DurationMedium: Int = 300
    const val DurationSlow: Int = 500

    val StandardEasing: Easing = FastOutSlowInEasing
    val DecelerateEasing: Easing = LinearOutSlowInEasing
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun <T> responsiveSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

object FrameXAccessibility {
    val MinTouchTarget: Dp = 48.dp
    val StandardRowHeight: Dp = 56.dp
}
