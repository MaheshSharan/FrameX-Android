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
import java.io.BufferedReader
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class CpuTimes(val total: Long, val idle: Long)

internal object CpuStatParser {
    fun parseTotalCpuLine(lines: List<String>): CpuTimes? {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("cpu ")) {
                return CpuTopologyParser.parseCpuTimeLine(trimmed)
            }
        }
        return null
    }

    fun calculateUsage(previous: CpuTimes, current: CpuTimes): Int? {
        return CpuTopologyParser.calculateUsagePercent(previous, current)
    }
}

@Singleton
class FpsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    data class FpsState(
        val fps: Int = 0,
        val jankyFrames: Int = 0,
        val totalFrames: Int = 0,
        val maxFrameTimeMs: Int = 0,
        val readStatus: MetricReadStatus = MetricReadStatus.Loading
    )

    val fpsState: Flow<FpsState> = flow {
        var initialized = false
        var lastKnownFps = 0

        while (true) {
            val shizukuReady = shizukuManager.isShizukuAvailable.value &&
                shizukuManager.hasPermission.value

            if (shizukuReady) {
                try {
                    if (!initialized) {
                        shizukuManager.executeCommand(
                            "dumpsys SurfaceFlinger --timestats -clear -enable"
                        )
                        initialized = true
                        emit(FpsState(fps = lastKnownFps, readStatus = MetricReadStatus.Loading))
                    } else {
                        val output = shizukuManager.executeCommand(
                            "dumpsys SurfaceFlinger --timestats -dump"
                        )

                        val parsed = FPS_REGEX
                            .find(output)
                            ?.groupValues?.get(1)
                            ?.toFloatOrNull()
                            ?.roundToInt()
                            ?: 0

                        val janky = JANKY_REGEX
                            .find(output)
                            ?.groupValues?.get(1)
                            ?.toIntOrNull()
                            ?: 0

                        val total = TOTAL_FRAMES_REGEX
                            .find(output)
                            ?.groupValues?.get(1)
                            ?.toIntOrNull()
                            ?: 0

                        if (parsed > 0) lastKnownFps = parsed
                        val frameTimeMs = estimateWorstFrameTimeMs(lastKnownFps, janky)
                        val status = if (output.isBlank()) MetricReadStatus.EmptyOutput else MetricReadStatus.Ok

                        emit(
                            FpsState(
                                fps = lastKnownFps,
                                jankyFrames = janky,
                                totalFrames = total,
                                maxFrameTimeMs = frameTimeMs,
                                readStatus = status
                            )
                        )

                        if (System.currentTimeMillis() % 3000L < 1000L) {
                            shizukuManager.executeCommand(
                                "dumpsys SurfaceFlinger --timestats -clear -enable"
                            )
                        }
                    }
                } catch (e: Exception) {
                    emit(FpsState(fps = lastKnownFps, readStatus = MetricReadStatus.ParseFailed))
                }
            } else {
                initialized = false
                lastKnownFps = 0
                emit(FpsState(fps = 0, readStatus = MetricReadStatus.NoShizuku))
            }
            delay(1000)
        }
    }

    private fun estimateWorstFrameTimeMs(fps: Int, jankyFrames: Int): Int {
        if (fps <= 0) return 0
        val baseMs = 1000f / fps.coerceAtLeast(1)
        return if (jankyFrames > 0) {
            (baseMs * (1f + (jankyFrames.coerceAtMost(10) * 0.35f))).roundToInt().coerceAtMost(250)
        } else {
            baseMs.roundToInt()
        }
    }

    companion object {
        private val FPS_REGEX = Regex("averageFPS\\s*=\\s*([0-9.]+)")
        private val JANKY_REGEX = Regex("missedFrames\\s*=\\s*([0-9]+)")
        private val TOTAL_FRAMES_REGEX = Regex("totalFrames\\s*=\\s*([0-9]+)")
    }
}

@Singleton
class CpuMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    /**
     * Unified O(1) CPU telemetry stream emitting peak MHz, total usage %,
     * and dynamic 1..N cluster states from a single read cycle per second.
     */
    val cpuSnapshot: Flow<CpuTelemetrySnapshot> = flow {
        var previousTimes: Map<Int, CpuTimes> = emptyMap()
        while (true) {
            val shizukuReady = shizukuManager.isShizukuAvailable.value &&
                shizukuManager.hasPermission.value

            val raw = if (shizukuReady) {
                runCatching { shizukuManager.readCpuTelemetry() }.getOrDefault("")
            } else {
                ""
            }

            val parsed = if (raw.isNotBlank()) {
                CpuTopologyParser.parseTelemetryPayload(raw, previousTimes)
            } else {
                CpuTopologyParser.readLocalFallback(previousTimes, shizukuReady)
            }

            previousTimes = parsed.second
            emit(parsed.first)
            delay(1000)
        }
    }
}

