package com.framex.app.metrics.monitors

import com.framex.app.metrics.CpuTelemetrySnapshot
import com.framex.app.metrics.CpuTimes
import com.framex.app.metrics.CpuTopologyParser
import com.framex.app.shizuku.ShizukuManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

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
