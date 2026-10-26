package com.framex.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.metrics.MetricsEngine
import com.framex.app.overlay.OverlayService
import com.framex.app.overlay.OverlayServiceController
import com.framex.app.repository.SettingsRepository
import com.framex.app.privilege.PrivilegeManager
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
    private val metricsEngine: MetricsEngine,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val hasOverlayPermission = MutableStateFlow(overlayServiceController.hasOverlayPermission())
    private val _whatsNewInfo = MutableStateFlow<WhatsNewInfo?>(null)

    init {
        checkWhatsNew()
        metricsEngine.setScreenOverrideModules(setOf("fps"), requesterKey = "dashboard_screen")
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "dashboard_screen")
    }

    private fun checkWhatsNew() {
        if (WhatsNewRegistry.DEBUG_FORCE_SHOW_WHATS_NEW) {
            val currentCode = com.framex.app.BuildConfig.VERSION_CODE.toLong()
            _whatsNewInfo.value = WhatsNewRegistry.getWhatsNewForVersion(currentCode)
            return
        }

        val lastSeenCode = settingsRepository.getLastSeenVersionCode()
        val currentCode = com.framex.app.BuildConfig.VERSION_CODE.toLong()
        val isOnboardingDone = settingsRepository.isOnboardingCompleted.value

        if (lastSeenCode == 0L) {
            if (isOnboardingDone) {
                // Existing user updating to this build for the first time: show What's New once
                _whatsNewInfo.value = WhatsNewRegistry.getWhatsNewForVersion(currentCode)
            } else {
                // Fresh install: record baseline so new users see onboarding instead
                settingsRepository.updateLastSeenVersionCode(currentCode)
            }
        } else if (currentCode > lastSeenCode) {
            _whatsNewInfo.value = WhatsNewRegistry.getWhatsNewForVersion(currentCode)
        }
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
    }.combine(_whatsNewInfo) { state, whatsNew ->
        state.copy(whatsNewInfo = whatsNew)
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
            whatsNewInfo = _whatsNewInfo.value
        )
    )

    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            DashboardUiEvent.StartOverlay -> overlayServiceController.startOverlayService()
            DashboardUiEvent.StopOverlay -> overlayServiceController.stopOverlayService()
            DashboardUiEvent.RefreshPermissions -> refreshPermissions()
            DashboardUiEvent.DismissWhatsNew -> dismissWhatsNew()
        }
    }

    private fun dismissWhatsNew() {
        _whatsNewInfo.value = null
        val currentCode = com.framex.app.BuildConfig.VERSION_CODE.toLong()
        settingsRepository.updateLastSeenVersionCode(currentCode)
    }

    fun refreshPermissions() {
        hasOverlayPermission.value = overlayServiceController.hasOverlayPermission()
        privilegeManager.refresh()
    }
}
