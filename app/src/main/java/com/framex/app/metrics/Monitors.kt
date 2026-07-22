package com.framex.app.metrics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

internal data class CpuTimes(val total: Long, val idle: Long)

internal object CpuStatParser {
    fun parseTotalCpuLine(lines: List<String>): CpuTimes? {
        val parts = lines.firstOrNull()
            ?.trim()
            ?.split("\\s+".toRegex())
            ?: return null

        if (parts.size < 5 || parts[0] != "cpu") return null

        val values = parts.drop(1).map { it.toLongOrNull() ?: return null }
        val user = values.getOrNull(0) ?: return null
        val nice = values.getOrNull(1) ?: return null
        val system = values.getOrNull(2) ?: return null
        val idle = values.getOrNull(3) ?: return null
        val iowait = values.getOrNull(4) ?: 0L
        val irq = values.getOrNull(5) ?: 0L
        val softirq = values.getOrNull(6) ?: 0L
        val steal = values.getOrNull(7) ?: 0L

        return CpuTimes(
            total = user + nice + system + idle + iowait + irq + softirq + steal,
            idle = idle + iowait
        )
    }

    fun calculateUsage(previous: CpuTimes, current: CpuTimes): Int? {
        val diffTotal = current.total - previous.total
        val diffIdle = current.idle - previous.idle
        if (diffTotal <= 0L || diffIdle < 0L) return null

        return (((diffTotal - diffIdle).toFloat() / diffTotal) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }
}

@Singleton
class FpsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    // fps: rolling averageFPS (existing behavior, unchanged).
    // jankyFrames: frames counted as janky in the CURRENT accumulation window from
    // the same `--timestats -dump` call — explains "FPS reads fine but feels laggy":
    // averageFPS is a mean over the window, so irregular/stuttering frame delivery
    // can still average out to a normal-looking number while jankyFrames stays high.
    data class FpsState(val fps: Int, val jankyFrames: Int)

    // Exact approach decoded from PerfStats smali (OverlayService$FPSMonitor.smali):
    //
    // TIMING (from smali constants 0xbb8=3000, 0x3e8=1000):
    //   Clear condition: System.currentTimeMillis() % 3000 < 1000
    //   Poll interval: 1000ms  (Handler.postDelayed 1000ms)
    //
    // WHY wall-clock clear beats poll-counter clear:
    //   A poll-counter "every 3 polls" clears, then the VERY NEXT poll dumps a
    //   freshly-cleared SurfaceFlinger → averageFPS absent → emit 0 → visible dropout.
    //   Wall-clock ensures ≥2 full seconds of frame accumulation before the next clear,
    //   so the poll right after a clear always has real data.
    //
    // HOLD LAST KNOWN: if a dump returns 0 (e.g. right after clear), keep showing
    //   the last real value instead of flashing 0. PerfStats does the same via Handler.
    val fpsState: Flow<FpsState> = flow {
        var initialized = false
        var lastKnownFps = 0

        while (true) {
            val shizukuReady = shizukuManager.isShizukuAvailable.value &&
                    shizukuManager.hasPermission.value

            if (shizukuReady) {
                try {
                    if (!initialized) {
                        // Clear any stale stats and start fresh accumulation.
                        // Do NOT dump yet — SurfaceFlinger needs ≥1 second to collect frames.
                        shizukuManager.executeCommand(
                            "dumpsys SurfaceFlinger --timestats -clear -enable"
                        )
                        initialized = true
                    } else {
                        // Step 1: dump the accumulated average (same as PerfStats step 1)
                        val output = shizukuManager.executeCommand(
                            "dumpsys SurfaceFlinger --timestats -dump"
                        )

                        val parsed = Regex("averageFPS\\s*=\\s*([0-9.]+)")
                            .find(output)
                            ?.groupValues?.get(1)
                            ?.toFloatOrNull()
                            ?.toInt()
                            ?: 0

                        // The real SurfaceFlinger --timestats field is "missedFrames"
                        // (confirmed against AOSP source: TimeStatsHelper.cpp formats it as
                        // "missedFrames = %d\n"). An earlier version of this code looked for
                        // "missedFrameCount", which does not exist in the actual dump — that
                        // typo is why jankyFrames always read 0. missedFrames is SurfaceFlinger's
                        // own count of frames that missed their intended present deadline in
                        // this accumulation window — the direct stutter signal, independent of
                        // whatever the window's averageFPS mean happens to be.
                        val janky = Regex("missedFrames\\s*=\\s*([0-9]+)")
                            .find(output)
                            ?.groupValues?.get(1)
                            ?.toIntOrNull()
                            ?: 0

                        // Step 2: show result — hold last known if dump returned nothing
                        if (parsed > 0) lastKnownFps = parsed
                        emit(FpsState(lastKnownFps, janky))

                        // Step 3: PerfStats exact clear logic (smali: rem-long % 3000 < 1000).
                        // Only clear during the first 1000ms of each 3000ms wall-clock cycle.
                        // Guarantees ≥2 seconds of accumulation before the next clear fires.
                        if (System.currentTimeMillis() % 3000L < 1000L) {
                            shizukuManager.executeCommand(
                                "dumpsys SurfaceFlinger --timestats -clear -enable"
                            )
                        }
                    }
                } catch (e: Exception) {
                    emit(FpsState(lastKnownFps, 0)) // hold last known; never flash 0 on transient error
                }
            } else {
                initialized = false
                lastKnownFps = 0
                emit(FpsState(0, 0))
            }
            delay(1000)
        }
    }
}

