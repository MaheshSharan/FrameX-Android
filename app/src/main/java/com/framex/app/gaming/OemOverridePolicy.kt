package com.framex.app.gaming

/** System-wide overrides that the vendor-neutral optimizer may apply. */
enum class GenericOverride {
    THERMAL_THROTTLE,
    FIXED_PERFORMANCE_MODE,
    REFRESH_RATE_LOCK,
    TOUCH_RESPONSE
}

/**
 * Decides which generic overrides are safe on a given firmware. Only the fixed performance
 * mode is blocked on Vivo/iQOO today, where it can lock the display. The other overrides keep
 * their current behavior there; widening this table is a product decision, so it lives in one
 * place and is covered by a unit test.
 */
object OemOverridePolicy {
    fun isAllowed(override: GenericOverride, isVivoOrIqoo: Boolean): Boolean = when (override) {
        GenericOverride.FIXED_PERFORMANCE_MODE -> !isVivoOrIqoo
        GenericOverride.THERMAL_THROTTLE,
        GenericOverride.REFRESH_RATE_LOCK,
        GenericOverride.TOUCH_RESPONSE -> true
    }
}
