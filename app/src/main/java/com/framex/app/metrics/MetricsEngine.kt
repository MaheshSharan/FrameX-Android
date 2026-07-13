package com.framex.app.metrics

import com.framex.app.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class MetricsState(
    val fps: Int = 0,
    val jankyFrames: Int = 0,
    val cpuMhz: Int = 0,
    val cpuPercentage: Int? = null,
    val cpuClusterUltraMhz: Int = 0,
    val cpuClusterPerfMhz: Int = 0,
    val cpuClusterEffMhz: Int = 0,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val batteryTempC: Float = 0f,
    val networkRxKbps: Float = 0f,
    val networkTxKbps: Float = 0f,
    val pingMs: Int = 0,
    // Full thermal breakdown — see ThermalMonitor.ThermalState for field meaning.
    val thermalCpuC: Float = 0f,
    val thermalGpuC: Float = 0f,
    val thermalNpuC: Float = 0f,
    val thermalSkinC: Float = 0f,
    val thermalStatus: Int = 0,
    // Busiest process at this tick (see TopProcessMonitor) — names the likely
    // culprit when a frame drop isn't explained by thermal or frequency alone.
    val topProcessName: String? = null,
    val topProcessCpuPercent: Float = 0f
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
    private val topProcessMonitor: TopProcessMonitor,
    private val settingsRepository: SettingsRepository
) {
    private val _metricsState = MutableStateFlow(MetricsState())
    val metricsState: StateFlow<MetricsState> = _metricsState.asStateFlow()

    // Rolling window of the last 60 FPS readings (≈ 60 seconds at 1s poll rate).
    // Used by the Dashboard chart to show a real sparkline from recent samples.
    private val _fpsHistory = MutableStateFlow<List<Int>>(emptyList())
    val fpsHistory: StateFlow<List<Int>> = _fpsHistory.asStateFlow()

    // Timestamped snapshot of the full metrics state, appended every ~1s regardless
    // of which modules are enabled for the overlay. This is the source of truth for
    // the "what caused the frame drop" correlation graph and for session log export —
    // both need every metric on the same timeline, not just what the user chose to
    // display on-screen. Capped to avoid unbounded memory growth during long sessions.
    data class MetricsSnapshot(val timestampMs: Long, val state: MetricsState)
    private val _snapshotHistory = MutableStateFlow<List<MetricsSnapshot>>(emptyList())
    val snapshotHistory: StateFlow<List<MetricsSnapshot>> = _snapshotHistory.asStateFlow()

    // SupervisorJob: one failing monitor coroutine never cancels the others.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moduleJobs = mutableMapOf<String, Job>()

    // Transient, NOT persisted, NOT shown in the overlay toggle UI.
    // Multiple independent callers (a screen wanting live readings, a recording
    // session wanting the full diagnostic set) can each hold their own named
    // request here without one clobbering the other when either releases its set.
    // The overlay (OverlayManager/AppearanceScreen) reads settingsRepository.enabledModules
    // directly, so anything added here never affects what the overlay displays.
    private val _screenOverrideRequests = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val screenOverrideModules = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    /** Request that [modules] keep polling regardless of the persisted overlay toggle,
     *  under [requesterKey] so unrelated requesters don't clobber each other.
     *  Pass emptySet() to release this requester's request. */
    fun setScreenOverrideModules(modules: Set<String>, requesterKey: String = "default") {
        val next = if (modules.isEmpty()) {
            _screenOverrideRequests.value - requesterKey
        } else {
            _screenOverrideRequests.value + (requesterKey to modules)
        }
        _screenOverrideRequests.value = next
        screenOverrideModules.value = next.values.flatten().toSet()
    }

    init {
        // FPS always runs — it is the core metric and has near-zero overhead.
        engineScope.launch {
            fpsMonitor.fpsState.collect { state ->
                _metricsState.value = _metricsState.value.copy(fps = state.fps, jankyFrames = state.jankyFrames)
                // Append to rolling history, capped at 60 entries.
                val next = ArrayDeque(_fpsHistory.value).also { d ->
                    d.addLast(state.fps)
                    if (d.size > 60) d.removeFirst()
                }
                _fpsHistory.value = next.toList()

                // Snapshot the full state at this tick for logging/correlation, capped
                // at MAX_SNAPSHOTS (~1hr at 1s cadence) so long sessions don't grow unbounded.
                val snapshots = ArrayDeque(_snapshotHistory.value).also { d ->
                    d.addLast(MetricsSnapshot(System.currentTimeMillis(), _metricsState.value))
                    if (d.size > MAX_SNAPSHOTS) d.removeFirst()
                }
                _snapshotHistory.value = snapshots.toList()
            }
        }

        // All other monitors only run while their module toggle is enabled by the user
        // (settingsRepository.enabledModules) OR temporarily requested by a screen/recording
        // (screenOverrideModules). The overlay only ever looks at the former, so a
        // screen override never changes what the overlay shows — only what gets measured.
        // This means zero polling happens for disabled modules — no wasted CPU, battery, or Shizuku calls.
        engineScope.launch {
            combine(
                settingsRepository.enabledModules,
                screenOverrideModules
            ) { persisted, override -> persisted + override }
                .collect { enabled ->
                toggleModule("cpu", enabled) {
                    kotlinx.coroutines.coroutineScope {
                        launch {
                            cpuMonitor.cpuUsage.collect {
                                _metricsState.value = _metricsState.value.copy(cpuMhz = it)
                            }
                        }
                        launch {
                            cpuMonitor.cpuPercentageUsage.collect {
                                _metricsState.value = _metricsState.value.copy(cpuPercentage = it)
                            }
                        }
                    }
                }
                toggleModule("cpu_cluster", enabled) {
                    cpuMonitor.cpuClusterUsage.collect { clusters ->
                        _metricsState.value = _metricsState.value.copy(
                            cpuClusterEffMhz = clusters.effMhz,
                            cpuClusterPerfMhz = clusters.perfMhz,
                            cpuClusterUltraMhz = clusters.ultraMhz
                        )
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
                    thermalMonitor.thermalState.collect { t ->
                        _metricsState.value = _metricsState.value.copy(
                            thermalCpuC = t.cpuC,
                            thermalGpuC = t.gpuC,
                            thermalNpuC = t.npuC,
                            thermalSkinC = t.skinC,
                            thermalStatus = t.status
                        )
                    }
                }
                toggleModule("ping", enabled) {
                    pingMonitor.ping.collect {
                        _metricsState.value = _metricsState.value.copy(pingMs = it)
                    }
                }
                toggleModule("top_process", enabled) {
                    topProcessMonitor.topProcess.collect { top ->
                        _metricsState.value = _metricsState.value.copy(
                            topProcessName = top?.name,
                            topProcessCpuPercent = top?.cpuPercent ?: 0f
                        )
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
                "cpu"         -> _metricsState.value.copy(cpuMhz = 0, cpuPercentage = null)
                "cpu_cluster" -> _metricsState.value.copy(
                    cpuClusterEffMhz = 0,
                    cpuClusterPerfMhz = 0,
                    cpuClusterUltraMhz = 0
                )
                "ram"     -> _metricsState.value.copy(ramUsedGb = 0f, ramTotalGb = 0f)
                "net"     -> _metricsState.value.copy(networkRxKbps = 0f, networkTxKbps = 0f)
                "temp"    -> _metricsState.value.copy(batteryTempC = 0f)
                "thermal" -> _metricsState.value.copy(
                    thermalCpuC = 0f, thermalGpuC = 0f, thermalNpuC = 0f,
                    thermalSkinC = 0f, thermalStatus = 0
                )
                "ping"    -> _metricsState.value.copy(pingMs = 0)
                "top_process" -> _metricsState.value.copy(topProcessName = null, topProcessCpuPercent = 0f)
                else      -> _metricsState.value
            }
        }
    }

    companion object {
        // ~1 hour of history at 1s cadence. Long enough for a full gaming session's
        // worth of correlation data without holding unbounded memory.
        private const val MAX_SNAPSHOTS = 3600
    }
}
