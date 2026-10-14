package com.framex.app.metrics

import androidx.compose.runtime.Immutable
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.math.roundToInt

@Immutable
data class CpuClusterMetric(
    val policyId: Int,
    val label: String,
    val coreIndices: List<Int>,
    val currentMhz: Int,
    val maxMhz: Int,
    val usagePercent: Int? = null
)

@Immutable
data class CpuTelemetrySnapshot(
    val peakMhz: Int = 0,
    val totalUsagePercent: Int? = null,
    val effMhz: Int = 0,
    val perfMhz: Int = 0,
    val ultraMhz: Int = 0,
    val clusters: List<CpuClusterMetric> = emptyList(),
    val readStatus: MetricReadStatus = MetricReadStatus.Loading
)

internal object CpuTopologyParser {

    private const val KHZ_TO_MHZ = 1000
    private const val TWO_CLUSTER_ULTRA_THRESHOLD_MHZ = 2800
    private const val TOTAL_CPU_KEY = -1

    private data class RawPolicyEntry(
        val policyId: Int,
        val coreIndices: List<Int>,
        val maxMhz: Int,
        val currentMhz: Int
    )

    /**
     * Parses a combined single-IPC telemetry payload from [com.framex.app.shizuku.CommandRunnerService.readCpuTelemetry]
     * in a single O(1) pass without regex allocations.
     */
    fun parseTelemetryPayload(
        payload: String,
        previousTimes: Map<Int, CpuTimes>
    ): Pair<CpuTelemetrySnapshot, Map<Int, CpuTimes>> {
        if (payload.isBlank()) {
            return CpuTelemetrySnapshot(readStatus = MetricReadStatus.EmptyOutput) to previousTimes
        }

        val currentTimes = HashMap<Int, CpuTimes>(18)
        val rawPolicies = ArrayList<RawPolicyEntry>(6)
        val fallbackCores = ArrayList<RawPolicyEntry>(16)

        for (rawLine in payload.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("STAT:") -> {
                    val statContent = line.substring(5).trim()
                    if (statContent.startsWith("cpu ")) {
                        parseCpuTimeLine(statContent)?.let { currentTimes[TOTAL_CPU_KEY] = it }
                    } else if (statContent.startsWith("cpu")) {
                        val spaceIdx = statContent.indexOf(' ')
                        if (spaceIdx > 3) {
                            val coreId = statContent.substring(3, spaceIdx).toIntOrNull()
                            if (coreId != null) {
                                parseCpuTimeLine(statContent)?.let { currentTimes[coreId] = it }
                            }
                        }
                    }
                }
                line.startsWith("POLICY:") -> {
                    // Format: POLICY:<policyId>:<related_cpus>:<maxFreqKhz>:<curFreqKhz>
                    val parts = line.substring(7).split(':')
                    if (parts.size >= 4) {
                        val policyId = parts[0].trim().toIntOrNull() ?: 0
                        val cores = parseSpaceSeparatedInts(parts[1])
                        val maxKhz = parts[2].trim().toLongOrNull() ?: 0L
                        val curKhz = parts[3].trim().toLongOrNull() ?: 0L
                        val curMhz = (curKhz / KHZ_TO_MHZ).toInt()
                        val maxMhz = (maxKhz / KHZ_TO_MHZ).toInt().coerceAtLeast(curMhz)
                        if (maxMhz > 0 || curMhz > 0) {
                            rawPolicies.add(
                                RawPolicyEntry(
                                    policyId = policyId,
                                    coreIndices = if (cores.isNotEmpty()) cores else listOf(policyId),
                                    maxMhz = maxMhz,
                                    currentMhz = curMhz
                                )
                            )
                        }
                    }
                }
                line.startsWith("CORE:") -> {
                    // Format: CORE:<coreId>:<maxFreqKhz>:<curFreqKhz>
                    val parts = line.substring(5).split(':')
                    if (parts.size >= 3) {
                        val coreId = parts[0].trim().toIntOrNull() ?: continue
                        val maxKhz = parts[1].trim().toLongOrNull() ?: 0L
                        val curKhz = parts[2].trim().toLongOrNull() ?: 0L
                        val curMhz = (curKhz / KHZ_TO_MHZ).toInt()
                        val maxMhz = (maxKhz / KHZ_TO_MHZ).toInt().coerceAtLeast(curMhz)
                        if (maxMhz > 0 || curMhz > 0) {
                            fallbackCores.add(
                                RawPolicyEntry(
                                    policyId = coreId,
                                    coreIndices = listOf(coreId),
                                    maxMhz = maxMhz,
                                    currentMhz = curMhz
                                )
                            )
                        }
                    }
                }
            }
        }

