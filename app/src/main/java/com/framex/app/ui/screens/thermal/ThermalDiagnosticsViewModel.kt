package com.framex.app.ui.screens.thermal

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.RecordableMetric
import com.framex.app.metrics.SessionLogger
import com.framex.app.metrics.SessionRecordingConfig
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ThermalDiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val metricsEngine: MetricsEngine,
    private val sessionLogger: SessionLogger,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val deviceDiagnosticManager: DeviceDiagnosticManager
) : ViewModel() {

    val metricsState = metricsEngine.metricsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetricsState())

    val snapshotHistory = metricsEngine.snapshotHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordedSnapshots = sessionLogger.recordedSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRecording = sessionLogger.isRecording
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val recordingConfig = sessionLogger.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionRecordingConfig())

    val lastMetadata = sessionLogger.lastMetadata
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isShizukuAvailable = shizukuManager.isShizukuAvailable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasShizukuPermission = shizukuManager.hasPermission
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _installedApps = MutableStateFlow<List<TargetAppOption>>(
        listOf(TargetAppOption(packageName = null, label = "System-Wide", versionName = ""))
    )
    val installedApps: StateFlow<List<TargetAppOption>> = _installedApps.asStateFlow()

    val maxRefreshRateHz: Int = deviceDiagnosticManager.getMaxHardwareRefreshRate().toInt().coerceAtLeast(60)

    val platformInfo: DevicePlatformInfo = run {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
                ?: deviceDiagnosticManager.getProperty("ro.board.platform")
                ?: Build.HARDWARE
        } else {
            deviceDiagnosticManager.getProperty("ro.board.platform") ?: Build.HARDWARE
        }
        DevicePlatformInfo(
            socName = soc.uppercase(),
            modelName = Build.MODEL,
            osVersion = "Android ${Build.VERSION.RELEASE}",
            displayRefreshHz = maxRefreshRateHz
        )
    }

    init {
        metricsEngine.setScreenOverrideModules(
            setOf("fps", "thermal", "temp", "cpu", "cpu_cluster", "power", "battery_level", "top_process"),
            requesterKey = "thermal_diagnostics_screen"
        )
        observeLauncherGames()
    }

    private fun observeLauncherGames() {
        settingsRepository.launcherGames
            .onEach { gamePackages ->
                val options = withContext(Dispatchers.IO) {
                    val pm = appContext.packageManager
                    val gameOptions = gamePackages.mapNotNull { pkg ->
                        runCatching {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            val ver = pm.getPackageInfo(pkg, 0).versionName.orEmpty()
                            TargetAppOption(
                                packageName = pkg,
                                label = label,
                                versionName = ver
                            )
                        }.getOrNull()
                    }.sortedBy { it.label.lowercase() }
                    listOf(TargetAppOption(null, "System-Wide", "")) + gameOptions
                }
                _installedApps.value = options
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "thermal_diagnostics_screen")
    }

    fun selectTargetApp(option: TargetAppOption) {
        val current = sessionLogger.config.value
        sessionLogger.updateConfig(
            current.copy(
                targetPackageName = option.packageName,
                targetAppLabel = option.label,
                targetAppVersion = option.versionName
            )
        )
    }

    fun toggleRecordedMetric(metric: RecordableMetric) {
        val current = sessionLogger.config.value
        val nextSet = if (metric in current.selectedMetrics) {
            if (current.selectedMetrics.size > 1) current.selectedMetrics - metric else current.selectedMetrics
        } else {
            current.selectedMetrics + metric
        }
        sessionLogger.updateConfig(current.copy(selectedMetrics = nextSet))
    }

    fun toggleRecording() {
        if (sessionLogger.isRecording.value) {
            sessionLogger.stopRecording()
        } else {
            sessionLogger.startRecording()
        }
    }

    fun recordedSampleCount(snapshots: List<MetricsEngine.MetricsSnapshot>): Int {
        val recorded = sessionLogger.recordedSnapshots.value
        if (recorded.isNotEmpty()) return recorded.size
        val startMs = sessionLogger.recordingStartTimestampMs
        if (startMs == 0L) return 0
        return snapshots.count { it.timestampMs >= startMs }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun refreshShizukuState() {
        shizukuManager.refreshState()
    }

    fun exportSession(onReady: (File) -> Unit, onEmpty: () -> Unit) {
        viewModelScope.launch {
            val file = sessionLogger.exportToFile()
            if (file == null) {
                onEmpty()
            } else {
                onReady(file)
            }
        }
    }
}
