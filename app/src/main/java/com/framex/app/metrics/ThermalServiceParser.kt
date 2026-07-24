package com.framex.app.metrics

/**
 * Pure parser for `dumpsys thermalservice` output.
 * No Android dependencies — unit-tested with fixture strings.
 *
 * Strategy (single pass, O(n) in dump length, O(1) sensor slots):
 * 1. Prefer the "Current temperatures from HAL:" section when present.
 * 2. Extract each `Temperature{…}` block; fields may appear in any order.
 * 3. Classify by mType first (AOSP Temperature types), then by mName keywords.
 */
internal object ThermalServiceParser {

    data class Result(
        val cpuC: Float? = null,
        val gpuC: Float? = null,
        val skinC: Float? = null,
        val npuC: Float? = null,
        val batteryC: Float? = null,
        val thermalStatus: Int = 0,
        /** Number of Temperature{…} blocks that yielded a value. */
        val entryCount: Int = 0,
        /** True if HAL is explicitly reported as not ready/disabled (e.g. HAL Ready: false). */
        val halNotReady: Boolean = false
    ) {
        val hasAnySensor: Boolean
            get() = cpuC != null || gpuC != null || skinC != null || npuC != null || batteryC != null
    }

    private val blockRegex = Regex("""Temperature\{([^}]*)\}""")
    private val valueRegex = Regex("""mValue\s*=\s*(-?[0-9.]+)""")
    private val typeRegex = Regex("""mType\s*=\s*(\d+)""")
    private val nameRegex = Regex("""mName\s*=\s*([^\s,}]+)""")
    private val statusRegex = Regex("""Thermal Status:\s*(\d+)""")

    /**
     * @return parsed sensors, or null when [output] is blank (caller treats as EmptyOutput).
     *         Non-blank dumps with zero matches return [Result] with [Result.entryCount] == 0
     *         (caller treats as ParseFailed).
     */
    fun parse(output: String): Result? {
        if (output.isBlank()) return null

        val halSection = output
            .substringAfter("Current temperatures from HAL:", missingDelimiterValue = "")
            .substringBefore("Current cooling devices")
        val section = if (halSection.isNotBlank()) halSection else output

        var cpu: Float? = null
        var gpu: Float? = null
        var skin: Float? = null
        var npu: Float? = null
        var battery: Float? = null
        var entryCount = 0

        for (match in blockRegex.findAll(section)) {
            val body = match.groupValues[1]
            val value = valueRegex.find(body)?.groupValues?.get(1)?.toFloatOrNull() ?: continue
            val type = typeRegex.find(body)?.groupValues?.get(1)?.toIntOrNull()
            val name = nameRegex.find(body)?.groupValues?.get(1).orEmpty()

            when (classify(type, name)) {
                SensorKind.CPU -> {
                    cpu = value
                    entryCount++
                }
                SensorKind.GPU -> {
                    gpu = value
                    entryCount++
                }
                SensorKind.SKIN -> {
                    skin = value
                    entryCount++
                }
                SensorKind.NPU -> {
                    npu = value
                    entryCount++
                }
                SensorKind.BATTERY -> {
                    battery = value
                    entryCount++
                }
                SensorKind.UNKNOWN -> {
                    // Count unknown Temperature blocks so ParseFailed is not falsely reported
                    // when the HAL only exposes types we do not map (still entryCount may be 0
                    // for mapping purposes — we only increment mapped sensors above).
                    // If nothing maps at all, entryCount stays 0 → ParseFailed at caller.
                }
            }
        }

        val thermalStatus = statusRegex.find(output)
            ?.groupValues?.get(1)
            ?.toIntOrNull()
            ?: 0

        val halNotReady = output.contains("HAL Ready: false", ignoreCase = true) || 
                          output.contains("mHalReady: false", ignoreCase = true) ||
                          output.contains("Thermal HAL is not ready", ignoreCase = true)

        return Result(
            cpuC = cpu,
            gpuC = gpu,
            skinC = skin,
            npuC = npu,
            batteryC = battery,
            thermalStatus = thermalStatus,
            entryCount = entryCount,
            halNotReady = halNotReady
        )
    }

    private enum class SensorKind { CPU, GPU, SKIN, NPU, BATTERY, UNKNOWN }

    /**
     * AOSP Temperature types: 0=CPU 1=GPU 2=BATTERY 3=SKIN 9=NPU.
     * Name fallback is case-insensitive and matches common OEM labels.
     */
    private fun classify(type: Int?, name: String): SensorKind {
        when (type) {
            0 -> return SensorKind.CPU
            1 -> return SensorKind.GPU
            2 -> return SensorKind.BATTERY
            3 -> return SensorKind.SKIN
            9 -> return SensorKind.NPU
        }

        val n = name.uppercase()
        return when {
            n.contains("SKIN") -> SensorKind.SKIN
            n.contains("GPU") || n.contains("GRAPHICS") -> SensorKind.GPU
            n.contains("NPU") || n.contains("TPU") || n.contains("APU") -> SensorKind.NPU
            n.contains("BATTERY") || n.contains("BATT") -> SensorKind.BATTERY
            // CPU last: many labels include "CPU" as a substring of longer names
            n.contains("CPU") || n.contains("SOC") || n.contains("CLUSTER") -> SensorKind.CPU
            else -> SensorKind.UNKNOWN
        }
    }
}
