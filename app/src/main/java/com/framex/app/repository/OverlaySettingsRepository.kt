package com.framex.app.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.framex.app.metrics.DEFAULT_METRIC_MODULE_ORDER
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlaySettingsRepository @Inject constructor(
    private val store: FrameXPreferenceStore
) {
    private val defaultOrderKeys: List<String> = DEFAULT_METRIC_MODULE_ORDER.map { it.storageKey }
    private val defaultEnabledModules = setOf("fps", "cpu", "ram", "temp", "battery_level")

    private val _overlayMode = MutableStateFlow(store.getString("overlay_mode", "Compact") ?: "Compact")
    val overlayMode: StateFlow<String> = _overlayMode.asStateFlow()

    private val _enabledModules = MutableStateFlow(
        store.getStringSet("enabled_modules", defaultEnabledModules)
    )
    val enabledModules: StateFlow<Set<String>> = _enabledModules.asStateFlow()

    private val _enabledModuleIcons = MutableStateFlow(
        store.getStringSet("enabled_module_icons", emptySet())
    )
    val enabledModuleIcons: StateFlow<Set<String>> = _enabledModuleIcons.asStateFlow()

    private val _cpuHotWarningEnabled = MutableStateFlow(
        store.getBoolean("cpu_hot_warning_enabled", true)
    )
    val cpuHotWarningEnabled: StateFlow<Boolean> = _cpuHotWarningEnabled.asStateFlow()

    private val _moduleOrder = MutableStateFlow(loadModuleOrder())
    val moduleOrder: StateFlow<List<String>> = _moduleOrder.asStateFlow()

    private val _overlayOpacity = MutableStateFlow(store.getFloat("overlay_opacity", 0.8f))
    val overlayOpacity: StateFlow<Float> = _overlayOpacity.asStateFlow()

    private val _overlayTextSize = MutableStateFlow(store.getInt("overlay_text_size", DEFAULT_TEXT_SIZE_INDEX))
    val overlayTextSize: StateFlow<Int> = _overlayTextSize.asStateFlow()

    private val _overlayScale = MutableStateFlow(store.getFloat("overlay_scale", 1.0f))
    val overlayScale: StateFlow<Float> = _overlayScale.asStateFlow()

    private val _overlayUseMonospace = MutableStateFlow(store.getBoolean("overlay_use_monospace", false))
    val overlayUseMonospace: StateFlow<Boolean> = _overlayUseMonospace.asStateFlow()

    private val _overlayColorIndex = MutableStateFlow(store.getInt("overlay_color_index", 0))
    val overlayColorIndex: StateFlow<Int> = _overlayColorIndex.asStateFlow()

    private val _overlayBgColorIndex = MutableStateFlow(store.getInt("overlay_bg_color_index", 0))
    val overlayBgColorIndex: StateFlow<Int> = _overlayBgColorIndex.asStateFlow()

    private val _overlayBorderColorIndex = MutableStateFlow(store.getInt("overlay_border_color_index", 0))
    val overlayBorderColorIndex: StateFlow<Int> = _overlayBorderColorIndex.asStateFlow()

    private val _overlayTextColorIndex = MutableStateFlow(store.getInt("overlay_text_color_index", 0))
    val overlayTextColorIndex: StateFlow<Int> = _overlayTextColorIndex.asStateFlow()

    init {
        store.observeKey("overlay_settings") { prefs ->
            prefs[stringPreferencesKey("overlay_mode")]?.let { _overlayMode.value = it }
            prefs[stringSetPreferencesKey("enabled_modules")]?.let { _enabledModules.value = it }
            prefs[stringSetPreferencesKey("enabled_module_icons")]?.let { _enabledModuleIcons.value = it }
            prefs[booleanPreferencesKey("cpu_hot_warning_enabled")]?.let { _cpuHotWarningEnabled.value = it }
            prefs[floatPreferencesKey("overlay_opacity")]?.let { _overlayOpacity.value = it }
            prefs[intPreferencesKey("overlay_text_size")]?.let { _overlayTextSize.value = it }
            prefs[floatPreferencesKey("overlay_scale")]?.let { _overlayScale.value = it }
            prefs[booleanPreferencesKey("overlay_use_monospace")]?.let { _overlayUseMonospace.value = it }
            prefs[intPreferencesKey("overlay_color_index")]?.let { _overlayColorIndex.value = it }
            prefs[intPreferencesKey("overlay_bg_color_index")]?.let { _overlayBgColorIndex.value = it }
            prefs[intPreferencesKey("overlay_border_color_index")]?.let { _overlayBorderColorIndex.value = it }
            prefs[intPreferencesKey("overlay_text_color_index")]?.let { _overlayTextColorIndex.value = it }
            prefs[stringPreferencesKey("module_order")]?.let { _moduleOrder.value = loadModuleOrder() }
        }
    }

    private fun loadModuleOrder(): List<String> {
        val raw = store.getString("module_order", null)
        val saved = raw?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val missing = defaultOrderKeys.filter { it !in saved }
        return if (saved.isEmpty()) defaultOrderKeys else saved + missing
    }

    fun setOverlayMode(mode: String) {
        _overlayMode.value = mode
        store.putString("overlay_mode", mode)
    }

    fun setEnabledModules(modules: Set<String>) {
        val copy = modules.toSet()
        _enabledModules.value = copy
        store.putStringSet("enabled_modules", copy)
    }

    fun toggleModule(module: String, enabled: Boolean) {
        val current = _enabledModules.value.toMutableSet()
        if (enabled) current.add(module) else current.remove(module)
        setEnabledModules(current)
    }

    fun setEnabledModuleIcons(icons: Set<String>) {
        val copy = icons.toSet()
        _enabledModuleIcons.value = copy
        store.putStringSet("enabled_module_icons", copy)
    }

    fun toggleModuleIcon(module: String, enabled: Boolean) {
        val current = _enabledModuleIcons.value.toMutableSet()
        if (enabled) current.add(module) else current.remove(module)
        setEnabledModuleIcons(current)
    }

    fun setCpuHotWarningEnabled(enabled: Boolean) {
        _cpuHotWarningEnabled.value = enabled
        store.putBoolean("cpu_hot_warning_enabled", enabled)
    }

    fun setModuleOrder(order: List<String>) {
        _moduleOrder.value = order
        store.putString("module_order", order.joinToString(","))
    }

    fun setOverlayOpacity(opacity: Float) {
        _overlayOpacity.value = opacity
        store.putFloat("overlay_opacity", opacity)
    }

    fun setOverlayTextSize(size: Int) {
        _overlayTextSize.value = size
        store.putInt("overlay_text_size", size)
    }

    fun setOverlayScale(scale: Float) {
        _overlayScale.value = scale
        store.putFloat("overlay_scale", scale)
    }

    fun setOverlayUseMonospace(useMonospace: Boolean) {
        _overlayUseMonospace.value = useMonospace
        store.putBoolean("overlay_use_monospace", useMonospace)
    }

    fun setOverlayColorIndex(index: Int) {
        _overlayColorIndex.value = index
        store.putInt("overlay_color_index", index)
    }

    fun setOverlayBgColorIndex(index: Int) {
        _overlayBgColorIndex.value = index
        store.putInt("overlay_bg_color_index", index)
    }

    fun setOverlayBorderColorIndex(index: Int) {
        _overlayBorderColorIndex.value = index
        store.putInt("overlay_border_color_index", index)
    }

    fun setOverlayTextColorIndex(index: Int) {
        _overlayTextColorIndex.value = index
        store.putInt("overlay_text_color_index", index)
    }

    fun resetOverlayToDefaults() {
        val defaultOrderStr = defaultOrderKeys.joinToString(",")
        _overlayMode.value = "Compact"
        _enabledModules.value = defaultEnabledModules
        _enabledModuleIcons.value = emptySet()
        _cpuHotWarningEnabled.value = true
        _moduleOrder.value = defaultOrderKeys
        _overlayOpacity.value = 0.8f
        _overlayTextSize.value = DEFAULT_TEXT_SIZE_INDEX
        _overlayScale.value = 1.0f
        _overlayUseMonospace.value = false
        _overlayColorIndex.value = 0
        _overlayBgColorIndex.value = 0
        _overlayBorderColorIndex.value = 0
        _overlayTextColorIndex.value = 0

        store.updateMemoryCache("overlay_mode", "Compact")
        store.updateMemoryCache("enabled_modules", defaultEnabledModules)
        store.updateMemoryCache("enabled_module_icons", emptySet<String>())
        store.updateMemoryCache("cpu_hot_warning_enabled", true)
        store.updateMemoryCache("module_order", defaultOrderStr)
        store.updateMemoryCache("overlay_opacity", 0.8f)
        store.updateMemoryCache("overlay_text_size", DEFAULT_TEXT_SIZE_INDEX)
        store.updateMemoryCache("overlay_scale", 1.0f)
        store.updateMemoryCache("overlay_use_monospace", false)
        store.updateMemoryCache("overlay_color_index", 0)
        store.updateMemoryCache("overlay_bg_color_index", 0)
        store.updateMemoryCache("overlay_border_color_index", 0)
        store.updateMemoryCache("overlay_text_color_index", 0)

        store.batchUpdate { prefs ->
            prefs[stringPreferencesKey("overlay_mode")] = "Compact"
            prefs[stringSetPreferencesKey("enabled_modules")] = defaultEnabledModules
            prefs[stringSetPreferencesKey("enabled_module_icons")] = emptySet()
            prefs[booleanPreferencesKey("cpu_hot_warning_enabled")] = true
            prefs[stringPreferencesKey("module_order")] = defaultOrderStr
            prefs[floatPreferencesKey("overlay_opacity")] = 0.8f
            prefs[intPreferencesKey("overlay_text_size")] = DEFAULT_TEXT_SIZE_INDEX
            prefs[floatPreferencesKey("overlay_scale")] = 1.0f
            prefs[booleanPreferencesKey("overlay_use_monospace")] = false
            prefs[intPreferencesKey("overlay_color_index")] = 0
            prefs[intPreferencesKey("overlay_bg_color_index")] = 0
            prefs[intPreferencesKey("overlay_border_color_index")] = 0
            prefs[intPreferencesKey("overlay_text_color_index")] = 0
        }
    }

    fun getOverlayPosition(isLandscape: Boolean): Pair<Int, Int> {
        val prefix = if (isLandscape) "land" else "port"
        val defaultX = if (isLandscape) 40 else 20
        val defaultY = if (isLandscape) 40 else 100
        val x = store.getInt("overlay_x_$prefix", defaultX)
        val y = store.getInt("overlay_y_$prefix", defaultY)
        return Pair(x, y)
    }

    fun setOverlayPosition(isLandscape: Boolean, x: Int, y: Int) {
        val prefix = if (isLandscape) "land" else "port"
        store.updateMemoryCache("overlay_x_$prefix", x)
        store.updateMemoryCache("overlay_y_$prefix", y)
        store.batchUpdate { prefs ->
            prefs[intPreferencesKey("overlay_x_$prefix")] = x
            prefs[intPreferencesKey("overlay_y_$prefix")] = y
        }
    }

    fun wasOverlayRunning(): Boolean = store.getBoolean("overlay_was_running", false)

    fun setOverlayWasRunning(running: Boolean) {
        store.putBoolean("overlay_was_running", running)
    }

    private companion object {
        /** Index into the 0.8x / 1.0x / 1.2x text scale options; 1 is the medium option. */
        const val DEFAULT_TEXT_SIZE_INDEX = 1
    }
}
