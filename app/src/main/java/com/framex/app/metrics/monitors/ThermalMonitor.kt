package com.framex.app.metrics.monitors

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.framex.app.metrics.MetricReadStatus
import com.framex.app.metrics.ThermalServiceParser
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

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
