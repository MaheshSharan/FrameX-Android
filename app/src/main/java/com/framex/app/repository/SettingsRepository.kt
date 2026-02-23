package com.framex.app.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("framex_settings", Context.MODE_PRIVATE)

    private val _overlayMode = MutableStateFlow(prefs.getString(KEY_OVERLAY_MODE, "Compact") ?: "Compact")
    val overlayMode: StateFlow<String> = _overlayMode.asStateFlow()

    // Default: FPS only. Other monitors start only when user enables them in overlay config.
    private val _enabledModules = MutableStateFlow(
        prefs.getStringSet(KEY_ENABLED_MODULES, setOf("fps")) ?: setOf("fps")
    )
    val enabledModules: StateFlow<Set<String>> = _enabledModules.asStateFlow()

    private val _overlayOpacity = MutableStateFlow(prefs.getFloat(KEY_OVERLAY_OPACITY, 0.75f))
    val overlayOpacity: StateFlow<Float> = _overlayOpacity.asStateFlow()

    private val _overlayTextSize = MutableStateFlow(prefs.getInt(KEY_OVERLAY_TEXT_SIZE, 1))
    val overlayTextSize: StateFlow<Int> = _overlayTextSize.asStateFlow()

    private val _overlayUseMonospace = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY_USE_MONOSPACE, false))
    val overlayUseMonospace: StateFlow<Boolean> = _overlayUseMonospace.asStateFlow()

    private val _overlayColorIndex = MutableStateFlow(prefs.getInt(KEY_OVERLAY_COLOR_INDEX, 0))
    val overlayColorIndex: StateFlow<Int> = _overlayColorIndex.asStateFlow()

    // Container background color index: 0=Black, 1=Dark Navy, 2=Charcoal, 3=Transparent
    private val _overlayBgColorIndex = MutableStateFlow(prefs.getInt(KEY_OVERLAY_BG_COLOR_INDEX, 0))
    val overlayBgColorIndex: StateFlow<Int> = _overlayBgColorIndex.asStateFlow()

    // Container border style index: 0=Accent, 1=None, 2=White Subtle, 3=Ghost
    private val _overlayBorderColorIndex = MutableStateFlow(prefs.getInt(KEY_OVERLAY_BORDER_COLOR_INDEX, 0))
    val overlayBorderColorIndex: StateFlow<Int> = _overlayBorderColorIndex.asStateFlow()

    // Metric value text color index: 0=White, 1=Accent, 2=Silver, 3=Auto (FPS-based coloring)
    private val _overlayTextColorIndex = MutableStateFlow(prefs.getInt(KEY_OVERLAY_TEXT_COLOR_INDEX, 0))
    val overlayTextColorIndex: StateFlow<Int> = _overlayTextColorIndex.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_IS_ONBOARDING_COMPLETED, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    // Overlay window position — persists the last dragged location across service restarts.
    private val _overlayX = MutableStateFlow(prefs.getInt(KEY_OVERLAY_X, 100))
    val overlayX: StateFlow<Int> = _overlayX.asStateFlow()

    private val _overlayY = MutableStateFlow(prefs.getInt(KEY_OVERLAY_Y, 100))
    val overlayY: StateFlow<Int> = _overlayY.asStateFlow()

    fun setOverlayMode(mode: String) {
        prefs.edit().putString(KEY_OVERLAY_MODE, mode).apply()
        _overlayMode.value = mode
    }

    fun setEnabledModules(modules: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED_MODULES, modules).apply()
        _enabledModules.value = modules
    }

    fun setOverlayOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_OVERLAY_OPACITY, opacity).apply()
        _overlayOpacity.value = opacity
    }

    fun setOverlayTextSize(size: Int) {
        prefs.edit().putInt(KEY_OVERLAY_TEXT_SIZE, size).apply()
        _overlayTextSize.value = size
    }

    fun setOverlayUseMonospace(useMonospace: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_USE_MONOSPACE, useMonospace).apply()
        _overlayUseMonospace.value = useMonospace
    }

    fun setOverlayColorIndex(index: Int) {
        prefs.edit().putInt(KEY_OVERLAY_COLOR_INDEX, index).apply()
        _overlayColorIndex.value = index
    }

    fun setOverlayBgColorIndex(index: Int) {
        prefs.edit().putInt(KEY_OVERLAY_BG_COLOR_INDEX, index).apply()
        _overlayBgColorIndex.value = index
    }

    fun setOverlayBorderColorIndex(index: Int) {
        prefs.edit().putInt(KEY_OVERLAY_BORDER_COLOR_INDEX, index).apply()
        _overlayBorderColorIndex.value = index
    }

    fun setOverlayTextColorIndex(index: Int) {
        prefs.edit().putInt(KEY_OVERLAY_TEXT_COLOR_INDEX, index).apply()
        _overlayTextColorIndex.value = index
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ONBOARDING_COMPLETED, completed).apply()
        _isOnboardingCompleted.value = completed
    }

    fun setOverlayPosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
        _overlayX.value = x
        _overlayY.value = y
    }

    companion object {
        private const val KEY_OVERLAY_MODE = "overlay_mode"
        private const val KEY_ENABLED_MODULES = "enabled_modules"
        private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        private const val KEY_OVERLAY_TEXT_SIZE = "overlay_text_size"
        private const val KEY_OVERLAY_USE_MONOSPACE = "overlay_use_monospace"
        private const val KEY_OVERLAY_COLOR_INDEX = "overlay_color_index"
        private const val KEY_OVERLAY_BG_COLOR_INDEX = "overlay_bg_color_index"
        private const val KEY_OVERLAY_BORDER_COLOR_INDEX = "overlay_border_color_index"
        private const val KEY_OVERLAY_TEXT_COLOR_INDEX = "overlay_text_color_index"
        private const val KEY_IS_ONBOARDING_COMPLETED = "is_onboarding_completed"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
    }
}
