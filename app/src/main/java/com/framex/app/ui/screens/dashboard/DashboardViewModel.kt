package com.framex.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.metrics.MetricsEngine
import com.framex.app.overlay.OverlayService
import com.framex.app.overlay.OverlayServiceController
import com.framex.app.permissions.PermissionsManager
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val overlayServiceController: OverlayServiceController,
    private val permissionsManager: PermissionsManager,
    private val privilegeManager: PrivilegeManager,
    private val metricsEngine: MetricsEngine
) : ViewModel() {

    init {
        metricsEngine.setScreenOverrideModules(setOf("fps"), requesterKey = "dashboard_screen")
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "dashboard_screen")
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        OverlayService.isRunning,
        permissionsManager.isReady,
        privilegeManager.currentMode,
        metricsEngine.fpsHistory
    ) { isRunning, isReady, mode, history ->
        DashboardUiState(
            isOverlayRunning = isRunning,
            isReady = isReady,
            fpsHistory = history,
            fpsStats = DashboardUtils.computeFpsStats(history),
            canShowHardwareTuning = mode == PrivilegeMode.ROOT
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(
            isOverlayRunning = OverlayService.isRunning.value,
            isReady = permissionsManager.isReady.value,
            canShowHardwareTuning = privilegeManager.currentMode.value == PrivilegeMode.ROOT
        )
    )

    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            DashboardUiEvent.StartOverlay -> overlayServiceController.startOverlayService()
            DashboardUiEvent.StopOverlay -> overlayServiceController.stopOverlayService()
        }
    }
}
