package com.framex.app.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricModuleTest {

    @Test
    fun defaultOrder_containsAllKnownModulesWithoutDuplicates() {
        assertEquals(MetricModuleId.entries.toSet(), DEFAULT_METRIC_MODULE_ORDER.toSet())
        assertEquals(MetricModuleId.entries.size, DEFAULT_METRIC_MODULE_ORDER.size)
    }

    @Test
    fun resolveMetricModuleOrder_appendsNewModulesToCustomOrder() {
        // User had customized order from previous version with only fps and cpu
        val userCustomized = listOf("fps", "cpu")
        val resolved = resolveMetricModuleOrder(userCustomized)

        // Custom order preserved at the beginning
        assertEquals(MetricModuleId.FPS, resolved[0])
        assertEquals(MetricModuleId.CPU_FREQUENCY, resolved[1])

        // All missing modules appended
        assertEquals(DEFAULT_METRIC_MODULE_ORDER.size, resolved.size)
        assertTrue(resolved.contains(MetricModuleId.BATTERY_LEVEL))
        assertTrue(resolved.contains(MetricModuleId.CLOCK))
        assertTrue(resolved.contains(MetricModuleId.SESSION_TIMER))
    }

    @Test
    fun isIconShown_handlesDefaultAndCustomSets() {
        assertTrue(isIconShown(emptySet(), "fps"))
        assertTrue(isIconShown(setOf("fps", "cpu"), "fps"))
        assertFalse(isIconShown(setOf("fps", "cpu"), "ram"))
    }

    @Test
    fun formatSessionDuration_formatsCorrectly() {
        assertEquals("00:00", formatSessionDuration(0L))
        assertEquals("00:45", formatSessionDuration(45L))
        assertEquals("01:05", formatSessionDuration(65L))
        assertEquals("24:10", formatSessionDuration(1450L))
        assertEquals("1:00:00", formatSessionDuration(3600L))
        assertEquals("2:15:30", formatSessionDuration(8130L))
    }

    @Test
    fun metricValueFor_formatsBatteryClockAndSessionTimer() {
        val state = MetricsState(
            batteryLevel = 82,
            currentTime = "19:45",
            sessionElapsedSec = 1450L
        )

        assertEquals("82%", metricValueFor(MetricModuleId.BATTERY_LEVEL, state))
        assertEquals("19:45", metricValueFor(MetricModuleId.CLOCK, state))
        assertEquals("24:10", metricValueFor(MetricModuleId.SESSION_TIMER, state))
    }

    @Test
    fun metricValueFor_formatsUnknownBatteryGracefully() {
        val unknownState = MetricsState(batteryLevel = -1)
        assertEquals("--%", metricValueFor(MetricModuleId.BATTERY_LEVEL, unknownState))
    }

    @Test
    fun metricValueFor_thermalWarningSuppression() {
        val hotState = MetricsState(
            thermalCpuC = 78f,
            thermalStatus = 3 // SEVERE / HOT
        )

        // With CPU hot warning enabled -> includes HOT status label
        val withWarning = metricValueFor(MetricModuleId.THERMAL_MONITOR, hotState, cpuHotWarningEnabled = true)
        assertEquals("78°C HOT", withWarning)

        // With CPU hot warning disabled -> pure clean temperature
        val withoutWarning = metricValueFor(MetricModuleId.THERMAL_MONITOR, hotState, cpuHotWarningEnabled = false)
        assertEquals("78°C", withoutWarning)
    }
}

