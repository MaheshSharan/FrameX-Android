package com.framex.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.metrics.MetricsEngine
import com.framex.app.overlay.OverlayService
import com.framex.app.overlay.OverlayServiceController
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val overlayServiceController: OverlayServiceController,
    private val privilegeManager: PrivilegeManager,
    private val metricsEngine: MetricsEngine
) : ViewModel() {

    private val hasOverlayPermission = MutableStateFlow(overlayServiceController.hasOverlayPermission())
    init {
        metricsEngine.setScreenOverrideModules(setOf("fps"), requesterKey = "dashboard_screen")
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "dashboard_screen")
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        OverlayService.isRunning,
        hasOverlayPermission,
        privilegeManager.isAvailable,
        privilegeManager.isReady,
        metricsEngine.fpsHistory
    ) { isRunning, hasOverlay, isShizukuAvail, hasShizukuPerm, history ->
        val stats = DashboardUtils.computeFpsStats(history)
        DashboardUiState(
            isOverlayRunning = isRunning,
            hasOverlayPermission = hasOverlay,
            isShizukuAvailable = isShizukuAvail,
            hasShizukuPermission = hasShizukuPerm,
            fpsHistory = history,
            fpsStats = stats
        )
    }.combine(privilegeManager.currentMode) { state, mode ->
        state.copy(canShowHardwareTuning = mode == PrivilegeMode.ROOT)
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(
            isOverlayRunning = OverlayService.isRunning.value,
            hasOverlayPermission = overlayServiceController.hasOverlayPermission(),
            isShizukuAvailable = privilegeManager.isAvailable.value,
            hasShizukuPermission = privilegeManager.isReady.value,
            canShowHardwareTuning = privilegeManager.currentMode.value == PrivilegeMode.ROOT
        )
    )

    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            DashboardUiEvent.StartOverlay -> overlayServiceController.startOverlayService()
            DashboardUiEvent.StopOverlay -> overlayServiceController.stopOverlayService()
            DashboardUiEvent.RefreshPermissions -> refreshPermissions()
        }
    }

    fun refreshPermissions() {
        hasOverlayPermission.value = overlayServiceController.hasOverlayPermission()
        privilegeManager.refresh()
    }
}
