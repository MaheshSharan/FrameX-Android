package com.framex.app.gaming

/** System-wide overrides that the Execution Center may apply. */
enum class GenericOverride {
    THERMAL_THROTTLE,
    FIXED_PERFORMANCE_MODE,
    REFRESH_RATE_LOCK,
    TOUCH_RESPONSE
}

/**
 * Decides which overrides are safe on the current device. Only the fixed performance mode is
 * blocked, and only when [com.framex.app.privilege.DeviceCapabilityProbe] flags the device as
 * risky (the display mode looks already locked, or toggling it does not settle) rather than on
 * a Vivo/iQOO brand match — the underlying risk (an OEM locking the display to 60Hz when fixed
 * performance mode is forced) is device-specific, not vendor-specific. The other overrides keep
 * their current behavior; widening this table is a product decision, so it lives in one place
 * and is covered by a unit test.
 */
object OemOverridePolicy {
    fun isAllowed(override: GenericOverride, risky: Boolean): Boolean = when (override) {
        GenericOverride.FIXED_PERFORMANCE_MODE -> !risky
        GenericOverride.THERMAL_THROTTLE,
        GenericOverride.REFRESH_RATE_LOCK,
        GenericOverride.TOUCH_RESPONSE -> true
    }
}
