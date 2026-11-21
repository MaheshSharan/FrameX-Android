package com.framex.app.gaming.catalog

import com.framex.app.repository.SettingsRepository

/** Reads the Execution Center switches. */
fun interface ToggleState {
    fun isOn(toggle: Toggle): Boolean
}

/** [ToggleState] over the Execution Center settings. */
class SettingsToggleState(private val settings: SettingsRepository) : ToggleState {

    override fun isOn(toggle: Toggle): Boolean = when (toggle) {
        Toggle.ALWAYS -> true
        Toggle.RAM_CACHE_PRE_TRIM -> settings.ramCachePreTrimEnabled.value
        Toggle.THERMAL_OVERRIDE -> settings.disableThermalThrottling.value
        Toggle.CPU_PRIORITY_LOCK -> settings.cpuPriorityLock.value
        Toggle.NETWORK_FIREWALL -> settings.networkFirewall.value
        Toggle.PHANTOM_PROC_KILLER -> settings.disablePhantomProcKiller.value
        Toggle.FIXED_PERFORMANCE_MODE -> settings.fixedPerformanceMode.value
        Toggle.REFRESH_RATE_LOCK -> settings.refreshRateLock.value
        Toggle.TOUCH_BOOST -> settings.touchBoost.value
    }
}

/** Capability lookup the capability probe exposes; kept here so the catalog package does not
 *  need to depend on the privilege package to describe its own filtering contract. */
fun interface CapableLookup {
    fun isCapable(commandId: String): Boolean
}
