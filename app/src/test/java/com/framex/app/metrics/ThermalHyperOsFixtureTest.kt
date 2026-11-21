package com.framex.app.metrics

import com.framex.app.privilege.scripts.ThermalScripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures from issue #120 (POCO X5 Pro, HyperOS 2.0). The dumpsys text and zone type names
 * are the reporter's; the zone temperatures are invented, in the kernel's millidegree format.
 */
class ThermalHyperOsFixtureTest {

    private val emptyHalDump = """
        IsStatusOverride: false
        Thermal Status: 0
        Cached temperatures:
        HAL Ready: true
        HAL connection:
        	ThermalHAL 2.0 connected: yes
        Current temperatures from HAL:
        Current cooling devices from HAL:
        	CoolingDevice{mValue=3, mType=2, mName=thermal-cpufreq-4}
        	CoolingDevice{mValue=0, mType=2, mName=cpu-isolate4}
        	CoolingDevice{mValue=0, mType=5, mName=cdsp_hw}
        Temperature static thresholds from HAL:
    """.trimIndent()

    private val sysfsDump = """
        modem-skin-usr:31000
        msm-skin-therm-usr:39500
        quiet_therm:38000
        cpu-0-0-usr:44000
        cpu-1-7-usr:52000
        cpuss-0-usr:46000
        cpuss-1-usr:48000
        cpu_therm:35000
        gpuss-0-usr:43000
        gpuss-1-usr:45000
        nspss-0-usr:41000
        batt_therm:38000
        vbat:4200000
        pa_therm0:33000
        cooling: thermal-cpufreq-4 3
    """.trimIndent()

    @Test
    fun cooling_devices_alone_are_not_sensor_data() {
        assertFalse(ThermalScripts.dumpHasSensorData(emptyHalDump))
        assertTrue(ThermalScripts.dumpHasSensorData("Temperature{mValue=40.0, mType=0, mName=CPU}"))
    }

    @Test
    fun empty_hal_section_is_unsupported_not_parse_failed() {
        val parsed = checkNotNull(ThermalServiceParser.parse(emptyHalDump))
        assertEquals(0, parsed.entryCount)
        assertTrue(parsed.halReportedNoSensors)
        assertFalse(parsed.halNotReady)
        assertTrue(parsed.isCpuThrottling)
    }

    @Test
    fun sysfs_zones_map_to_cpu_gpu_skin_npu() {
        val parsed = checkNotNull(ThermalServiceParser.parse(sysfsDump))
        assertFalse(parsed.halReportedNoSensors)
        assertEquals(47f, parsed.cpuC)
        assertEquals(43f, parsed.gpuC)
        assertEquals(39.5f, parsed.skinC)
        assertEquals(41f, parsed.npuC)
        assertEquals(38f, parsed.batteryC)
    }

    @Test
    fun modem_sensors_never_become_skin_or_cpu() {
        val parsed = checkNotNull(ThermalServiceParser.parse("modem-skin-usr:31000\nmdmss-0-usr:50000"))
        assertNull(parsed.skinC)
        assertNull(parsed.cpuC)
    }
}
