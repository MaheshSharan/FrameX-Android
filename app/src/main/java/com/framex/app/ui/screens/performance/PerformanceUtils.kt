package com.framex.app.ui.screens.performance

import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Pure calculation, diagnostic logic, and system probes for Performance Screen.
 * Adheres to AGENTS.md Section 2.1 modular screen architecture.
 */
object PerformanceUtils {
    const val BYTES_TO_MB = 1024L * 1024L
    const val SOCKET_TIMEOUT_MS = 1000
    const val RETRY_DELAY_MS = 150L

    suspend fun manualBoostRam(
        whitelist: Set<String>,
        deviceDiagnosticManager: DeviceDiagnosticManager,
        shizukuManager: ShizukuManager,
        gamingModeEngine: GamingModeEngine
    ): Pair<Long, Int> {
        val availBefore = deviceDiagnosticManager.getAvailableMemoryBytes()
        var stoppedCount = 0
        if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
            try {
                shizukuManager.executeCommand("pm trim-caches 4G")
                val targets = withContext(Dispatchers.IO) {
                    gamingModeEngine.getInstalledUserApps()
                        .filter { it.packageName !in whitelist }
                }
                for (app in targets) {
                    try {
                        shizukuManager.executeCommand("am force-stop ${app.packageName}")
                        stoppedCount++
                    } catch (e: Exception) {
                        FrameXLog.w("Failed to force-stop ${app.packageName}", e)
                    }
                }
                shizukuManager.executeCommand("am kill-all")
            } catch (e: Exception) {
                FrameXLog.w("Error during manual RAM boost via Shizuku", e)
            }
        }
        System.gc()

        val availAfter = deviceDiagnosticManager.getAvailableMemoryBytes()
        val freed = ((availAfter - availBefore) / BYTES_TO_MB).coerceAtLeast(0L)
        return Pair(freed, stoppedCount)
    }

    suspend fun measureNetworkLatency(shizukuManager: ShizukuManager): Int? {
        if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
            try {
                val output = shizukuManager.executeCommand("ping -c 1 8.8.8.8")
                if (output.contains("time=")) {
                    val pingMs = output.split("time=").getOrNull(1)
                        ?.split(" ")?.getOrNull(0)
                        ?.toFloatOrNull()
                        ?.toInt()
                    if (pingMs != null && pingMs > 0) return pingMs
                }
            } catch (e: Exception) {
                FrameXLog.w("Shizuku ping check failed, falling back to socket probe", e)
            }
        }
        var minPing: Int? = null
        for (i in 1..3) {
            try {
                val start = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress("8.8.8.8", 53), SOCKET_TIMEOUT_MS)
                val latency = (System.currentTimeMillis() - start).toInt()
                socket.close()
                minPing = minOf(minPing ?: latency, latency)
            } catch (e: Exception) {
                FrameXLog.w("Socket ping probe iteration $i failed", e)
            }
            delay(RETRY_DELAY_MS)
        }
        return minPing
    }
}
