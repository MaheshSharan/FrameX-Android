package com.framex.app.metrics.monitors

import com.framex.app.metrics.MetricReadStatus
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.utils.FrameXLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class PingResult(
    val pingMs: Int,
    val readStatus: MetricReadStatus
)

@Singleton
class PingMonitor @Inject constructor(
    private val privilegeManager: PrivilegeManager
) {
    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }

    val ping: Flow<PingResult> = flow {
        while (true) {
            val output = if (privilegeManager.isReady.value) {
                try {
                    privilegeManager.execText("ping -c 1 -w 2 8.8.8.8")
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
            FrameXLog.w("executePing failed", e)
            ""
        }
    }
}
