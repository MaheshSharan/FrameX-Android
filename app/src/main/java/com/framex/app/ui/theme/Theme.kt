package com.framex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryRed,
    secondary = PrimaryDarkRed,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = BackgroundLight,
    onSecondary = BackgroundLight,
    onBackground = BackgroundLight,
    onSurface = BackgroundLight,
    surfaceVariant = SurfaceLight
)

@Composable
fun FrameXTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
