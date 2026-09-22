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

    data class RawSensor(
        val name: String,
        val type: Int?,
        val valueC: Float,
        val kind: SensorKind
    )

    enum class CpuCandidateRank(val priority: Int) {
        /** Package or SoC aggregate sensor (highest priority, e.g. SOC, CPU-PACKAGE, CPUSS, MTKTS_SOC, TS_SOC). */
        PACKAGE_OR_SOC(1),
        /** Clustered cores (e.g. CPU-CLUSTER, GOLD, SILVER, PRIME). */
        CLUSTER_AVERAGE(2),
        /** Individual core sensor (e.g. cpu0..cpu7, TSENS_TZ_SENSOR). */
        CORE_SENSOR(3),
        /** Fallback hotspot or junction diode (e.g. HOTSPOT, JUNCTION, MAX_TEMP). */
        HOTSPOT_FALLBACK(4)
    }

    data class CpuCandidate(
        val sensor: RawSensor,
        val rank: CpuCandidateRank
    )

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
        val halNotReady: Boolean = false,
        /** True strictly if CPU/cpufreq cooling devices report active throttling. */
        val isCpuThrottling: Boolean = false,
        /** All raw sensor readings preserved for diagnostics. */
        val rawSensors: List<RawSensor> = emptyList(),
        /** Classified and ranked CPU candidates. */
        val cpuCandidates: List<CpuCandidate> = emptyList()
    ) {
        val hasAnySensor: Boolean
            get() = cpuC != null || gpuC != null || skinC != null || npuC != null || batteryC != null

        // Backwards compatibility alias
        val isThrottling: Boolean
            get() = isCpuThrottling
    }

    private val blockRegex = Regex("""Temperature\{([^}]*mValue[^}]*)\}""")
    private val valueRegex = Regex("""mValue\s*=\s*(-?[0-9.]+)""")
    private val typeRegex = Regex("""mType\s*=\s*(\d+)""")
    private val nameRegex = Regex("""mName\s*=\s*([^\s,}]+)""")
    private val statusRegex = Regex("""Thermal Status:\s*(\d+)""")
    private val propStatusRegex = Regex("""(?:sys\.thermal\.status|thermal_status)[:=]\s*(\d+)""")
    private val coolingBlockRegex = Regex("""CoolingDevice\{([^}]*)\}""")
    private val coolingValueRegex = Regex("""mValue\s*=\s*(\d+)""")
    private val coolingTypeRegex = Regex("""mType\s*=\s*(\d+)""")
    private val coolingNameRegex = Regex("""mName\s*=\s*([^\s,}]+)""")

    /**
     * @return parsed sensors, or null when [output] is blank (caller treats as EmptyOutput).
     *         Non-blank dumps with zero matches return [Result] with [Result.entryCount] == 0
     *         (caller treats as ParseFailed).
     */
    fun parse(output: String): Result? {
        if (output.isBlank()) return null

        val halSection = if (output.contains("Current temperatures from HAL:")) {
            output.substringAfter("Current temperatures from HAL:")
                .substringBefore("Current cooling devices")
                .substringBefore("Temperature static thresholds")
        } else {
            ""
        }
        val section = if (halSection.isNotBlank()) halSection else output

        val rawSensors = mutableListOf<RawSensor>()
        val cpuCandidates = mutableListOf<CpuCandidate>()
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

            if (value !in 0f..120f) continue

            val kind = classify(type, name)
            val raw = RawSensor(name = name, type = type, valueC = value, kind = kind)
            rawSensors.add(raw)

            when (kind) {
                SensorKind.CPU -> {
                    val rank = rankCpuCandidate(name)
                    cpuCandidates.add(CpuCandidate(raw, rank))
                    entryCount++
                }
                SensorKind.GPU -> {
                    if (gpu == null) gpu = value
                    entryCount++
                }
                SensorKind.SKIN -> {
                    if (skin == null) skin = value
                    entryCount++
                }
                SensorKind.NPU -> {
                    if (npu == null) npu = value
                    entryCount++
                }
                SensorKind.BATTERY -> {
                    if (battery == null) battery = value
                    entryCount++
                }
                SensorKind.UNKNOWN -> {}
            }
        }

        var isCpuThrottling = false

        // Parse cooling devices from HAL dump specifically for CPU cooling
        for (match in coolingBlockRegex.findAll(output)) {
            val body = match.groupValues[1]
            val cVal = coolingValueRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val cType = coolingTypeRegex.find(body)?.groupValues?.get(1)?.toIntOrNull()
            val cName = coolingNameRegex.find(body)?.groupValues?.get(1).orEmpty()
            if (cVal > 0 && isCpuCoolingDevice(cType, cName)) {
                isCpuThrottling = true
                break
            }
        }

        // Also check cooling_device lines from sysfs specifically for CPU/cpufreq
        if (!isCpuThrottling) {
            val coolingLine = output.lines().firstOrNull { line ->
                val l = line.lowercase()
                (l.contains("cooling") || l.contains("cooling_device")) &&
                    isCpuCoolingDevice(null, line) &&
                    line.trim().split(Regex("""[:\s]+""")).lastOrNull()?.toIntOrNull()?.let { it > 0 } == true
            }
            if (coolingLine != null) {
                isCpuThrottling = true
            }
        }

        var cpu: Float? = selectRepresentativeCpuTemp(cpuCandidates)

        if (entryCount == 0) {
            val fallback = parseFallbackZones(output)
            if (fallback.entryCount > 0) {
                cpu = fallback.cpuC
                gpu = fallback.gpuC
                skin = fallback.skinC
                npu = fallback.npuC
                battery = fallback.batteryC
                entryCount = fallback.entryCount
                rawSensors.addAll(fallback.rawSensors)
                cpuCandidates.addAll(fallback.cpuCandidates)
                if (fallback.isCpuThrottling) isCpuThrottling = true
            }
        }

        val thermalStatus = statusRegex.find(output)
            ?.groupValues?.get(1)
            ?.toIntOrNull()
            ?: propStatusRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()
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
            halNotReady = halNotReady,
            isCpuThrottling = isCpuThrottling,
            rawSensors = rawSensors,
            cpuCandidates = cpuCandidates
        )
    }

    fun rankCpuCandidate(name: String): CpuCandidateRank {
        val n = name.uppercase()
        if (n.contains("HOTSPOT") || n.contains("JUNCTION") || n.contains("MAX_TEMP") || n.contains("MAXTEMP")) {
            return CpuCandidateRank.HOTSPOT_FALLBACK
        }
        if (n == "CPU" || n.contains("CPU-PACKAGE") || n.contains("CPUSS") ||
            n.contains("MTKTS_SOC") || n.contains("TS_SOC") || n == "SOC" || n.contains("SOC-THERM")) {
            return CpuCandidateRank.PACKAGE_OR_SOC
        }
        if (n.contains("CLUSTER") || n.contains("GOLD") || n.contains("SILVER") || n.contains("PRIME")) {
            return CpuCandidateRank.CLUSTER_AVERAGE
        }
        return CpuCandidateRank.CORE_SENSOR
    }

    fun selectRepresentativeCpuTemp(candidates: List<CpuCandidate>): Float? {
        if (candidates.isEmpty()) return null
        val bestPriority = candidates.minOf { it.rank.priority }
        val bestCandidates = candidates.filter { it.rank.priority == bestPriority }
        return when (bestCandidates.first().rank) {
            CpuCandidateRank.PACKAGE_OR_SOC -> {
                bestCandidates.map { it.sensor.valueC }.average().toFloat()
            }
            CpuCandidateRank.CLUSTER_AVERAGE -> {
                // Max across clusters reflects the active/loaded cluster
                bestCandidates.maxOf { it.sensor.valueC }
            }
            CpuCandidateRank.CORE_SENSOR -> {
                // Average across standard active core sensors
                bestCandidates.map { it.sensor.valueC }.average().toFloat()
            }
            CpuCandidateRank.HOTSPOT_FALLBACK -> {
                // Fallback only if no package/cluster/core sensors exist
                bestCandidates.minOf { it.sensor.valueC }
            }
        }
    }

    private fun isCpuCoolingDevice(type: Int?, name: String): Boolean {
        val n = name.lowercase()
        if (n.contains("fan") || n.contains("gpu") || n.contains("modem") ||
            n.contains("batt") || n.contains("skin") || n.contains("lcd") || n.contains("camera")) {
            return false
        }
        // AOSP CoolingDevice.TYPE_CPU = 2
        if (type == 2) return true
        return n.contains("cpufreq") || n.contains("cpu-cooling") ||
            n.contains("thermal-cpufreq") || n.startsWith("cpu") || n.contains("processor")
    }

    private fun parseFallbackZones(output: String): Result {
        val rawSensors = mutableListOf<RawSensor>()
        val cpuCandidates = mutableListOf<CpuCandidate>()
        var gpu: Float? = null
        var skin: Float? = null
        var npu: Float? = null
        var battery: Float? = null
        var count = 0

        val lines = output.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Strategy 1: Explicit name:value pair lines (from sh -c 'for z in ...; do echo "$name:$temp"; done')
        for (line in lines) {
            if (line.contains(":")) {
                val parts = line.split(":").map { it.trim() }
                if (parts.size >= 2) {
                    val namePart = parts[parts.size - 2]
                    val valPart = parts.last()
                    val rawNum = valPart.toFloatOrNull()
                    if (rawNum != null) {
                        val degC = if (kotlin.math.abs(rawNum) > 200f) rawNum / 1000f else rawNum
                        if (degC in 0f..120f) {
                            val kind = classify(null, namePart)
                            val raw = RawSensor(name = namePart, type = null, valueC = degC, kind = kind)
                            rawSensors.add(raw)
                            when (kind) {
                                SensorKind.CPU -> {
                                    val rank = rankCpuCandidate(namePart)
                                    cpuCandidates.add(CpuCandidate(raw, rank))
                                    count++
                                }
                                SensorKind.GPU -> if (gpu == null) { gpu = degC; count++ }
                                SensorKind.SKIN -> if (skin == null) { skin = degC; count++ }
                                SensorKind.NPU -> if (npu == null) { npu = degC; count++ }
                                SensorKind.BATTERY -> if (battery == null) { battery = degC; count++ }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }

        if (count > 0) {
            val cpu = selectRepresentativeCpuTemp(cpuCandidates)
            return Result(
                cpuC = cpu,
                gpuC = gpu,
                skinC = skin,
                npuC = npu,
                batteryC = battery,
                entryCount = count,
                rawSensors = rawSensors,
                cpuCandidates = cpuCandidates
            )
        }

        // Strategy 2: Legacy fallback for unstructured text
        val names = mutableListOf<Pair<Int, String>>()
        val values = mutableListOf<Pair<Int, Float>>()

        for ((idx, line) in lines.withIndex()) {
            val num = line.toFloatOrNull()
            if (num != null) {
                val degC = if (kotlin.math.abs(num) > 200f) num / 1000f else num
                if (degC in 0f..120f) {
                    values.add(idx to degC)
                }
            } else {
                val kind = classify(null, line)
                if (kind != SensorKind.UNKNOWN) {
                    names.add(idx to line)
                }
            }
        }

        if (names.isNotEmpty() && values.isNotEmpty()) {
            for ((nameIdx, lineName) in names) {
                val matchVal = values.firstOrNull { it.first >= nameIdx } ?: values.firstOrNull()
                if (matchVal != null) {
                    val kind = classify(null, lineName)
                    val raw = RawSensor(name = lineName, type = null, valueC = matchVal.second, kind = kind)
                    rawSensors.add(raw)
                    when (kind) {
                        SensorKind.CPU -> {
                            val rank = rankCpuCandidate(lineName)
                            cpuCandidates.add(CpuCandidate(raw, rank))
                            count++
                        }
                        SensorKind.GPU -> if (gpu == null) { gpu = matchVal.second; count++ }
                        SensorKind.SKIN -> if (skin == null) { skin = matchVal.second; count++ }
                        SensorKind.NPU -> if (npu == null) { npu = matchVal.second; count++ }
                        SensorKind.BATTERY -> if (battery == null) { battery = matchVal.second; count++ }
                        else -> {}
                    }
                }
            }
        }

        val cpu = selectRepresentativeCpuTemp(cpuCandidates)
        return Result(
            cpuC = cpu,
            gpuC = gpu,
            skinC = skin,
            npuC = npu,
            batteryC = battery,
            entryCount = count,
            rawSensors = rawSensors,
            cpuCandidates = cpuCandidates
        )
    }

    enum class SensorKind { CPU, GPU, SKIN, NPU, BATTERY, UNKNOWN }

    /**
     * AOSP Temperature types: 0=CPU 1=GPU 2=BATTERY 3=SKIN 9=NPU.
     * Name fallback is case-insensitive with strict GPU matching to avoid false positives.
     */
    fun classify(type: Int?, name: String): SensorKind {
        when (type) {
            0 -> return SensorKind.CPU
            1 -> return SensorKind.GPU
            2 -> return SensorKind.BATTERY
            3 -> return SensorKind.SKIN
            9 -> return SensorKind.NPU
        }

        val n = name.uppercase()
        return when {
            n.contains("SKIN") || n.contains("QUIET_THERM") || n.contains("XO_THERM") ||
                n.contains("SHELL_TEMP") || n.contains("BACK_THERM") || n.contains("CHASSIS") -> SensorKind.SKIN
            isGpuName(n) -> SensorKind.GPU
            n.contains("NPU") || n.contains("TPU") || n.contains("APU") || n.contains("Q6-HVX") -> SensorKind.NPU
            n.contains("BATTERY") || n.contains("BATT") -> SensorKind.BATTERY
            n.contains("CPU") || n.contains("SOC") || n.contains("CLUSTER") || n.contains("CPUSS") ||
                n.contains("TSENS") || n.contains("APC") || n.contains("SILVER") || n.contains("GOLD") ||
                n.contains("PRIME") || n.contains("MTKTS") || n.contains("TS_SOC") -> SensorKind.CPU
            else -> SensorKind.UNKNOWN
        }
    }

    /** Strict GPU matching prevents false positives on unrelated '3D' or 'GFX' substrings. */
    private fun isGpuName(n: String): Boolean {
        return n.contains("KGSL") || n.contains("GPUSS") || n.contains("MALI") ||
            n.contains("GRAPHICS") || n.contains("KGSL-3D0") ||
            (n.startsWith("GPU") && (n.length == 3 || n[3] == '-' || n[3] == '_' || n[3].isDigit())) ||
            n.contains("-GPU") || n.contains("_GPU") || n.contains("GPU-") || n.contains("GPU_") ||
            n.startsWith("GFX") || n.endsWith("GFX") || n.contains("GFX-") || n.contains("_GFX") || n.contains("-GFX")
    }
}