        val effectivePolicies = if (rawPolicies.isNotEmpty()) {
            rawPolicies.sortedWith(compareBy<RawPolicyEntry> { it.maxMhz }.thenBy { it.policyId })
        } else {
            groupCoresByMaxFrequency(fallbackCores)
        }

        return buildSnapshot(
            sortedPolicies = effectivePolicies,
            currentTimes = currentTimes,
            previousTimes = previousTimes
        )
    }

    /**
     * Local non-Shizuku JVM File fallback for reading CPU topology and /proc/stat directly
     * from the app UID when allowed by the device's SELinux policy.
     */
    fun readLocalFallback(
        previousTimes: Map<Int, CpuTimes>,
        shizukuReady: Boolean
    ): Pair<CpuTelemetrySnapshot, Map<Int, CpuTimes>> {
        val currentTimes = HashMap<Int, CpuTimes>(18)
        var anyFileReadable = false

        try {
            val statFile = File("/proc/stat")
            if (statFile.exists() && statFile.canRead()) {
                BufferedReader(FileReader(statFile), 1024).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("cpu ")) {
                            parseCpuTimeLine(trimmed)?.let {
                                currentTimes[TOTAL_CPU_KEY] = it
                                anyFileReadable = true
                            }
                        } else if (trimmed.startsWith("cpu")) {
                            val spaceIdx = trimmed.indexOf(' ')
                            if (spaceIdx > 3) {
                                val coreId = trimmed.substring(3, spaceIdx).toIntOrNull()
                                if (coreId != null) {
                                    parseCpuTimeLine(trimmed)?.let { currentTimes[coreId] = it }
                                }
                            }
                        } else if (currentTimes.isNotEmpty()) {
                            break
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {
            // /proc/stat restricted by hidepid/SELinux on unprivileged UID
        }

        val rawPolicies = ArrayList<RawPolicyEntry>(6)
        try {
            val policyDir = File("/sys/devices/system/cpu/cpufreq")
            val dirs = policyDir.listFiles { f -> f.isDirectory && f.name.startsWith("policy") }
            if (!dirs.isNullOrEmpty()) {
                for (dir in dirs) {
                    val policyId = dir.name.removePrefix("policy").toIntOrNull() ?: 0
                    val relatedStr = readLineFast(File(dir, "related_cpus"))
                        .ifEmpty { readLineFast(File(dir, "affected_cpus")) }
                    val cores = parseSpaceSeparatedInts(relatedStr).ifEmpty { listOf(policyId) }
                    val curMhz = (readLongFast(File(dir, "scaling_cur_freq")) / KHZ_TO_MHZ).toInt()
                    val maxMhz = (readLongFast(File(dir, "cpuinfo_max_freq")) / KHZ_TO_MHZ)
                        .toInt()
                        .coerceAtLeast(curMhz)
                    if (curMhz > 0 || maxMhz > 0) {
                        anyFileReadable = true
                        rawPolicies.add(RawPolicyEntry(policyId, cores, maxMhz, curMhz))
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to cpu0..cpu15 below
        }

        val sortedPolicies = if (rawPolicies.isNotEmpty()) {
            rawPolicies.sortedWith(compareBy<RawPolicyEntry> { it.maxMhz }.thenBy { it.policyId })
        } else {
            val cores = ArrayList<RawPolicyEntry>(16)
            val cpuRoot = File("/sys/devices/system/cpu")
            for (i in 0..15) {
                val freqDir = File(cpuRoot, "cpu$i/cpufreq")
                if (!freqDir.exists()) continue
                val curMhz = (readLongFast(File(freqDir, "scaling_cur_freq")) / KHZ_TO_MHZ).toInt()
                val maxMhz = (readLongFast(File(freqDir, "cpuinfo_max_freq")) / KHZ_TO_MHZ)
                    .toInt()
                    .coerceAtLeast(curMhz)
                if (curMhz > 0 || maxMhz > 0) {
                    anyFileReadable = true
                    cores.add(RawPolicyEntry(i, listOf(i), maxMhz, curMhz))
                }
            }
            groupCoresByMaxFrequency(cores)
        }

        if (!anyFileReadable && sortedPolicies.isEmpty()) {
            val status = if (shizukuReady) MetricReadStatus.PermissionDenied else MetricReadStatus.NoShizuku
            return CpuTelemetrySnapshot(readStatus = status) to previousTimes
        }

        return buildSnapshot(
            sortedPolicies = sortedPolicies,
            currentTimes = currentTimes,
            previousTimes = previousTimes
        )
    }

    private fun buildSnapshot(
        sortedPolicies: List<RawPolicyEntry>,
        currentTimes: Map<Int, CpuTimes>,
        previousTimes: Map<Int, CpuTimes>
    ): Pair<CpuTelemetrySnapshot, Map<Int, CpuTimes>> {
        val prevTotal = previousTimes[TOTAL_CPU_KEY]
        val curTotal = currentTimes[TOTAL_CPU_KEY]
        val totalPct = if (prevTotal != null && curTotal != null) {
            calculateUsagePercent(prevTotal, curTotal)
        } else {
            null
        }

        var peakMhz = 0
        val clusterMetrics = ArrayList<CpuClusterMetric>(sortedPolicies.size)

        for (policy in sortedPolicies) {
            if (policy.currentMhz > peakMhz) {
                peakMhz = policy.currentMhz
            }
            val clusterPct = calculateClusterUsagePercent(policy.coreIndices, previousTimes, currentTimes)
            clusterMetrics.add(
                CpuClusterMetric(
                    policyId = policy.policyId,
                    label = formatCoreRangeLabel(policy.coreIndices),
                    coreIndices = policy.coreIndices,
                    currentMhz = policy.currentMhz,
                    maxMhz = policy.maxMhz,
                    usagePercent = clusterPct
                )
            )
        }

        val (effMhz, perfMhz, ultraMhz) = when (sortedPolicies.size) {
            0 -> Triple(0, 0, 0)
            1 -> Triple(sortedPolicies[0].currentMhz, 0, 0)
            2 -> {
                if (sortedPolicies[1].maxMhz >= TWO_CLUSTER_ULTRA_THRESHOLD_MHZ) {
                    Triple(0, sortedPolicies[0].currentMhz, sortedPolicies[1].currentMhz)
                } else {
                    Triple(sortedPolicies[0].currentMhz, sortedPolicies[1].currentMhz, 0)
                }
            }
            else -> Triple(
                sortedPolicies.first().currentMhz,
                sortedPolicies[sortedPolicies.lastIndex - 1].currentMhz,
                sortedPolicies.last().currentMhz
            )
        }

        val nextTimes = if (currentTimes.isNotEmpty()) currentTimes else previousTimes
        val status = if (sortedPolicies.isNotEmpty() || totalPct != null) {
            MetricReadStatus.Ok
        } else {
            MetricReadStatus.NoData
        }

        return CpuTelemetrySnapshot(
            peakMhz = peakMhz,
            totalUsagePercent = totalPct,
            effMhz = effMhz,
            perfMhz = perfMhz,
            ultraMhz = ultraMhz,
            clusters = clusterMetrics,
            readStatus = status
        ) to nextTimes
    }

    private fun calculateClusterUsagePercent(
        coreIndices: List<Int>,
        previousTimes: Map<Int, CpuTimes>,
        currentTimes: Map<Int, CpuTimes>
    ): Int? {
        if (coreIndices.isEmpty() || previousTimes.isEmpty() || currentTimes.isEmpty()) return null
        var diffTotalSum = 0L
        var diffIdleSum = 0L
        var matchedCores = 0

        for (coreId in coreIndices) {
            val prev = previousTimes[coreId] ?: continue
            val cur = currentTimes[coreId] ?: continue
            val dTotal = cur.total - prev.total
            val dIdle = cur.idle - prev.idle
            if (dTotal > 0L && dIdle >= 0L) {
                diffTotalSum += dTotal
                diffIdleSum += dIdle
                matchedCores++
            }
        }

        if (matchedCores == 0 || diffTotalSum <= 0L) return null
        return (((diffTotalSum - diffIdleSum).toFloat() / diffTotalSum) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun groupCoresByMaxFrequency(cores: List<RawPolicyEntry>): List<RawPolicyEntry> {
        if (cores.isEmpty()) return emptyList()
        return cores
            .groupBy { it.maxMhz }
            .entries
            .sortedBy { it.key }
            .map { (maxMhz, group) ->
                val allCores = group.flatMap { it.coreIndices }.distinct().sorted()
                val peakCurMhz = group.maxOf { it.currentMhz }
                RawPolicyEntry(
                    policyId = allCores.firstOrNull() ?: 0,
                    coreIndices = allCores,
                    maxMhz = maxMhz,
                    currentMhz = peakCurMhz
                )
            }
    }

    fun formatCoreRangeLabel(cores: List<Int>): String {
        if (cores.isEmpty()) return "CPU"
        val sorted = cores.distinct().sorted()
        if (sorted.size == 1) return "CPU ${sorted[0]}"
        val isContiguous = (sorted.last() - sorted.first()) == (sorted.size - 1)
        return if (isContiguous) {
            "CPU ${sorted.first()}~${sorted.last()}"
        } else {
            "CPU ${sorted.joinToString(",")}"
        }
    }

    fun parseCpuTimeLine(line: String): CpuTimes? {
        // Fast single-pass token parser for "cpu[N] user nice system idle iowait irq softirq steal"
        var idx = 0
        val len = line.length
        while (idx < len && !line[idx].isWhitespace()) idx++
        while (idx < len && line[idx].isWhitespace()) idx++
        if (idx >= len) return null

        var fieldIndex = 0
        var user = 0L
        var nice = 0L
        var system = 0L
        var idle = 0L
        var iowait = 0L
        var irq = 0L
        var softirq = 0L
        var steal = 0L

        while (idx < len && fieldIndex < 8) {
            var acc = 0L
            var hasDigit = false
            while (idx < len && !line[idx].isWhitespace()) {
                val c = line[idx]
                if (c in '0'..'9') {
                    hasDigit = true
                    acc = acc * 10L + (c - '0')
                } else {
                    return null
                }
                idx++
            }
            if (!hasDigit) break
            when (fieldIndex) {
                0 -> user = acc
                1 -> nice = acc
                2 -> system = acc
                3 -> idle = acc
                4 -> iowait = acc
                5 -> irq = acc
                6 -> softirq = acc
                7 -> steal = acc
            }
            fieldIndex++
            while (idx < len && line[idx].isWhitespace()) idx++
        }

        if (fieldIndex < 4) return null
        return CpuTimes(
            total = user + nice + system + idle + iowait + irq + softirq + steal,
            idle = idle + iowait
        )
    }

    fun calculateUsagePercent(previous: CpuTimes, current: CpuTimes): Int? {
        val diffTotal = current.total - previous.total
        val diffIdle = current.idle - previous.idle
        if (diffTotal <= 0L || diffIdle < 0L) return null
        return (((diffTotal - diffIdle).toFloat() / diffTotal) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun parseSpaceSeparatedInts(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        val result = ArrayList<Int>(8)
        var acc = 0
        var hasDigit = false
        for (i in 0 until raw.length) {
            val ch = raw[i]
            if (ch in '0'..'9') {
                hasDigit = true
                acc = acc * 10 + (ch - '0')
            } else if (hasDigit) {
                result.add(acc)
                acc = 0
                hasDigit = false
            }
        }
        if (hasDigit) result.add(acc)
        return result
    }

    private fun readLineFast(file: File): String {
        return try {
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file), 64).use { it.readLine()?.trim().orEmpty() }
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun readLongFast(file: File): Long =
        readLineFast(file).toLongOrNull() ?: 0L
}
