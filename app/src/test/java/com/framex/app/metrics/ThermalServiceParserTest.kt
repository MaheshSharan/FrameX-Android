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
    }
}
