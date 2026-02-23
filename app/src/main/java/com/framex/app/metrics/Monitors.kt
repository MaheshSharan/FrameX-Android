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

@Singleton
class FpsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
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
    val framesPerSecond: Flow<Int> = flow {
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

                        // Step 2: show result — hold last known if dump returned nothing
                        if (parsed > 0) lastKnownFps = parsed
                        emit(lastKnownFps)

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
                    emit(lastKnownFps) // hold last known; never flash 0 on transient error
                }
            } else {
                initialized = false
                lastKnownFps = 0
                emit(0)
            }
            delay(1000)
        }
    }
}

@Singleton
class CpuMonitor @Inject constructor() {
    // Exact same approach as PerfStats: read cpu0 current clock frequency from sysfs.
    // This file is world-readable on all Android devices — no Shizuku required.
    // Returns kHz; divide by 1000 = MHz.
    val cpuUsage: Flow<Int> = flow {
        while (true) {
            try {
                val raw = java.io.File(
                    "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
                ).readText().trim()
                val mhz = (raw.toIntOrNull() ?: 0) / 1000
                emit(mhz)
            } catch (e: Exception) {
                emit(0)
            }
            delay(1000)
        }
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
            val state = if (shizukuManager.isShizukuAvailable.value &&
                shizukuManager.hasPermission.value) {
                // Exact PerfStats command: "free -m | grep Mem"
                // Output: "Mem: <total> <used> <free> <shared> <buf/cache> <available>"
                try {
                    val output = shizukuManager.executeCommand("free -m | grep Mem")
                    val parts = output.trim().split("\\s+".toRegex())
                    val totalMb = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                    val usedMb = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                    RamState(usedMb / 1024f, totalMb / 1024f)
                } catch (e: Exception) {
                    fallbackRam(context)
                }
            } else {
                fallbackRam(context)
            }
            emit(state)
            delay(2000)
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
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    val batteryTemp: Flow<Float> = flow {
        while (true) {
            val temp = if (shizukuManager.isShizukuAvailable.value &&
                shizukuManager.hasPermission.value) {
                // Exact PerfStats command: "dumpsys battery | grep temperature"
                // Output line: "  temperature: 280"  → strip non-digits → 280 ÷ 10 = 28.0°C
                try {
                    val output = shizukuManager.executeCommand(
                        "dumpsys battery | grep temperature"
                    )
                    val digits = output.replace(Regex("\\D"), "")
                    if (digits.isNotEmpty()) digits.toInt() / 10.0f else fallbackTemp(context)
                } catch (e: Exception) {
                    fallbackTemp(context)
                }
            } else {
                fallbackTemp(context)
            }
            emit(temp)
            delay(5000)
        }
    }

    private fun fallbackTemp(context: Context): Float {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val raw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return raw / 10.0f
    }
}

@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isThrottling: Flow<Boolean> = flow {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        while (true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val status = powerManager.currentThermalStatus
                emit(status >= PowerManager.THERMAL_STATUS_SEVERE)
            } else {
                emit(false)
            }
            delay(5000)
        }
    }
}

@Singleton
class PingMonitor @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    val ping: Flow<Int> = flow {
        while (true) {
            if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
                try {
                    // Exact PerfStats command: "ping -c 1 google.com"
                    // Parse: split on "time=" → take [1] → split on " " → take [0] = "12.4"
                    val output = shizukuManager.executeCommand("ping -c 1 google.com")
                    val pingMs = if (output.contains("time=")) {
                        output.split("time=").getOrNull(1)
                            ?.split(" ")?.getOrNull(0)
                            ?.toFloatOrNull()
                            ?.roundToInt() ?: 0
                    } else 0
                    emit(pingMs)
                } catch (e: Exception) {
                    emit(0)
                }
            } else {
                emit(0)
            }
            delay(3000)
        }
    }
}
