package com.framex.app.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutionCenterSettingsRepository @Inject constructor(
    private val store: FrameXPreferenceStore
) {
    private val _disableThermalThrottling = MutableStateFlow(
        store.getBoolean("exec_disable_thermal_throttling", false)
    )
    val disableThermalThrottling: StateFlow<Boolean> = _disableThermalThrottling.asStateFlow()

    private val _ramCachePreTrimEnabled = MutableStateFlow(
        store.getBoolean("exec_ram_cache_pre_trim", true)
    )
    val ramCachePreTrimEnabled: StateFlow<Boolean> = _ramCachePreTrimEnabled.asStateFlow()

    private val _disablePhantomProcKiller = MutableStateFlow(
        store.getBoolean("exec_disable_phantom_proc_killer", true)
    )
    val disablePhantomProcKiller: StateFlow<Boolean> = _disablePhantomProcKiller.asStateFlow()

    private val _cpuPriorityLock = MutableStateFlow(
        store.getBoolean("exec_cpu_priority_lock", true)
    )
    val cpuPriorityLock: StateFlow<Boolean> = _cpuPriorityLock.asStateFlow()

    private val _fixedPerformanceMode = MutableStateFlow(
        store.getBoolean("exec_fixed_performance_mode", false)
    )
    val fixedPerformanceMode: StateFlow<Boolean> = _fixedPerformanceMode.asStateFlow()

    private val _networkFirewall = MutableStateFlow(
        store.getBoolean("exec_network_firewall", false)
    )
    val networkFirewall: StateFlow<Boolean> = _networkFirewall.asStateFlow()

    private val _refreshRateLock = MutableStateFlow(
        store.getBoolean("exec_refresh_rate_lock", false)
    )
    val refreshRateLock: StateFlow<Boolean> = _refreshRateLock.asStateFlow()

    private val _touchBoost = MutableStateFlow(
        store.getBoolean("exec_touch_boost", false)
    )
    val touchBoost: StateFlow<Boolean> = _touchBoost.asStateFlow()

    private val _vivoMonsterMode = MutableStateFlow(
        store.getBoolean("exec_vivo_monster_mode", false)
    )
    val vivoMonsterMode: StateFlow<Boolean> = _vivoMonsterMode.asStateFlow()

    private val _vivoVipThread = MutableStateFlow(
        store.getBoolean("exec_vivo_vip_thread", false)
    )
    val vivoVipThread: StateFlow<Boolean> = _vivoVipThread.asStateFlow()

    private val _vivoGameHandshake = MutableStateFlow(
        store.getBoolean("exec_vivo_game_handshake", false)
    )
    val vivoGameHandshake: StateFlow<Boolean> = _vivoGameHandshake.asStateFlow()

    private val _vivoGyroPromotion = MutableStateFlow(
        store.getBoolean("exec_vivo_gyro_promotion", false)
    )
    val vivoGyroPromotion: StateFlow<Boolean> = _vivoGyroPromotion.asStateFlow()

    private val _vivoTouchOptimization = MutableStateFlow(
        store.getBoolean("exec_vivo_touch_optimization", false)
    )
    val vivoTouchOptimization: StateFlow<Boolean> = _vivoTouchOptimization.asStateFlow()

    private val _vivo144FpsUnlock = MutableStateFlow(
        store.getBoolean("exec_vivo_144_fps_unlock", false)
    )
    val vivo144FpsUnlock: StateFlow<Boolean> = _vivo144FpsUnlock.asStateFlow()

    private val _vivoMaintenancePulse = MutableStateFlow(
        store.getBoolean("exec_vivo_maintenance_pulse", false)
    )
    val vivoMaintenancePulse: StateFlow<Boolean> = _vivoMaintenancePulse.asStateFlow()

    private val _vivoPulseGamePlusMode = MutableStateFlow(
        store.getBoolean("exec_vivo_pulse_game_plus_mode", false)
    )
    val vivoPulseGamePlusMode: StateFlow<Boolean> = _vivoPulseGamePlusMode.asStateFlow()

    private val _vivoPulseStandardPromotion = MutableStateFlow(
        store.getBoolean("exec_vivo_pulse_standard_promotion", false)
    )
    val vivoPulseStandardPromotion: StateFlow<Boolean> = _vivoPulseStandardPromotion.asStateFlow()

    private val _vivoPulseSceneMoreFps = MutableStateFlow(
        store.getBoolean("exec_vivo_pulse_scene_more_fps", false)
    )
    val vivoPulseSceneMoreFps: StateFlow<Boolean> = _vivoPulseSceneMoreFps.asStateFlow()

    private val _auditLoggingEnabled = MutableStateFlow(
        store.getBoolean("audit_logging_enabled", true)
    )
    val auditLoggingEnabled: StateFlow<Boolean> = _auditLoggingEnabled.asStateFlow()

    init {
        store.observeKey("execution_center_settings") { prefs ->
            prefs[booleanPreferencesKey("exec_disable_thermal_throttling")]?.let { _disableThermalThrottling.value = it }
            prefs[booleanPreferencesKey("exec_ram_cache_pre_trim")]?.let { _ramCachePreTrimEnabled.value = it }
            prefs[booleanPreferencesKey("exec_disable_phantom_proc_killer")]?.let { _disablePhantomProcKiller.value = it }
            prefs[booleanPreferencesKey("exec_cpu_priority_lock")]?.let { _cpuPriorityLock.value = it }
            prefs[booleanPreferencesKey("exec_fixed_performance_mode")]?.let { _fixedPerformanceMode.value = it }
            prefs[booleanPreferencesKey("exec_network_firewall")]?.let { _networkFirewall.value = it }
            prefs[booleanPreferencesKey("exec_refresh_rate_lock")]?.let { _refreshRateLock.value = it }
            prefs[booleanPreferencesKey("exec_touch_boost")]?.let { _touchBoost.value = it }
            prefs[booleanPreferencesKey("exec_vivo_monster_mode")]?.let { _vivoMonsterMode.value = it }
            prefs[booleanPreferencesKey("exec_vivo_vip_thread")]?.let { _vivoVipThread.value = it }
            prefs[booleanPreferencesKey("exec_vivo_game_handshake")]?.let { _vivoGameHandshake.value = it }
            prefs[booleanPreferencesKey("exec_vivo_gyro_promotion")]?.let { _vivoGyroPromotion.value = it }
            prefs[booleanPreferencesKey("exec_vivo_touch_optimization")]?.let { _vivoTouchOptimization.value = it }
            prefs[booleanPreferencesKey("exec_vivo_144_fps_unlock")]?.let { _vivo144FpsUnlock.value = it }
            prefs[booleanPreferencesKey("exec_vivo_maintenance_pulse")]?.let { _vivoMaintenancePulse.value = it }
            prefs[booleanPreferencesKey("exec_vivo_pulse_game_plus_mode")]?.let { _vivoPulseGamePlusMode.value = it }
            prefs[booleanPreferencesKey("exec_vivo_pulse_standard_promotion")]?.let { _vivoPulseStandardPromotion.value = it }
            prefs[booleanPreferencesKey("exec_vivo_pulse_scene_more_fps")]?.let { _vivoPulseSceneMoreFps.value = it }
            prefs[booleanPreferencesKey("audit_logging_enabled")]?.let { _auditLoggingEnabled.value = it }
        }
    }

    fun setDisableThermalThrottling(enabled: Boolean) {
        _disableThermalThrottling.value = enabled
        store.putBoolean("exec_disable_thermal_throttling", enabled)
    }

    fun setRamCachePreTrimEnabled(enabled: Boolean) {
        _ramCachePreTrimEnabled.value = enabled
        store.putBoolean("exec_ram_cache_pre_trim", enabled)
    }

    fun setDisablePhantomProcKiller(enabled: Boolean) {
        _disablePhantomProcKiller.value = enabled
        store.putBoolean("exec_disable_phantom_proc_killer", enabled)
    }

    fun setCpuPriorityLock(enabled: Boolean) {
        _cpuPriorityLock.value = enabled
        store.putBoolean("exec_cpu_priority_lock", enabled)
    }

    fun setFixedPerformanceMode(enabled: Boolean) {
        _fixedPerformanceMode.value = enabled
        store.putBoolean("exec_fixed_performance_mode", enabled)
    }

    fun setNetworkFirewall(enabled: Boolean) {
        _networkFirewall.value = enabled
        store.putBoolean("exec_network_firewall", enabled)
    }

    fun setRefreshRateLock(enabled: Boolean) {
        _refreshRateLock.value = enabled
        store.putBoolean("exec_refresh_rate_lock", enabled)
    }

    fun setTouchBoost(enabled: Boolean) {
        _touchBoost.value = enabled
        store.putBoolean("exec_touch_boost", enabled)
    }

    fun setVivoMonsterMode(enabled: Boolean) {
        _vivoMonsterMode.value = enabled
        store.putBoolean("exec_vivo_monster_mode", enabled)
    }

    fun setVivoVipThread(enabled: Boolean) {
        _vivoVipThread.value = enabled
        store.putBoolean("exec_vivo_vip_thread", enabled)
    }

    fun setVivoGameHandshake(enabled: Boolean) {
        _vivoGameHandshake.value = enabled
        store.putBoolean("exec_vivo_game_handshake", enabled)
    }

    fun setVivoGyroPromotion(enabled: Boolean) {
        _vivoGyroPromotion.value = enabled
        store.putBoolean("exec_vivo_gyro_promotion", enabled)
    }

    fun setVivoTouchOptimization(enabled: Boolean) {
        _vivoTouchOptimization.value = enabled
        store.putBoolean("exec_vivo_touch_optimization", enabled)
    }

    fun setVivo144FpsUnlock(enabled: Boolean) {
        _vivo144FpsUnlock.value = enabled
        store.putBoolean("exec_vivo_144_fps_unlock", enabled)
    }

    fun setVivoMaintenancePulse(enabled: Boolean) {
        _vivoMaintenancePulse.value = enabled
        store.putBoolean("exec_vivo_maintenance_pulse", enabled)
    }

    fun setVivoPulseGamePlusMode(enabled: Boolean) {
        _vivoPulseGamePlusMode.value = enabled
        store.putBoolean("exec_vivo_pulse_game_plus_mode", enabled)
    }

    fun setVivoPulseStandardPromotion(enabled: Boolean) {
        _vivoPulseStandardPromotion.value = enabled
        store.putBoolean("exec_vivo_pulse_standard_promotion", enabled)
    }

    fun setVivoPulseSceneMoreFps(enabled: Boolean) {
        _vivoPulseSceneMoreFps.value = enabled
        store.putBoolean("exec_vivo_pulse_scene_more_fps", enabled)
    }

    fun setAuditLoggingEnabled(enabled: Boolean) {
        _auditLoggingEnabled.value = enabled
        store.putBoolean("audit_logging_enabled", enabled)
    }
}