@Singleton
class RamMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class RamState(
        val usedGb: Float,
        val totalGb: Float,
        val readStatus: MetricReadStatus = MetricReadStatus.Ok
    )

    val ramUsage: Flow<RamState> = flow {
        while (true) {
            val state = try {
                parseMemInfoFast() ?: fallbackRam(context)
            } catch (e: Exception) {
                fallbackRam(context)
            }
            emit(state)
            delay(2000)
        }
    }

    /**
     * O(1) early-exit reader for /proc/meminfo.
     * MemTotal and MemAvailable reside within the first 5 lines on all Linux 3.14+ kernels.
     */
    private fun parseMemInfoFast(): RamState? {
        val file = java.io.File("/proc/meminfo")
        if (!file.exists() || !file.canRead()) return null

        var totalKb = -1L
        var availKb = -1L
        var freeKb = 0L
        var buffersKb = 0L
        var cachedKb = 0L

        BufferedReader(FileReader(file), 512).use { reader ->
            var line: String? = reader.readLine()
            var linesRead = 0
            while (line != null && linesRead < 12) {
                linesRead++
                when {
                    line.startsWith("MemTotal:") -> totalKb = extractKbValue(line)
                    line.startsWith("MemAvailable:") -> availKb = extractKbValue(line)
                    line.startsWith("MemFree:") -> freeKb = extractKbValue(line)
                    line.startsWith("Buffers:") -> buffersKb = extractKbValue(line)
                    line.startsWith("Cached:") -> cachedKb = extractKbValue(line)
                }
                if (totalKb > 0L && availKb >= 0L) break
                line = reader.readLine()
            }
        }

        if (totalKb <= 0L) return null
        val usedKb = if (availKb >= 0L) {
            (totalKb - availKb).coerceAtLeast(0L)
        } else {
            (totalKb - freeKb - buffersKb - cachedKb).coerceAtLeast(0L)
        }
        return RamState(
            usedGb = usedKb.toFloat() / KB_PER_GB,
            totalGb = totalKb.toFloat() / KB_PER_GB,
            readStatus = MetricReadStatus.Ok
        )
    }

    private fun extractKbValue(line: String): Long {
        var acc = 0L
        var foundDigit = false
        for (i in 0 until line.length) {
            val ch = line[i]
            if (ch in '0'..'9') {
                foundDigit = true
                acc = acc * 10L + (ch - '0')
            } else if (foundDigit) {
                break
            }
        }
        return if (foundDigit) acc else -1L
    }

    private fun fallbackRam(context: Context): RamState {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return RamState(
            usedGb = (info.totalMem - info.availMem).toFloat() / BYTES_PER_GB,
            totalGb = info.totalMem.toFloat() / BYTES_PER_GB,
            readStatus = MetricReadStatus.Ok
        )
    }

    companion object {
        private const val KB_PER_GB = 1024f * 1024f
        private const val BYTES_PER_GB = 1024f * 1024f * 1024f
    }
}

@Singleton
class NetworkMonitor @Inject constructor() {
    data class NetworkState(
        val rxSpeedKbps: Float,
        val txSpeedKbps: Float,
        val readStatus: MetricReadStatus = MetricReadStatus.Ok
    )

