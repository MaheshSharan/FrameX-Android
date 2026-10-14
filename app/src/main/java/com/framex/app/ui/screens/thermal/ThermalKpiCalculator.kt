package com.framex.app.ui.screens.thermal

import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import java.util.Locale
import kotlin.math.abs

/**
 * Pure single-pass O(N) KPI calculator for per-app session profiling:
 * Computes MAX/MIN/AVG FPS, Jitter %, 1% Low FPS, SoC Temp stats, High-Temp Duration,
 * Battery Temp Max, Power (W) Max/Min/Avg, and worst Frame Time (ms).
 */
fun computeSessionKpiSummary(
    snapshots: List<MetricsEngine.MetricsSnapshot>,
    highTempThresholdC: Float = SessionKpiSummary.HIGH_SOC_TEMP_THRESHOLD_C
): SessionKpiSummary {
    if (snapshots.isEmpty()) return SessionKpiSummary()

    val n = snapshots.size
    val validFps = IntArray(n)
    var fpsCount = 0

    var fpsSum = 0L
    var maxFps = 0
    var minFps = Int.MAX_VALUE
    var frameDeltaSum = 0f
    var frameDeltaCount = 0
    var prevFps = -1

    var socSum = 0f
    var socCount = 0
    var maxSoc = 0f
    var minSoc = Float.MAX_VALUE
    var highTempSec = 0

    var maxBat = 0f
    var powerSum = 0f
    var powerCount = 0
    var maxPower = 0f
    var minPower = Float.MAX_VALUE
    var maxFrameTime = 0

    for (i in 0 until n) {
        val s = snapshots[i].state
        val fps = s.fps
        if (fps > 0) {
            validFps[fpsCount++] = fps
            fpsSum += fps
            if (fps > maxFps) maxFps = fps
            if (fps < minFps) minFps = fps
            if (prevFps > 0) {
                frameDeltaSum += abs(fps - prevFps).toFloat()
                frameDeltaCount++
            }
            prevFps = fps
        }

        if (s.frameTimeMs > maxFrameTime) {
            maxFrameTime = s.frameTimeMs
        }

        val socC = if (s.thermalCpuC > 0f) s.thermalCpuC else s.batteryTempC
        if (socC > 0f) {
            socSum += socC
            socCount++
            if (socC > maxSoc) maxSoc = socC
            if (socC < minSoc) minSoc = socC
            if (socC >= highTempThresholdC) {
                highTempSec += 1
            }
        }

        if (s.batteryTempC > maxBat) {
            maxBat = s.batteryTempC
        }

        if (s.powerWatts > 0f) {
            powerSum += s.powerWatts
            powerCount++
            if (s.powerWatts > maxPower) maxPower = s.powerWatts
            if (s.powerWatts < minPower) minPower = s.powerWatts
        }
    }

    val avgFps = if (fpsCount > 0) fpsSum.toFloat() / fpsCount else 0f
    val resolvedMinFps = if (minFps == Int.MAX_VALUE) 0f else minFps.toFloat()

    val onePercentLow = if (fpsCount > 0) {
        validFps.sort(0, fpsCount)
        val tailCount = (fpsCount / 100).coerceAtLeast(1)
        var tailSum = 0L
        for (idx in 0 until tailCount) {
            tailSum += validFps[idx]
        }
        tailSum.toFloat() / tailCount
    } else {
        0f
    }

    val jitterPct = if (avgFps > 0f && frameDeltaCount > 0) {
        val meanDelta = frameDeltaSum / frameDeltaCount
        ((meanDelta / avgFps) * 100f).coerceIn(0f, 100f)
    } else {
        0f
    }

    val durationSec = if (n >= 2) {
        ((snapshots.last().timestampMs - snapshots.first().timestampMs) / 1000L).toInt().coerceAtLeast(n)
    } else {
        n
    }

    return SessionKpiSummary(
        sampleCount = n,
        durationSec = durationSec,
        maxFps = maxFps.toFloat(),
        minFps = resolvedMinFps,
        avgFps = avgFps,
        jitterPercent = jitterPct,
        onePercentLowFps = onePercentLow,
        avgSocTempC = if (socCount > 0) socSum / socCount else 0f,
        maxSocTempC = maxSoc,
        minSocTempC = if (minSoc == Float.MAX_VALUE) 0f else minSoc,
        highTempDurationSec = highTempSec,
        maxBatteryTempC = maxBat,
        avgPowerWatts = if (powerCount > 0) powerSum / powerCount else 0f,
        maxPowerWatts = maxPower,
        minPowerWatts = if (minPower == Float.MAX_VALUE) 0f else minPower,
        maxFrameTimeMs = maxFrameTime
    )
}

fun buildDiagnosticSummaryText(
    state: MetricsState,
    snapshots: List<MetricsEngine.MetricsSnapshot>
): String {
    val kpi = computeSessionKpiSummary(
        snapshots.ifEmpty {
            listOf(MetricsEngine.MetricsSnapshot(System.currentTimeMillis(), state))
        }
    )
    val topProcess = state.topProcessName ?: "None"
    val statusText = getThermalStatusLabel(state.thermalStatus)

    return """
### FrameX Performance & Thermal Diagnostic Report
- **Device**: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})
- **System Thermal State**: $statusText
- **FPS (Max / Avg / Min / 1% Low)**: ${String.format(Locale.US, "%.0f / %.1f / %.0f / %.1f FPS", kpi.maxFps, kpi.avgFps, kpi.minFps, kpi.onePercentLowFps)}
- **Frame Jitter**: ${String.format(Locale.US, "%.1f%%", kpi.jitterPercent)} (Max Frame Time: ${kpi.maxFrameTimeMs} ms)
- **SoC Temp (Max / Avg)**: ${String.format(Locale.US, "%.1f°C / %.1f°C", kpi.maxSocTempC, kpi.avgSocTempC)}
- **Battery Peak / Avg Power**: ${String.format(Locale.US, "%.1f°C / %.2f W", kpi.maxBatteryTempC, kpi.avgPowerWatts)}
- **Top Process**: $topProcess (${String.format(Locale.US, "%.0f%%", state.topProcessCpuPercent)})
""".trimIndent()
}
