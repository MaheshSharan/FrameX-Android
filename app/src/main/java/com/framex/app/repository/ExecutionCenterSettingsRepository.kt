package com.framex.app.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The on/off switches for the eight Common/Generic Execution Center commands. */
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

    private val _auditLoggingEnabled = MutableStateFlow(
        store.getBoolean("audit_logging_enabled", false)
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

    fun setAuditLoggingEnabled(enabled: Boolean) {
        _auditLoggingEnabled.value = enabled
        store.putBoolean("audit_logging_enabled", enabled)
    }
}