@Singleton
class CpuMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    data class CpuClusterState(val effMhz: Int, val perfMhz: Int, val ultraMhz: Int)
    private data class CpuPolicy(val currentMhz: Int, val maxMhz: Int)

    // Expose discovered CPU frequency policy groups, mapped by max clock.
    val cpuClusterUsage: Flow<CpuClusterState> = flow {
        while (true) {
            emit(readClusterState())
            delay(1000)
        }
    }

    // Exact same approach as PerfStats: read cpu0 current clock frequency from sysfs.
    val cpuUsage: Flow<Int> = flow {
        while (true) {
            emit(readFreq(0))
            delay(1000)
        }
    }

    // System-wide CPU utilization percentage (0-100%) parsed from /proc/stat
    val cpuPercentageUsage: Flow<Int?> = flow {
        var previousCpuTimes: CpuTimes? = null
        while (true) {
            try {
                val output = if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
                    try {
                        shizukuManager.executeCommand("cat /proc/stat")
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }
                
                val lines = if (output.isNotEmpty()) {
                    output.lines()
                } else {
                    try {
                        java.io.File("/proc/stat").readLines()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                val currentCpuTimes = CpuStatParser.parseTotalCpuLine(lines)
                val previous = previousCpuTimes
                previousCpuTimes = currentCpuTimes

                if (currentCpuTimes != null && previous != null) {
                    emit(CpuStatParser.calculateUsage(previous, currentCpuTimes))
                } else {
                    emit(null)
                }
            } catch (e: Exception) {
                emit(null)
            }
            delay(1000)
        }
    }

    private fun readClusterState(): CpuClusterState {
        val policies = readCpuPolicies().sortedBy { it.maxMhz }
        return when (policies.size) {
            0 -> CpuClusterState(0, 0, 0)
            1 -> CpuClusterState(policies[0].currentMhz, 0, 0)
            2 -> CpuClusterState(policies[0].currentMhz, policies[1].currentMhz, 0)
            else -> CpuClusterState(
                effMhz = policies.first().currentMhz,
                perfMhz = policies[policies.lastIndex - 1].currentMhz,
                ultraMhz = policies.last().currentMhz
            )
        }
    }

    private fun readCpuPolicies(): List<CpuPolicy> {
        val policyDir = java.io.File("/sys/devices/system/cpu/cpufreq")
        val policies = policyDir.listFiles { file -> file.isDirectory && file.name.startsWith("policy") }
            ?.mapNotNull { policy ->
                val current = readMhz(policy.resolve("scaling_cur_freq"))
                val max = readMhz(policy.resolve("cpuinfo_max_freq"))
                if (current > 0 || max > 0) CpuPolicy(current, max.coerceAtLeast(current)) else null
            }
            .orEmpty()

        if (policies.isNotEmpty()) return policies

        return java.io.File("/sys/devices/system/cpu")
            .listFiles { file -> file.isDirectory && file.name.matches(Regex("cpu\\d+")) }
            ?.mapNotNull { cpu ->
                val freqDir = cpu.resolve("cpufreq")
                val current = readMhz(freqDir.resolve("scaling_cur_freq"))
                val max = readMhz(freqDir.resolve("cpuinfo_max_freq"))
                if (current > 0 || max > 0) CpuPolicy(current, max.coerceAtLeast(current)) else null
            }
            .orEmpty()
            .distinctBy { it.maxMhz }
    }

    private fun readFreq(coreIndex: Int): Int {
        return readMhz(java.io.File("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_cur_freq"))
    }

    private fun readMhz(file: java.io.File): Int {
        return try {
            val raw = file.readText().trim()
            (raw.toIntOrNull() ?: 0) / KHZ_TO_MHZ
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        private const val KHZ_TO_MHZ = 1000
    }
}

@Singleton
class RamMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    data class RamState(val usedGb: Float, val totalGb: Float)

    val ramUsage: Flow<RamState> = flow {
        while (true) {
            val state = try {
                parseMemInfo()
            } catch (e: Exception) {
                fallbackRam(context)
            }
            emit(state)
            delay(2000)
        }
    }

    private fun parseMemInfo(): RamState {
        val file = java.io.File("/proc/meminfo")
        if (file.exists()) {
            val lines = file.readLines()
            var totalKb = 0f
            var availKb = -1f
            var freeKb = 0f
            var buffersKb = 0f
            var cachedKb = 0f
            for (line in lines) {
                val parts = line.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val valStr = parts[1].trim().split("\\s+".toRegex())[0].trim()
                    val value = valStr.toFloatOrNull() ?: 0f
                    when (key) {
                        "MemTotal" -> totalKb = value
                        "MemAvailable" -> availKb = value
                        "MemFree" -> freeKb = value
                        "Buffers" -> buffersKb = value
                        "Cached" -> cachedKb = value
                    }
                }
            }
            val usedKb = if (availKb >= 0f) {
                totalKb - availKb
            } else {
                totalKb - freeKb - buffersKb - cachedKb
            }
            // Return RAM usage in GB
            return RamState(usedKb / (1024f * 1024f), totalKb / (1024f * 1024f))
        } else {
            return fallbackRam(context)
        }
    }

    private fun fallbackRam(context: Context): RamState {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return RamState(
            usedGb = (info.totalMem - info.availMem).toFloat() / (1024 * 1024 * 1024),
            totalGb = info.totalMem.toFloat() / (1024 * 1024 * 1024)
        )
    }
}

