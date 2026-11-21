package com.framex.app.metrics.monitors

import android.app.ActivityManager
import android.content.Context
import com.framex.app.metrics.MetricReadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import javax.inject.Inject
import javax.inject.Singleton

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
        val file = File("/proc/meminfo")
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
