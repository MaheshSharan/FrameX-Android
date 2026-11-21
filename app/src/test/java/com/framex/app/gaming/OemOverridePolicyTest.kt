package com.framex.app.gaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemOverridePolicyTest {

    @Test
    fun fixedPerformanceMode_isBlockedWhenDeviceIsFlaggedRisky() {
        assertFalse(OemOverridePolicy.isAllowed(GenericOverride.FIXED_PERFORMANCE_MODE, risky = true))
    }

    @Test
    fun fixedPerformanceMode_isAllowedWhenNotRisky() {
        assertTrue(OemOverridePolicy.isAllowed(GenericOverride.FIXED_PERFORMANCE_MODE, risky = false))
    }

    @Test
    fun otherOverrides_areAlwaysAllowedRegardlessOfRisk() {
        listOf(
            GenericOverride.THERMAL_THROTTLE,
            GenericOverride.REFRESH_RATE_LOCK,
            GenericOverride.TOUCH_RESPONSE
        ).forEach {
            assertTrue(OemOverridePolicy.isAllowed(it, risky = true))
            assertTrue(OemOverridePolicy.isAllowed(it, risky = false))
        }
    }
}