    val networkSpeed: Flow<NetworkState> = flow {
        if (TrafficStats.getTotalRxBytes() == TrafficStats.UNSUPPORTED.toLong()) {
            while (true) {
                emit(NetworkState(0f, 0f, MetricReadStatus.HardwareUnsupported))
                delay(2000)
            }
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
                emit(
                    NetworkState(
                        rxSpeedKbps = rxSpeedKbps.coerceAtLeast(0f),
                        txSpeedKbps = txSpeedKbps.coerceAtLeast(0f),
                        readStatus = MetricReadStatus.Ok
                    )
                )
            } else {
                emit(NetworkState(0f, 0f, MetricReadStatus.NoData))
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
    data class BatteryState(
        val tempC: Float = 0f,
        val level: Int = -1,
        val voltageMv: Int = 0,
        val currentMa: Float = 0f,
        val powerWatts: Float = 0f,
        val readStatus: MetricReadStatus = MetricReadStatus.Ok
    )

    val batteryState: Flow<BatteryState> = flow {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        while (true) {
            emit(readBatterySnapshot(context, batteryManager))
            delay(BATTERY_POLL_INTERVAL_MS)
        }
    }

    val batteryTemp: Flow<Float> = flow {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        while (true) {
            emit(readBatterySnapshot(context, batteryManager).tempC)
            delay(BATTERY_POLL_INTERVAL_MS)
        }
    }

    val batteryLevel: Flow<Int> = flow {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        while (true) {
            emit(readBatterySnapshot(context, batteryManager).level)
            delay(BATTERY_POLL_INTERVAL_MS)
        }
    }

    private fun readBatterySnapshot(
        context: Context,
        batteryManager: BatteryManager?
    ): BatteryState {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = rawTemp / 10.0f

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val normalizedLevel = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            -1
        }

        val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        // Some OEMs report voltage in mV (e.g., 3850) and a few report in uV or V.
        val voltageMv = when {
            rawVoltage > 100_000 -> rawVoltage / 1000
            rawVoltage in 1..20 -> rawVoltage * 1000
            else -> rawVoltage
        }

        val rawCurrent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val currentMa = if (rawCurrent != Int.MIN_VALUE && rawCurrent != 0) {
            val absVal = abs(rawCurrent.toLong()).toFloat()
            // Normalize uA (> 20,000) vs mA
            if (absVal > 20_000f) absVal / 1000f else absVal
        } else {
            0f
        }

        val powerWatts = if (voltageMv > 0 && currentMa > 0f) {
            ((voltageMv.toFloat() * currentMa) / 1_000_000f).coerceIn(0f, 35f)
        } else {
            0f
        }

        return BatteryState(
            tempC = tempC,
            level = normalizedLevel,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerWatts = powerWatts,
            readStatus = if (normalizedLevel >= 0 || tempC > 0f) MetricReadStatus.Ok else MetricReadStatus.NoData
        )
    }

    companion object {
        private const val BATTERY_POLL_INTERVAL_MS = 2500L
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
        val hasBattery: Boolean = false,
        val isThrottling: Boolean = false
    ) {
        val statusLabel: String
            get() = when (status) {
                0 -> "Normal"
                1 -> "Light Throttling"
                2 -> "Moderate Throttling"
                3 -> "Severe Throttling"
                4 -> "Critical Throttling"
                5 -> "Emergency Mitigation"
                6 -> "System Shutdown"
                else -> "Unknown"
            }

        val pressureLabel: String
            get() = when (status) {
                0 -> if (isThrottling) "Throttled" else "Stable"
                1, 2 -> "Rising"
                else -> "Elevated"
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
                            val status = powerManager.thermalStatusOrDefault()
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
                                val status = parsed?.thermalStatus?.takeIf { it > 0 } ?: powerManager.thermalStatusOrDefault()
                                val statusType = if (parsed?.halNotReady == true) {
                                    MetricReadStatus.HardwareUnsupported
                                } else {
                                    MetricReadStatus.ParseFailed
                                }
                                ThermalState(status = status, readStatus = statusType, isThrottling = parsed?.isThrottling == true)
                            }
                        } else {
                            consecutiveFailures = 0
                            val effectiveStatus = parsed.thermalStatus.takeIf { it > 0 } ?: powerManager.thermalStatusOrDefault()
                            val newState = ThermalState(
                                cpuC = parsed.cpuC ?: 0f,
                                gpuC = parsed.gpuC ?: 0f,
                                npuC = parsed.npuC ?: 0f,
                                skinC = parsed.skinC ?: 0f,
                                batteryC = parsed.batteryC ?: 0f,
                                status = effectiveStatus,
                                readStatus = MetricReadStatus.Ok,
                                hasCpu = parsed.cpuC != null,
                                hasGpu = parsed.gpuC != null,
                                hasNpu = parsed.npuC != null,
                                hasSkin = parsed.skinC != null,
                                hasBattery = parsed.batteryC != null,
                                isThrottling = parsed.isThrottling
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
                        val status = powerManager.thermalStatusOrDefault()
                        ThermalState(status = status, readStatus = MetricReadStatus.ParseFailed)
                    }
                }
            } else {
                consecutiveFailures = 0
                lastGoodState = null
                val status = powerManager.thermalStatusOrDefault()
                ThermalState(status = status, readStatus = MetricReadStatus.NoShizuku)
            }
            emit(state)
            delay(2000)
        }
    }
}

private fun PowerManager.thermalStatusOrDefault(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) currentThermalStatus else 0

data class PingResult(
    val pingMs: Int,
    val readStatus: MetricReadStatus
)

@Singleton
class PingMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }

    val ping: Flow<PingResult> = flow {
        while (true) {
            val output = if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
                try {
                    shizukuManager.executeCommand("ping -c 1 -w 2 8.8.8.8")
                } catch (e: Exception) {
                    executePing()
                }
            } else {
                executePing()
            }

            val result = parsePingOutput(output)
            emit(result)
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun parsePingOutput(output: String): PingResult {
        if (output.isBlank()) return PingResult(0, MetricReadStatus.EmptyOutput)

        return if (output.contains("time=")) {
            val ms = output.split("time=").getOrNull(1)
                ?.split(" ")?.getOrNull(0)
                ?.toFloatOrNull()
                ?.roundToInt() ?: 0
            if (ms > 0) {
                PingResult(ms, MetricReadStatus.Ok)
            } else {
                PingResult(0, MetricReadStatus.ParseFailed)
            }
        } else {
            PingResult(0, MetricReadStatus.NoData)
        }
    }

    private fun executePing(): String {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -w 2 8.8.8.8")
            try {
                process.inputStream.bufferedReader().use { it.readText() }
            } finally {
                process.destroy()
            }
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
        val topProcesses: List<CpuInfoTopParser.TopProcess> = emptyList(),
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
                        val parsedList = CpuInfoTopParser.parseTopProcesses(output, limit = 5)
                        val topOne = parsedList.firstOrNull()
                        if (topOne != null) {
                            TopProcess(
                                name = topOne.name,
                                cpuPercent = topOne.cpuPercent,
                                topProcesses = parsedList,
                                readStatus = MetricReadStatus.Ok
                            )
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
            delay(2000)
        }
    }
}
