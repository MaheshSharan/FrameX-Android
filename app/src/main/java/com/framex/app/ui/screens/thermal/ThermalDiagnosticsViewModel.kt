package com.framex.app.ui.screens.thermal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.metrics.MetricsEngine
import com.framex.app.metrics.MetricsState
import com.framex.app.metrics.SessionLogger
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ThermalDiagnosticsViewModel @Inject constructor(
    private val metricsEngine: MetricsEngine,
    private val sessionLogger: SessionLogger,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val metricsState = metricsEngine.metricsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetricsState())

    val snapshotHistory = metricsEngine.snapshotHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRecording = sessionLogger.isRecording
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isShizukuAvailable = shizukuManager.isShizukuAvailable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasShizukuPermission = shizukuManager.hasPermission
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Persisted graph controls (issue #55): survive navigating away from and back to
    // this screen, unlike the previous remember{}-scoped selection.
    val thermalTimeWindow = settingsRepository.thermalTimeWindow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.thermalTimeWindow.value)

    val thermalGraphMode = settingsRepository.thermalGraphMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.thermalGraphMode.value)

    fun setThermalTimeWindow(window: TimeWindow) {
        settingsRepository.setThermalTimeWindow(window.name)
    }

    fun setThermalGraphMode(mode: GraphMetricMode) {
        settingsRepository.setThermalGraphMode(mode.name)
    }

    init {
        metricsEngine.setScreenOverrideModules(
            setOf("thermal", "temp", "top_process"),
            requesterKey = "thermal_diagnostics_screen"
        )
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "thermal_diagnostics_screen")
    }

    fun toggleRecording() {
        if (sessionLogger.isRecording.value) {
            sessionLogger.stopRecording()
        } else {
            sessionLogger.startRecording()
        }
    }

    fun recordedSampleCount(snapshots: List<MetricsEngine.MetricsSnapshot>): Int {
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
