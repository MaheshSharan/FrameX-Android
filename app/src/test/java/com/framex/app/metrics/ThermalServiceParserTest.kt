package com.framex.app.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalServiceParserTest {

    @Test
    fun blankOutputReturnsNull() {
        assertNull(ThermalServiceParser.parse(""))
        assertNull(ThermalServiceParser.parse("   \n"))
    }

    @Test
    fun parsesCanonicalAospHalBlock() {
        val dump = """
            IsStatusOverride: false
            Thermal Status: 2
            Cached temperatures:
            Current temperatures from HAL:
            Temperature{mValue=62.895, mType=0, mName=CPU, mStatus=0}
            Temperature{mValue=55.1, mType=1, mName=GPU, mStatus=0}
            Temperature{mValue=40.6, mType=3, mName=SKIN, mStatus=2}
            Temperature{mValue=33.0, mType=2, mName=BATTERY, mStatus=0}
            Temperature{mValue=48.2, mType=9, mName=NPU, mStatus=0}
            Current cooling devices from HAL:
            CoolingDevice{mValue=1, mType=0, mName=thermal-cpufreq}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)
        assertNotNull(result)
        assertEquals(62.895f, result!!.cpuC!!, 0.001f)
        assertEquals(55.1f, result.gpuC!!, 0.001f)
        assertEquals(40.6f, result.skinC!!, 0.001f)
        assertEquals(33.0f, result.batteryC!!, 0.001f)
        assertEquals(48.2f, result.npuC!!, 0.001f)
        assertEquals(2, result.thermalStatus)
        assertEquals(5, result.entryCount)
        assertTrue(result.hasAnySensor)
    }

    @Test
    fun toleratesReorderedFields() {
        val dump = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mName=CPU, mStatus=0, mType=0, mValue=51.25}
            Temperature{mType=1, mValue=44.0, mName=GPU, mStatus=0}
            Current cooling devices from HAL:
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertEquals(51.25f, result.cpuC!!, 0.001f)
        assertEquals(44.0f, result.gpuC!!, 0.001f)
        assertEquals(2, result.entryCount)
    }

    @Test
    fun classifiesByNameWhenTypeUnknown() {
        val dump = """
            Thermal Status: 1
            Current temperatures from HAL:
            Temperature{mValue=50.0, mType=99, mName=big-CPU-cluster, mStatus=0}
            Temperature{mValue=41.0, mType=99, mName=skin0, mStatus=0}
            Temperature{mValue=39.0, mType=99, mName=gpu_therm, mStatus=0}
            Current cooling devices from HAL:
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertEquals(50.0f, result.cpuC!!, 0.001f)
        assertEquals(41.0f, result.skinC!!, 0.001f)
        assertEquals(39.0f, result.gpuC!!, 0.001f)
        assertEquals(1, result.thermalStatus)
    }

    @Test
    fun fallsBackToFullDumpWhenHalSectionMissing() {
        val dump = """
            Thermal Status: 0
            Temperature{mValue=47.0, mType=0, mName=CPU, mStatus=0}
            Temperature{mValue=38.0, mType=3, mName=SKIN, mStatus=0}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertEquals(47.0f, result.cpuC!!, 0.001f)
        assertEquals(38.0f, result.skinC!!, 0.001f)
        assertTrue(result.hasAnySensor)
    }

    @Test
    fun garbageNonBlankYieldsZeroEntries() {
        val result = ThermalServiceParser.parse("hello world\nno temperatures here")
        assertNotNull(result)
        assertEquals(0, result!!.entryCount)
        assertFalse(result.hasAnySensor)
        assertNull(result.cpuC)
    }

    @Test
    fun spacesAroundEqualsAreAccepted() {
        val dump = """
            Thermal Status: 3
            Temperature{mValue = 60.0, mType = 0, mName = CPU, mStatus = 0}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertEquals(60.0f, result.cpuC!!, 0.001f)
        assertEquals(3, result.thermalStatus)
        // Strictly separated: high thermalStatus does not automatically mean CPU cooling device is active
        assertFalse(result.isCpuThrottling)
    }

    @Test
    fun detectsThrottlingFromCpuCoolingDevicesSpecifically() {
        val dump = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=55.0, mType=0, mName=CPU, mStatus=0}
            Current cooling devices from HAL:
            CoolingDevice{mValue=2, mType=0, mName=thermal-cpufreq}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertEquals(55.0f, result.cpuC!!, 0.001f)
        assertTrue(result.isCpuThrottling)
    }

    @Test
    fun ignoresNonCpuCoolingDevices() {
        val dump = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=55.0, mType=0, mName=CPU, mStatus=0}
            Current cooling devices from HAL:
            CoolingDevice{mValue=3, mType=0, mName=thermal-fan}
            CoolingDevice{mValue=1, mType=1, mName=battery-cooler}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertEquals(55.0f, result.cpuC!!, 0.001f)
        assertFalse(result.isCpuThrottling)
    }

    @Test
    fun preservesAllRawSensorsAndRanksCandidates() {
        val dump = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=42.0, mType=0, mName=cpu0, mStatus=0}
            Temperature{mValue=44.0, mType=0, mName=cpu1, mStatus=0}
            Temperature{mValue=45.0, mType=0, mName=cpu2, mStatus=0}
            Temperature{mValue=46.0, mType=0, mName=cpu3, mStatus=0}
            Temperature{mValue=99.0, mType=0, mName=cpu_hotspot_junction, mStatus=0}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        // 1. All raw sensors preserved
        assertEquals(5, result.rawSensors.size)
        assertEquals(5, result.cpuCandidates.size)

        // 2. Candidate ranking: cpu0..cpu3 are CORE_SENSOR (pri 3), hotspot is HOTSPOT_FALLBACK (pri 4)
        val hotspotCandidate = result.cpuCandidates.first { it.sensor.name == "cpu_hotspot_junction" }
        assertEquals(ThermalServiceParser.CpuCandidateRank.HOTSPOT_FALLBACK, hotspotCandidate.rank)

        // 3. Representative CPU temperature is the core average (44.25°C), hotspot outlier is safely excluded
        assertEquals(44.25f, result.cpuC!!, 0.001f)
        assertEquals(5, result.entryCount)
        assertFalse(result.isCpuThrottling)
    }

    @Test
    fun prefersPackageSensorOverCoreAndHotspot() {
        val dump = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=43.0, mType=0, mName=cpu0, mStatus=0}
            Temperature{mValue=45.0, mType=0, mName=cpu1, mStatus=0}
            Temperature{mValue=44.0, mType=0, mName=SOC, mStatus=0}
            Temperature{mValue=95.0, mType=0, mName=cpu-hotspot, mStatus=0}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        // SOC is PACKAGE_OR_SOC (priority 1), selected directly
        assertEquals(44.0f, result.cpuC!!, 0.001f)
    }

    @Test
    fun classifiesAdrenoAndMaliGpuSensors() {
        val dump = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=48.5, mType=99, mName=kgsl-3d0, mStatus=0}
            Temperature{mValue=41.2, mType=99, mName=shell_temp, mStatus=0}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertEquals(48.5f, result.gpuC!!, 0.001f)
        assertEquals(41.2f, result.skinC!!, 0.001f)
    }

    @Test
    fun strictGpuMatchingRejectsUnrelatedNames() {
        val dump = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=35.0, mType=99, mName=3d_audio_dsp, mStatus=0}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        // Unrelated '3d' in audio DSP is NOT classified as GPU
        assertNull(result.gpuC)
    }

    @Test
    fun classifiesMediaTekAndQualcommClusterSensors() {
        val dump = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=53.0, mType=99, mName=mtkts_soc, mStatus=0}
            Temperature{mValue=49.0, mType=99, mName=tsens_tz_sensor12, mStatus=0}
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        // mtkts_soc is PACKAGE_OR_SOC (pri 1)
        assertEquals(53.0f, result.cpuC!!, 0.001f)
        assertEquals(2, result.entryCount)
    }

    @Test
    fun parsesSysfsFallbackForDevicesWithoutHal() {
        // Reproduces KNOWN_LIMITATIONS.md Snapdragon 720G / Tab S6 Lite (#19) sysfs dump
        val dump = """
            cpu-1-0-usr:42000
            cpu-1-1-usr:44000
            gpu-usr:48000
            skin-msm-therm:36000
            battery:32000
            cooling: thermal-cpufreq 0
            sys.thermal.status: 0
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertEquals(43.0f, result.cpuC!!, 0.001f) // Average of cpu-1-0 (42) and cpu-1-1 (44)
        assertEquals(48.0f, result.gpuC!!, 0.001f)
        assertEquals(36.0f, result.skinC!!, 0.001f)
        assertEquals(32.0f, result.batteryC!!, 0.001f)
        assertEquals(5, result.entryCount)
        assertFalse(result.isCpuThrottling)
    }

    @Test
    fun handlesHalNotReadyFlagCorrectly() {
        val dump = """
            HAL Ready: false
            Thermal Status: 0
        """.trimIndent()

        val result = ThermalServiceParser.parse(dump)!!
        assertTrue(result.halNotReady)
        assertEquals(0, result.entryCount)
        assertFalse(result.hasAnySensor)
    }
}

