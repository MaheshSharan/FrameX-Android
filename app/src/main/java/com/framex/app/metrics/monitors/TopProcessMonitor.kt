package com.framex.app.metrics.monitors

import com.framex.app.metrics.CpuInfoTopParser
import com.framex.app.metrics.MetricReadStatus
import com.framex.app.shizuku.ShizukuManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

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
