package com.framex.app.gaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemOverridePolicyTest {

    @Test
    fun fixedPerformanceMode_isBlockedOnVivoAndIqoo() {
        assertFalse(OemOverridePolicy.isAllowed(GenericOverride.FIXED_PERFORMANCE_MODE, isVivoOrIqoo = true))
    }

    @Test
    fun fixedPerformanceMode_isAllowedElsewhere() {
        assertTrue(OemOverridePolicy.isAllowed(GenericOverride.FIXED_PERFORMANCE_MODE, isVivoOrIqoo = false))
    }

    @Test
    fun otherOverrides_keepTheirCurrentBehaviorOnVivo() {
        listOf(
            GenericOverride.THERMAL_THROTTLE,
            GenericOverride.REFRESH_RATE_LOCK,
            GenericOverride.TOUCH_RESPONSE
        ).forEach { assertTrue(OemOverridePolicy.isAllowed(it, isVivoOrIqoo = true)) }
    }
}