@Singleton
class NetworkMonitor @Inject constructor() {
    data class NetworkState(val rxSpeedKbps: Float, val txSpeedKbps: Float)

    val networkSpeed: Flow<NetworkState> = flow {
        // Guard: on some devices/builds TrafficStats is not supported.
        if (TrafficStats.getTotalRxBytes() == TrafficStats.UNSUPPORTED.toLong()) {
            while (true) { emit(NetworkState(0f, 0f)); delay(2000) }
            return@flow
        }
        var previousRx = TrafficStats.getTotalRxBytes()
        var previousTx = TrafficStats.getTotalTxBytes()
        var previousTime = System.currentTimeMillis()

        while (true) {
            delay(1000)
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()
            val currentTime = System.currentTimeMillis()

            val timeDiffSec = (currentTime - previousTime) / 1000.0f
            if (timeDiffSec > 0 && currentRx >= 0 && currentTx >= 0) {
                val rxSpeedKbps = ((currentRx - previousRx) / 1024.0f) / timeDiffSec
                val txSpeedKbps = ((currentTx - previousTx) / 1024.0f) / timeDiffSec
                emit(NetworkState(
                    rxSpeedKbps.coerceAtLeast(0f),
                    txSpeedKbps.coerceAtLeast(0f)
                ))
            } else {
                emit(NetworkState(0f, 0f))
            }

            previousRx = currentRx
            previousTx = currentTx
            previousTime = currentTime
        }
    }
}

@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val batteryTemp: Flow<Float> = flow {
        while (true) {
            emit(readBatteryTemp(context))
            delay(BATTERY_POLL_INTERVAL_MS)
        }
    }

    private fun readBatteryTemp(context: Context): Float {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val raw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return raw / 10.0f
    }

    companion object {
        private const val BATTERY_POLL_INTERVAL_MS = 5000L
    }
}

