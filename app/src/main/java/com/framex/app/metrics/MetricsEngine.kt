package com.framex.app.metrics

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import com.framex.app.gaming.LogStatus
import com.framex.app.gaming.SystemAuditLogRepository
import com.framex.app.metrics.monitors.BatteryMonitor
import com.framex.app.metrics.monitors.CpuMonitor
import com.framex.app.metrics.monitors.FpsMonitor
import com.framex.app.metrics.monitors.NetworkMonitor
import com.framex.app.metrics.monitors.PingMonitor
import com.framex.app.metrics.monitors.RamMonitor
import com.framex.app.metrics.monitors.ThermalMonitor
import com.framex.app.metrics.monitors.TopProcessMonitor
import com.framex.app.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class MetricsState(
    val fps: Int = 0,
    val jankyFrames: Int = 0,
    val totalFrames: Int = 0,
    val frameTimeMs: Int = 0,
    val fpsReadStatus: MetricReadStatus = MetricReadStatus.Loading,
    val cpuMhz: Int = 0,
    val cpuPercentage: Int? = null,
    val cpuClusterUltraMhz: Int = 0,
    val cpuClusterPerfMhz: Int = 0,
    val cpuClusterEffMhz: Int = 0,
    val cpuClusters: List<CpuClusterMetric> = emptyList(),
    val cpuReadStatus: MetricReadStatus = MetricReadStatus.Loading,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val batteryTempC: Float = 0f,
    val batteryLevel: Int = -1,
    val batteryVoltageMv: Int = 0,
    val batteryCurrentMa: Float = 0f,
    val powerWatts: Float = 0f,
    val currentTime: String = "",
    val sessionElapsedSec: Long = 0L,
    val networkRxKbps: Float = 0f,
    val networkTxKbps: Float = 0f,
    val pingMs: Int = 0,
    val pingReadStatus: MetricReadStatus = MetricReadStatus.Loading,
    val thermalCpuC: Float = 0f,
    val thermalGpuC: Float = 0f,
    val thermalNpuC: Float = 0f,
    val thermalSkinC: Float = 0f,
    val thermalStatus: Int = 0,
    val thermalReadStatus: MetricReadStatus = MetricReadStatus.Loading,
    val hasThermalCpu: Boolean = false,
    val hasThermalGpu: Boolean = false,
    val hasThermalNpu: Boolean = false,
    val hasThermalSkin: Boolean = false,
    val hasThermalBattery: Boolean = false,
    val isThrottling: Boolean = false,
    val topProcessName: String? = null,
    val topProcessCpuPercent: Float = 0f,
    val topProcesses: List<CpuInfoTopParser.TopProcess> = emptyList(),
    val topProcessReadStatus: MetricReadStatus = MetricReadStatus.Loading
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
    private val settingsRepository: SettingsRepository,
    private val auditLogRepository: SystemAuditLogRepository
) {
    private val _metricsState = MutableStateFlow(MetricsState())
    val metricsState: StateFlow<MetricsState> = _metricsState.asStateFlow()

    private val _fpsHistory = MutableStateFlow<List<Int>>(emptyList())
    val fpsHistory: StateFlow<List<Int>> = _fpsHistory.asStateFlow()

    data class MetricsSnapshot(val timestampMs: Long, val state: MetricsState)
    private val _snapshotHistory = MutableStateFlow<List<MetricsSnapshot>>(emptyList())
    val snapshotHistory: StateFlow<List<MetricsSnapshot>> = _snapshotHistory.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moduleJobs = mutableMapOf<String, Job>()
    private var snapshotTickJob: Job? = null
    private var sessionStartTimeMs: Long = SystemClock.elapsedRealtime()

    private val fpsRing = ArrayDeque<Int>(MAX_FPS_HISTORY_SIZE + 1)
    private val snapshotRing = ArrayDeque<MetricsSnapshot>(MAX_SNAPSHOTS + 1)
    private val ringLock = Any()

    // Tracks whether the floating overlay window is actively visible on screen.
    // Prevents polling overlay modules when the overlay service is stopped/hidden.
    private val _isOverlayActive = MutableStateFlow(false)

    private val _screenOverrideRequests = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val screenOverrideModules = MutableStateFlow<Set<String>>(emptySet())

    fun setOverlayActive(active: Boolean) {
        _isOverlayActive.value = active
    }

    fun resetSessionTimer() {
        sessionStartTimeMs = SystemClock.elapsedRealtime()
        _metricsState.value = _metricsState.value.copy(sessionElapsedSec = 0L)
    }

    private fun getCurrentFormattedTime(): String {
        val now = LocalTime.now()
        return String.format(Locale.US, "%02d:%02d", now.hour, now.minute)
    }

    /**
     * Request that [modules] poll while a screen or recording session is active,
     * under [requesterKey] so independent callers never clobber one another.
     */
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
        engineScope.launch {
            combine(
                _isOverlayActive,
                settingsRepository.enabledModules,
                screenOverrideModules,
                settingsRepository.isGamingModeActiveFlow
            ) { overlayActive, persistedOverlayMods, screenMods, gamingActive ->
                buildSet {
                    if (overlayActive) addAll(persistedOverlayMods)
                    addAll(screenMods)
                    if (gamingActive) add("thermal")
                }
            }
                .distinctUntilChanged()
                .collect { enabled ->
                    updateActiveCollectors(enabled)
                }
        }
    }

    private fun updateActiveCollectors(enabled: Set<String>) {
        toggleModule("fps", enabled) {
            var loggedFirstFps = false
            fpsMonitor.fpsState.collect { state ->
                _metricsState.value = _metricsState.value.copy(
                    fps = state.fps,
                    jankyFrames = state.jankyFrames,
                    totalFrames = state.totalFrames,
                    frameTimeMs = state.maxFrameTimeMs,
                    fpsReadStatus = state.readStatus
                )
                if (!loggedFirstFps && state.fps > 0) {
                    loggedFirstFps = true
                    auditLogRepository.addLog(
                        "Telemetry: FPS Polling Active",
                        "SurfaceFlinger stream initialized (first reading: ${state.fps} FPS)",
                        LogStatus.SUCCESS
                    )
                }
                val updatedHistory = synchronized(ringLock) {
                    fpsRing.addLast(state.fps)
                    if (fpsRing.size > MAX_FPS_HISTORY_SIZE) fpsRing.removeFirst()
                    ArrayList(fpsRing)
                }
                _fpsHistory.value = updatedHistory
            }
        }

        val needsCpu = "cpu" in enabled || "cpu_cluster" in enabled
        if (needsCpu) {
            if (moduleJobs["cpu_combined"]?.isActive != true) {
                moduleJobs["cpu_combined"] = engineScope.launch {
                    cpuMonitor.cpuSnapshot.collect { snap ->
                        _metricsState.value = _metricsState.value.copy(
                            cpuMhz = if ("cpu" in enabled || "cpu_cluster" in enabled) snap.peakMhz else 0,
                            cpuPercentage = snap.totalUsagePercent,
                            cpuClusterEffMhz = snap.effMhz,
                            cpuClusterPerfMhz = snap.perfMhz,
                            cpuClusterUltraMhz = snap.ultraMhz,
                            cpuClusters = snap.clusters,
                            cpuReadStatus = snap.readStatus
                        )
                    }
                }
            }
        } else {
            moduleJobs["cpu_combined"]?.cancel()
            moduleJobs.remove("cpu_combined")
            _metricsState.value = _metricsState.value.copy(
                cpuMhz = 0,
                cpuPercentage = null,
                cpuClusterEffMhz = 0,
                cpuClusterPerfMhz = 0,
                cpuClusterUltraMhz = 0,
                cpuClusters = emptyList(),
                cpuReadStatus = MetricReadStatus.Loading
            )
        }

        toggleModule("ram", enabled) {
            ramMonitor.ramUsage.collect {
                _metricsState.value = _metricsState.value.copy(
                    ramUsedGb = it.usedGb,
                    ramTotalGb = it.totalGb
                )
            }
        }

        toggleModule("net", enabled) {
            networkMonitor.networkSpeed.collect {
                _metricsState.value = _metricsState.value.copy(
                    networkRxKbps = it.rxSpeedKbps,
                    networkTxKbps = it.txSpeedKbps
                )
            }
        }

        val needsBattery = "temp" in enabled || "battery_level" in enabled || "power" in enabled
        if (needsBattery) {
            if (moduleJobs["battery_combined"]?.isActive != true) {
                moduleJobs["battery_combined"] = engineScope.launch {
                    batteryMonitor.batteryState.collect { b ->
                        _metricsState.value = _metricsState.value.copy(
                            batteryTempC = b.tempC,
                            batteryLevel = b.level,
                            batteryVoltageMv = b.voltageMv,
                            batteryCurrentMa = b.currentMa,
                            powerWatts = b.powerWatts
                        )
                    }
                }
            }
        } else {
            moduleJobs["battery_combined"]?.cancel()
            moduleJobs.remove("battery_combined")
            _metricsState.value = _metricsState.value.copy(
                batteryTempC = 0f,
                batteryLevel = -1,
                batteryVoltageMv = 0,
                batteryCurrentMa = 0f,
                powerWatts = 0f
            )
        }

        toggleModule("thermal", enabled) {
            var lastThermalStatus = -1
            thermalMonitor.thermalState.collect { t ->
                _metricsState.value = _metricsState.value.copy(
                    thermalCpuC = t.cpuC,
                    thermalGpuC = t.gpuC,
                    thermalNpuC = t.npuC,
                    thermalSkinC = t.skinC,
                    thermalStatus = t.status,
                    thermalReadStatus = t.readStatus,
                    hasThermalCpu = t.hasCpu,
                    hasThermalGpu = t.hasGpu,
                    hasThermalNpu = t.hasNpu,
                    hasThermalSkin = t.hasSkin,
                    hasThermalBattery = t.hasBattery,
                    isThrottling = t.isThrottling
                )
                if (t.status != lastThermalStatus) {
                    lastThermalStatus = t.status
                    if (t.status >= 1) {
                        auditLogRepository.addLog(
                            "Thermal Alert",
                            "${t.statusLabel} (CPU: ${t.cpuC}°C, GPU: ${t.gpuC}°C, Skin: ${t.skinC}°C)",
                            LogStatus.FAILED
                        )
                    } else if (lastThermalStatus != -1) {
                        auditLogRepository.addLog(
                            "Thermal Status",
                            "Normal operating temperature restored",
                            LogStatus.SUCCESS
                        )
                    }
                }
            }
        }

        toggleModule("ping", enabled) {
            pingMonitor.ping.collect { p ->
                _metricsState.value = _metricsState.value.copy(
                    pingMs = p.pingMs,
                    pingReadStatus = p.readStatus
                )
            }
        }

        toggleModule("top_process", enabled) {
            topProcessMonitor.topProcess.collect { top ->
                _metricsState.value = _metricsState.value.copy(
                    topProcessName = if (top.readStatus == MetricReadStatus.Ok) top.name else null,
                    topProcessCpuPercent = top.cpuPercent,
                    topProcesses = if (top.readStatus == MetricReadStatus.Ok) top.topProcesses else emptyList(),
                    topProcessReadStatus = top.readStatus
                )
            }
        }

        // Maintain clock, session timer, and snapshot timeline only while at least one consumer is active.
        if (enabled.isNotEmpty()) {
            if (snapshotTickJob?.isActive != true) {
                snapshotTickJob = engineScope.launch {
                    while (true) {
                        val elapsed = (SystemClock.elapsedRealtime() - sessionStartTimeMs) / 1000L
                        val timeStr = getCurrentFormattedTime()
                        val current = _metricsState.value.copy(
                            currentTime = timeStr,
                            sessionElapsedSec = elapsed.coerceAtLeast(0L)
                        )
                        _metricsState.value = current

                        val snapList = synchronized(ringLock) {
                            snapshotRing.addLast(MetricsSnapshot(System.currentTimeMillis(), current))
                            if (snapshotRing.size > MAX_SNAPSHOTS) snapshotRing.removeFirst()
                            ArrayList(snapshotRing)
                        }
                        _snapshotHistory.value = snapList
                        delay(1000L)
                    }
                }
            }
        } else {
            snapshotTickJob?.cancel()
            snapshotTickJob = null
        }
    }

    private fun toggleModule(key: String, enabled: Set<String>, block: suspend () -> Unit) {
        if (key in enabled) {
            if (moduleJobs[key]?.isActive == true) return
            moduleJobs[key] = engineScope.launch { block() }
        } else {
            moduleJobs[key]?.cancel()
            moduleJobs.remove(key)
            _metricsState.value = when (key) {
                "fps" -> _metricsState.value.copy(
                    fps = 0,
                    jankyFrames = 0,
                    totalFrames = 0,
                    frameTimeMs = 0,
                    fpsReadStatus = MetricReadStatus.Loading
                )
                "ram" -> _metricsState.value.copy(ramUsedGb = 0f, ramTotalGb = 0f)
                "net" -> _metricsState.value.copy(networkRxKbps = 0f, networkTxKbps = 0f)
                "thermal" -> _metricsState.value.copy(
                    thermalCpuC = 0f,
                    thermalGpuC = 0f,
                    thermalNpuC = 0f,
                    thermalSkinC = 0f,
                    thermalStatus = 0,
                    thermalReadStatus = MetricReadStatus.Loading,
                    hasThermalCpu = false,
                    hasThermalGpu = false,
                    hasThermalNpu = false,
                    hasThermalSkin = false,
                    hasThermalBattery = false,
                    isThrottling = false
                )
                "ping" -> _metricsState.value.copy(pingMs = 0, pingReadStatus = MetricReadStatus.Loading)
                "top_process" -> _metricsState.value.copy(
                    topProcessName = null,
                    topProcessCpuPercent = 0f,
                    topProcesses = emptyList(),
                    topProcessReadStatus = MetricReadStatus.Loading
                )
                else -> _metricsState.value
            }
        }
    }

    companion object {
        private const val MAX_FPS_HISTORY_SIZE = 60
        private const val MAX_SNAPSHOTS = 3600
    }
}
