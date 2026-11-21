package com.framex.app.ui.screens.appearance

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Immutable representations for container background color options.
 */
enum class BackgroundOption(val color: Color, val labelRes: Int) {
    BLACK(Color.Black, com.framex.app.R.string.appearance_bg_black),
    NAVY(Color(0xFF0D1117), com.framex.app.R.string.appearance_bg_navy),
    CHARCOAL(Color(0xFF1C1C1E), com.framex.app.R.string.appearance_bg_charcoal),
    CLEAR(Color.Transparent, com.framex.app.R.string.appearance_bg_clear)
}

/**
 * Backdrops behind the preview card so overlay opacity can be judged against
 * dark, light, and busy in-game scenes. Selection is transient, never persisted.
 */
enum class PreviewBackground(val brush: Brush, val labelRes: Int) {
    DARK(Brush.linearGradient(listOf(Color(0xFF1E2024), Color(0xFF1E2024))), com.framex.app.R.string.appearance_preview_bg_dark),
    LIGHT(Brush.linearGradient(listOf(Color(0xFFF2F2F2), Color(0xFFF2F2F2))), com.framex.app.R.string.appearance_preview_bg_light),
    SUNSET(Brush.linearGradient(listOf(Color(0xFFFF9A3C), Color(0xFFD6336C), Color(0xFF3B1C6B))), com.framex.app.R.string.appearance_preview_bg_sunset),
    FOREST(Brush.linearGradient(listOf(Color(0xFF9BD97B), Color(0xFF2E7D32), Color(0xFF0B2E13))), com.framex.app.R.string.appearance_preview_bg_forest),
    BUSY(
        Brush.horizontalGradient(
            0.0f to Color(0xFFE53935), 0.25f to Color(0xFFE53935),
            0.25f to Color(0xFFFDD835), 0.5f to Color(0xFFFDD835),
            0.5f to Color(0xFF1E88E5), 0.75f to Color(0xFF1E88E5),
            0.75f to Color(0xFF43A047), 1.0f to Color(0xFF43A047)
        ),
        com.framex.app.R.string.appearance_preview_bg_busy
    )
}

/**
 * Immutable representations for container border options.
 */
enum class BorderStyleOption(val labelRes: Int) {
    ACCENT(com.framex.app.R.string.appearance_border_accent),
    NONE(com.framex.app.R.string.appearance_border_none),
    SUBTLE(com.framex.app.R.string.appearance_border_subtle),
    GHOST(com.framex.app.R.string.appearance_border_ghost)
}

/**
 * Immutable representations for font size options.
 */
enum class TextSizeOption(val targetScale: Float, val labelRes: Int) {
    SMALL(0.8f, com.framex.app.R.string.appearance_size_small),
    MEDIUM(1.0f, com.framex.app.R.string.appearance_size_medium),
    LARGE(1.2f, com.framex.app.R.string.appearance_size_large)
}

/**
 * Metric value text color options.
 */
enum class TextColorOption(val labelRes: Int) {
    WHITE(com.framex.app.R.string.appearance_value_color_white),
    ACCENT(com.framex.app.R.string.appearance_value_color_accent),
    SILVER(com.framex.app.R.string.appearance_value_color_silver),
    AUTO(com.framex.app.R.string.appearance_value_color_auto)
}

/**
 * Comprehensive UI State for Appearance configuration.
 */
@Immutable
data class AppearanceUiState(
    // Persisted baseline from SettingsRepository
    val savedOpacity: Float = 0.75f,
    val savedTextSize: Int = 1,
    val savedScale: Float = 1.0f,
    val savedUseMonospace: Boolean = false,
    val savedColorIndex: Int = 0,
    val savedBgColorIndex: Int = 0,
    val savedBorderColorIndex: Int = 0,
    val savedTextColorIndex: Int = 0,
    val savedCpuHotWarning: Boolean = false,

    // Active draft values being previewed and edited
    val opacity: Float = 0.75f,
    val textSize: Int = 1,
    val scale: Float = 1.0f,
    val useMonospace: Boolean = false,
    val colorIndex: Int = 0,
    val bgColorIndex: Int = 0,
    val borderColorIndex: Int = 0,
    val textColorIndex: Int = 0,
    val cpuHotWarning: Boolean = false,

    // Overlay layout context for live preview synchronization
    val mode: String = "compact",
    val enabledModules: Set<String> = emptySet(),
    val enabledModuleIcons: Set<String> = emptySet(),
    val moduleOrder: List<String> = emptyList(),

    // Preview-only backdrop index into PreviewBackground; not persisted.
    val previewBackgroundIndex: Int = 0
) {
    val hasChanges: Boolean
        get() = opacity != savedOpacity ||
            textSize != savedTextSize ||
            kotlin.math.abs(scale - savedScale) > 0.01f ||
            useMonospace != savedUseMonospace ||
            colorIndex != savedColorIndex ||
            bgColorIndex != savedBgColorIndex ||
            borderColorIndex != savedBorderColorIndex ||
            textColorIndex != savedTextColorIndex ||
            cpuHotWarning != savedCpuHotWarning
}

/**
 * Unidirectional UI events for Appearance screen interactions.
 */
sealed interface AppearanceUiEvent {
    data class SetOpacity(val opacity: Float) : AppearanceUiEvent
    data class SetScale(val scale: Float) : AppearanceUiEvent
    data class SetTextSize(val index: Int) : AppearanceUiEvent
    data class SetMonospace(val enabled: Boolean) : AppearanceUiEvent
    data class SetColorIndex(val index: Int) : AppearanceUiEvent
    data class SetBgColorIndex(val index: Int) : AppearanceUiEvent
    data class SetBorderColorIndex(val index: Int) : AppearanceUiEvent
    data class SetTextColorIndex(val index: Int) : AppearanceUiEvent
    data class SetCpuHotWarning(val enabled: Boolean) : AppearanceUiEvent
    data object CyclePreviewBackground : AppearanceUiEvent
    data object SaveChanges : AppearanceUiEvent
    data object ResetChanges : AppearanceUiEvent
}

/**
 * One-shot side-effects dispatched from ViewModel to UI.
 */
sealed interface AppearanceUiEffect {
    data class ShowToast(val messageResId: Int) : AppearanceUiEffect
}
