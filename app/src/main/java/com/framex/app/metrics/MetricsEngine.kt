package com.framex.app.metrics

import com.framex.app.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class MetricsState(
    val fps: Int = 0,
    val cpuMhz: Int = 0,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val batteryTempC: Float = 0f,
    val networkRxKbps: Float = 0f,
    val networkTxKbps: Float = 0f,
    val pingMs: Int = 0,
    val isThermalThrottling: Boolean = false
)

@Singleton
class MetricsEngine @Inject constructor(
    private val fpsMonitor: FpsMonitor,
    private val cpuMonitor: CpuMonitor,
    private val ramMonitor: RamMonitor,
    private val networkMonitor: NetworkMonitor,
    private val batteryMonitor: BatteryMonitor,
    private val thermalMonitor: ThermalMonitor,
    private val pingMonitor: PingMonitor,
    private val settingsRepository: SettingsRepository
) {
    private val _metricsState = MutableStateFlow(MetricsState())
    val metricsState: StateFlow<MetricsState> = _metricsState.asStateFlow()

    // Rolling window of the last 60 FPS readings (≈ 60 seconds at 1s poll rate).
    // Used by the Dashboard chart to show a real sparkline instead of fake static data.
    private val _fpsHistory = MutableStateFlow<List<Int>>(emptyList())
    val fpsHistory: StateFlow<List<Int>> = _fpsHistory.asStateFlow()

    // SupervisorJob: one failing monitor coroutine never cancels the others.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moduleJobs = mutableMapOf<String, Job>()

    init {
        // FPS always runs — it is the core metric and has near-zero overhead.
        engineScope.launch {
            fpsMonitor.framesPerSecond.collect { fps ->
                _metricsState.value = _metricsState.value.copy(fps = fps)
                // Append to rolling history, capped at 60 entries.
                val next = ArrayDeque(_fpsHistory.value).also { d ->
                    d.addLast(fps)
                    if (d.size > 60) d.removeFirst()
                }
                _fpsHistory.value = next.toList()
            }
        }

        // All other monitors only run while their module toggle is enabled by the user.
        // This means zero polling happens for disabled modules — no wasted CPU, battery, or Shizuku calls.
        engineScope.launch {
            settingsRepository.enabledModules.collect { enabled ->
                toggleModule("cpu", enabled) {
                    cpuMonitor.cpuUsage.collect {
                        _metricsState.value = _metricsState.value.copy(cpuMhz = it)
                    }
                }
                toggleModule("ram", enabled) {
                    ramMonitor.ramUsage.collect {
                        _metricsState.value = _metricsState.value.copy(
                            ramUsedGb = it.usedGb, ramTotalGb = it.totalGb
                        )
                    }
                }
                toggleModule("net", enabled) {
                    networkMonitor.networkSpeed.collect {
                        _metricsState.value = _metricsState.value.copy(
                            networkRxKbps = it.rxSpeedKbps, networkTxKbps = it.txSpeedKbps
                        )
                    }
                }
                toggleModule("temp", enabled) {
                    batteryMonitor.batteryTemp.collect {
                        _metricsState.value = _metricsState.value.copy(batteryTempC = it)
                    }
                }
                toggleModule("thermal", enabled) {
                    thermalMonitor.isThrottling.collect {
                        _metricsState.value = _metricsState.value.copy(isThermalThrottling = it)
                    }
                }
                toggleModule("ping", enabled) {
                    pingMonitor.ping.collect {
                        _metricsState.value = _metricsState.value.copy(pingMs = it)
                    }
                }
            }
        }
    }

    /** Starts the module coroutine if key is in enabled set; cancels and zeroes it if not. */
    private fun toggleModule(key: String, enabled: Set<String>, block: suspend () -> Unit) {
        if (key in enabled) {
            if (moduleJobs[key]?.isActive == true) return
            moduleJobs[key] = engineScope.launch { block() }
        } else {
            moduleJobs[key]?.cancel()
            moduleJobs.remove(key)
            // Reset state field to zero so the overlay doesn't show stale data.
            _metricsState.value = when (key) {
                "cpu"     -> _metricsState.value.copy(cpuMhz = 0)
                "ram"     -> _metricsState.value.copy(ramUsedGb = 0f, ramTotalGb = 0f)
                "net"     -> _metricsState.value.copy(networkRxKbps = 0f, networkTxKbps = 0f)
                "temp"    -> _metricsState.value.copy(batteryTempC = 0f)
                "thermal" -> _metricsState.value.copy(isThermalThrottling = false)
                "ping"    -> _metricsState.value.copy(pingMs = 0)
                else      -> _metricsState.value
            }
        }
    }
}
