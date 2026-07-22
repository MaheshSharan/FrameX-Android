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

    // Display order of metric modules (both enabled and disabled), as module storage keys.
    // Stored as a single delimited string rather than a StringSet — SharedPreferences'
    // StringSet has no guaranteed iteration order, which would silently discard any
    // ordering the user set up via drag-and-drop in Overlay Config. Empty when the user
    // has never customized ordering; callers fall back to the canonical default order
    // (see MetricModule.kt's resolveMetricModuleOrder).
    private val _moduleOrder = MutableStateFlow(loadModuleOrder())
    val moduleOrder: StateFlow<List<String>> = _moduleOrder.asStateFlow()

    private fun loadModuleOrder(): List<String> =
        prefs.getString(KEY_MODULE_ORDER, null)
            ?.split(MODULE_ORDER_DELIMITER)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private val _overlayOpacity = MutableStateFlow(prefs.getFloat(KEY_OVERLAY_OPACITY, 0.75f))
    val overlayOpacity: StateFlow<Float> = _overlayOpacity.asStateFlow()

    private val _overlayTextSize = MutableStateFlow(prefs.getInt(KEY_OVERLAY_TEXT_SIZE, 1))
    val overlayTextSize: StateFlow<Int> = _overlayTextSize.asStateFlow()

    private val _overlayScale = MutableStateFlow(
        if (prefs.contains(KEY_OVERLAY_SCALE)) {
            prefs.getFloat(KEY_OVERLAY_SCALE, 1.0f)
        } else {
            when (prefs.getInt(KEY_OVERLAY_TEXT_SIZE, 1)) {
                0 -> 0.8f
                2 -> 1.2f
                else -> 1.0f
            }
        }
    )
    val overlayScale: StateFlow<Float> = _overlayScale.asStateFlow()

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
    private val _overlayX = MutableStateFlow(prefs.getInt(KEY_OVERLAY_PORTRAIT_X, -1))
    val overlayX: StateFlow<Int> = _overlayX.asStateFlow()

    private val _overlayY = MutableStateFlow(prefs.getInt(KEY_OVERLAY_PORTRAIT_Y, -1))
    val overlayY: StateFlow<Int> = _overlayY.asStateFlow()

    fun setOverlayMode(mode: String) {
        prefs.edit().putString(KEY_OVERLAY_MODE, mode).apply()
        _overlayMode.value = mode
    }

    fun setEnabledModules(modules: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED_MODULES, modules).apply()
        _enabledModules.value = modules
    }

    fun setModuleOrder(order: List<String>) {
        prefs.edit().putString(KEY_MODULE_ORDER, order.joinToString(MODULE_ORDER_DELIMITER)).apply()
        _moduleOrder.value = order
    }

    fun setOverlayOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_OVERLAY_OPACITY, opacity).apply()
        _overlayOpacity.value = opacity
    }

    fun setOverlayTextSize(size: Int) {
        val targetScale = when (size) {
            0 -> 0.8f
            2 -> 1.2f
            else -> 1.0f
        }
        setOverlayScale(targetScale)
    }

    fun setOverlayScale(scale: Float) {
        prefs.edit().putFloat(KEY_OVERLAY_SCALE, scale).apply()
        _overlayScale.value = scale
        val matchedIndex = when {
            kotlin.math.abs(scale - 0.8f) < 0.05f -> 0
            kotlin.math.abs(scale - 1.0f) < 0.05f -> 1
            kotlin.math.abs(scale - 1.2f) < 0.05f -> 2
            else -> -1
        }
        if (matchedIndex != -1) {
            prefs.edit().putInt(KEY_OVERLAY_TEXT_SIZE, matchedIndex).apply()
            _overlayTextSize.value = matchedIndex
        }
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

    fun getOverlayPosition(isLandscape: Boolean): Pair<Int, Int> {
        val keyX = if (isLandscape) KEY_OVERLAY_LANDSCAPE_X else KEY_OVERLAY_PORTRAIT_X
        val keyY = if (isLandscape) KEY_OVERLAY_LANDSCAPE_Y else KEY_OVERLAY_PORTRAIT_Y
        return Pair(prefs.getInt(keyX, -1), prefs.getInt(keyY, -1))
    }

    fun setOverlayPosition(isLandscape: Boolean, x: Int, y: Int) {
        val keyX = if (isLandscape) KEY_OVERLAY_LANDSCAPE_X else KEY_OVERLAY_PORTRAIT_X
        val keyY = if (isLandscape) KEY_OVERLAY_LANDSCAPE_Y else KEY_OVERLAY_PORTRAIT_Y
        prefs.edit().putInt(keyX, x).putInt(keyY, y).apply()
        _overlayX.value = x
        _overlayY.value = y
    }

    // ---- Gaming Mode --------------------------------------------------------

    /** Persisted whitelist — package names the user has opted out of killing. */
    private val _gamingModeWhitelist = MutableStateFlow(
        prefs.getStringSet(KEY_GAMING_WHITELIST, emptySet()) ?: emptySet()
    )
    val gamingModeWhitelist: StateFlow<Set<String>> = _gamingModeWhitelist.asStateFlow()

    fun setGamingModeWhitelist(pkgs: Set<String>) {
        prefs.edit().putStringSet(KEY_GAMING_WHITELIST, pkgs).apply()
        _gamingModeWhitelist.value = pkgs
    }

    fun toggleGamingWhitelistApp(packageName: String) {
        val current = _gamingModeWhitelist.value.toMutableSet()
        if (current.contains(packageName)) current.remove(packageName) else current.add(packageName)
        setGamingModeWhitelist(current)
    }

    /** Whether Gaming Mode was active when the app was last killed. Used for recovery. */
    fun setGamingModeActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_GAMING_MODE_ACTIVE, active).apply()
    }

    fun isGamingModeActive(): Boolean = prefs.getBoolean(KEY_GAMING_MODE_ACTIVE, false)

    /**
     * The set of packages whose AppOps we changed during the last Gaming Mode
     * session.  Stored so disableGamingMode() only resets what it actually changed.
     */
    fun setGamingAffectedPackages(pkgs: Set<String>) {
        prefs.edit().putStringSet(KEY_GAMING_AFFECTED_PKGS, pkgs).apply()
    }

    fun getGamingAffectedPackages(): Set<String> =
        prefs.getStringSet(KEY_GAMING_AFFECTED_PKGS, emptySet()) ?: emptySet()

    // ---- Game Launcher ------------------------------------------------------

    private val _launcherGames = MutableStateFlow(
        prefs.getStringSet(KEY_LAUNCHER_GAMES, emptySet()) ?: emptySet()
    )
    val launcherGames: StateFlow<Set<String>> = _launcherGames.asStateFlow()

    fun setLauncherGames(pkgs: Set<String>) {
        prefs.edit().putStringSet(KEY_LAUNCHER_GAMES, pkgs).apply()
        _launcherGames.value = pkgs
    }

    fun toggleLauncherGame(packageName: String) {
        val current = _launcherGames.value.toMutableSet()
        val whitelist = _gamingModeWhitelist.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
            whitelist.add(packageName)
            setGamingModeWhitelist(whitelist)
        }
        setLauncherGames(current)
    }

    // Per-game configs
    fun getGameConfigBoostRam(pkg: String): Boolean = prefs.getBoolean("game_config_${pkg}_boost_ram", true)
    fun setGameConfigBoostRam(pkg: String, enabled: Boolean) = prefs.edit().putBoolean("game_config_${pkg}_boost_ram", enabled).apply()

    fun getGameConfigDisableBrightness(pkg: String): Boolean = prefs.getBoolean("game_config_${pkg}_disable_brightness", true)
    fun setGameConfigDisableBrightness(pkg: String, enabled: Boolean) = prefs.edit().putBoolean("game_config_${pkg}_disable_brightness", enabled).apply()

    fun getGameConfigDisableRotate(pkg: String): Boolean = prefs.getBoolean("game_config_${pkg}_disable_rotate", true)
    fun setGameConfigDisableRotate(pkg: String, enabled: Boolean) = prefs.edit().putBoolean("game_config_${pkg}_disable_rotate", enabled).apply()

    fun getGameConfigRingtoneVol(pkg: String): Int = prefs.getInt("game_config_${pkg}_ringtone_vol", 50)
    fun setGameConfigRingtoneVol(pkg: String, vol: Int) = prefs.edit().putInt("game_config_${pkg}_ringtone_vol", vol).apply()

    // ---- Esports Optimizations ----------------------------------------------

    private val _vivoOptEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIVO_OPT_ENABLED, false))
    val vivoOptEnabled: StateFlow<Boolean> = _vivoOptEnabled.asStateFlow()

    private val _cpuPriorityLock = MutableStateFlow(prefs.getBoolean(KEY_CPU_PRIORITY_LOCK, true))
    val cpuPriorityLock: StateFlow<Boolean> = _cpuPriorityLock.asStateFlow()

    private val _networkFirewall = MutableStateFlow(prefs.getBoolean(KEY_NETWORK_FIREWALL, true))
    val networkFirewall: StateFlow<Boolean> = _networkFirewall.asStateFlow()

    private val _refreshRateLock = MutableStateFlow(prefs.getBoolean(KEY_REFRESH_RATE_LOCK, true))
    val refreshRateLock: StateFlow<Boolean> = _refreshRateLock.asStateFlow()

    private val _touchBoost = MutableStateFlow(prefs.getBoolean(KEY_TOUCH_BOOST, true))
    val touchBoost: StateFlow<Boolean> = _touchBoost.asStateFlow()

    private val _framePacingOverlay = MutableStateFlow(prefs.getBoolean(KEY_FRAME_PACING_OVERLAY, false))
    val framePacingOverlay: StateFlow<Boolean> = _framePacingOverlay.asStateFlow()

    fun setVivoOptEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIVO_OPT_ENABLED, enabled).apply()
        _vivoOptEnabled.value = enabled
    }

    fun setCpuPriorityLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CPU_PRIORITY_LOCK, enabled).apply()
        _cpuPriorityLock.value = enabled
    }

    fun setNetworkFirewall(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NETWORK_FIREWALL, enabled).apply()
        _networkFirewall.value = enabled
    }

    fun setRefreshRateLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REFRESH_RATE_LOCK, enabled).apply()
        _refreshRateLock.value = enabled
    }

    fun setTouchBoost(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TOUCH_BOOST, enabled).apply()
        _touchBoost.value = enabled
    }

    fun setFramePacingOverlay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FRAME_PACING_OVERLAY, enabled).apply()
        _framePacingOverlay.value = enabled
    }

    private val _autoUpdateCheckEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, true))
    val autoUpdateCheckEnabled: StateFlow<Boolean> = _autoUpdateCheckEnabled.asStateFlow()

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, enabled).apply()
        _autoUpdateCheckEnabled.value = enabled
    }

    private val _overlayWasRunning = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY_WAS_RUNNING, false))
    val overlayWasRunning: StateFlow<Boolean> = _overlayWasRunning.asStateFlow()

    fun setOverlayWasRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_WAS_RUNNING, running).apply()
        _overlayWasRunning.value = running
    }

    companion object {
        private const val KEY_OVERLAY_MODE = "overlay_mode"
        private const val KEY_ENABLED_MODULES = "enabled_modules"
        private const val KEY_MODULE_ORDER = "module_order"
        private const val MODULE_ORDER_DELIMITER = ","
        private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        private const val KEY_OVERLAY_TEXT_SIZE = "overlay_text_size"
        private const val KEY_OVERLAY_SCALE = "overlay_scale"
        private const val KEY_OVERLAY_USE_MONOSPACE = "overlay_use_monospace"
        private const val KEY_OVERLAY_COLOR_INDEX = "overlay_color_index"
        private const val KEY_OVERLAY_BG_COLOR_INDEX = "overlay_bg_color_index"
        private const val KEY_OVERLAY_BORDER_COLOR_INDEX = "overlay_border_color_index"
        private const val KEY_OVERLAY_TEXT_COLOR_INDEX = "overlay_text_color_index"
        private const val KEY_IS_ONBOARDING_COMPLETED = "is_onboarding_completed"
        private const val KEY_OVERLAY_PORTRAIT_X = "overlay_x"
        private const val KEY_OVERLAY_PORTRAIT_Y = "overlay_y"
        private const val KEY_OVERLAY_LANDSCAPE_X = "overlay_landscape_x"
        private const val KEY_OVERLAY_LANDSCAPE_Y = "overlay_landscape_y"
        private const val KEY_GAMING_WHITELIST = "gaming_mode_whitelist"
        private const val KEY_GAMING_MODE_ACTIVE = "gaming_mode_active"
        private const val KEY_GAMING_AFFECTED_PKGS = "gaming_affected_pkgs"
        private const val KEY_LAUNCHER_GAMES = "launcher_games"
        private const val KEY_VIVO_OPT_ENABLED = "esports_vivo_opt_enabled"
        private const val KEY_CPU_PRIORITY_LOCK = "esports_cpu_priority_lock"
        private const val KEY_NETWORK_FIREWALL = "esports_network_firewall"
        private const val KEY_REFRESH_RATE_LOCK = "esports_refresh_rate_lock"
        private const val KEY_TOUCH_BOOST = "esports_touch_boost"
        private const val KEY_FRAME_PACING_OVERLAY = "esports_frame_pacing_overlay"
        private const val KEY_AUTO_UPDATE_CHECK_ENABLED = "auto_update_check_enabled"
        private const val KEY_OVERLAY_WAS_RUNNING = "overlay_was_running"
    }
}