@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    data class ThermalState(
        val cpuC: Float = 0f,
        val gpuC: Float = 0f,
        val npuC: Float = 0f,
        val skinC: Float = 0f,
        val batteryC: Float = 0f,
        val status: Int = 0,
        val readStatus: MetricReadStatus = MetricReadStatus.Loading,
        val hasCpu: Boolean = false,
        val hasGpu: Boolean = false,
        val hasNpu: Boolean = false,
        val hasSkin: Boolean = false,
        val hasBattery: Boolean = false
    ) {
        val statusLabel: String get() = when (status) {
            0 -> "NONE"; 1 -> "LIGHT"; 2 -> "MODERATE"; 3 -> "SEVERE"
            4 -> "CRITICAL"; 5 -> "EMERGENCY"; 6 -> "SHUTDOWN"; else -> "?"
        }
    }

    val thermalState: Flow<ThermalState> = flow {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        var lastGoodState: ThermalState? = null
        var consecutiveFailures = 0

        while (true) {
            val shizukuReady = shizukuManager.isShizukuAvailable.value &&
                    shizukuManager.hasPermission.value

            val state = if (shizukuReady) {
                try {
                    val output = shizukuManager.getThermalTemperatures()
                    if (output.isBlank()) {
                        consecutiveFailures++
                        if (consecutiveFailures <= 3 && lastGoodState != null) {
                            lastGoodState.copy(readStatus = MetricReadStatus.Stale)
                        } else {
                            if (consecutiveFailures > 3) {
                                lastGoodState = null
                            }
                            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                powerManager.currentThermalStatus
                            } else 0
                            ThermalState(status = status, readStatus = MetricReadStatus.EmptyOutput)
                        }
                    } else {
                        val parsed = ThermalServiceParser.parse(output)
                        if (parsed == null || parsed.entryCount == 0) {
                            consecutiveFailures++
                            if (consecutiveFailures <= 3 && lastGoodState != null) {
                                lastGoodState.copy(readStatus = MetricReadStatus.Stale)
                            } else {
                                if (consecutiveFailures > 3) {
                                    lastGoodState = null
                                }
                                val status = parsed?.thermalStatus ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    powerManager.currentThermalStatus
                                } else 0
                                ThermalState(status = status, readStatus = MetricReadStatus.ParseFailed)
                            }
                        } else {
                            consecutiveFailures = 0
                            val newState = ThermalState(
                                cpuC = parsed.cpuC ?: 0f,
                                gpuC = parsed.gpuC ?: 0f,
                                npuC = parsed.npuC ?: 0f,
                                skinC = parsed.skinC ?: 0f,
                                batteryC = parsed.batteryC ?: 0f,
                                status = parsed.thermalStatus,
                                readStatus = MetricReadStatus.Ok,
                                hasCpu = parsed.cpuC != null,
                                hasGpu = parsed.gpuC != null,
                                hasNpu = parsed.npuC != null,
                                hasSkin = parsed.skinC != null,
                                hasBattery = parsed.batteryC != null
                            )
                            lastGoodState = newState
                            newState
                        }
                    }
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures <= 3 && lastGoodState != null) {
                        lastGoodState.copy(readStatus = MetricReadStatus.Stale)
                    } else {
                        if (consecutiveFailures > 3) {
                            lastGoodState = null
                        }
                        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            powerManager.currentThermalStatus
                        } else 0
                        ThermalState(status = status, readStatus = MetricReadStatus.ParseFailed)
                    }
                }
            } else {
                consecutiveFailures = 0
                lastGoodState = null
                val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    powerManager.currentThermalStatus
                } else 0
                ThermalState(status = status, readStatus = MetricReadStatus.NoShizuku)
            }
            emit(state)
            delay(1000)
        }
    }
}

@Singleton
class PingMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    companion object {
        // ICMP echo has near-zero cost, but child "ping" process still costs a bit of CPU/battery.
        // 20s keeps the reading fresh without polling faster than a human perceives latency changing.
        private const val POLL_INTERVAL_MS = 20_000L
    }

    val ping: Flow<Int> = flow {
        while (true) {
            val output = if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
                try {
                    shizukuManager.executeCommand("ping -c 1 8.8.8.8")
                } catch (e: Exception) {
                    executePing()
                }
            } else {
                executePing()
            }

            val pingMs = if (output.contains("time=")) {
                output.split("time=").getOrNull(1)
                    ?.split(" ")?.getOrNull(0)
                    ?.toFloatOrNull()
                    ?.roundToInt() ?: 0
            } else 0
            emit(pingMs)
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun executePing(): String {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 8.8.8.8")
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.w("executePing failed", e)
            ""
        }
    }
}

@Singleton
class TopProcessMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    data class TopProcess(
        val name: String = "",
        val cpuPercent: Float = 0f,
        val readStatus: MetricReadStatus = MetricReadStatus.Loading
    )

    val topProcess: Flow<TopProcess> = flow {
        while (true) {
            val shizukuReady = shizukuManager.isShizukuAvailable.value &&
                    shizukuManager.hasPermission.value

            val result = if (shizukuReady) {
                try {
                    val output = shizukuManager.executeCommand("dumpsys cpuinfo")
                    if (output.isBlank()) {
                        TopProcess(readStatus = MetricReadStatus.EmptyOutput)
                    } else {
                        val parsed = CpuInfoTopParser.parseTop(output)
                        if (parsed != null) {
                            TopProcess(name = parsed.name, cpuPercent = parsed.cpuPercent, readStatus = MetricReadStatus.Ok)
                        } else {
                            TopProcess(readStatus = MetricReadStatus.NoData)
                        }
                    }
                } catch (e: Exception) {
                    TopProcess(readStatus = MetricReadStatus.ParseFailed)
                }
            } else {
                TopProcess(readStatus = MetricReadStatus.NoShizuku)
            }

            emit(result)
            delay(1000)
        }
    }
}
