package com.framex.app.ui.screens.dashboard

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.metrics.MetricsEngine
import com.framex.app.overlay.OverlayService
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val metricsEngine: MetricsEngine
) : ViewModel() {

    private val hasOverlayPermission = MutableStateFlow(checkOverlayPermission())

    val uiState: StateFlow<DashboardUiState> = combine(
        OverlayService.isRunning,
        hasOverlayPermission,
        shizukuManager.isShizukuAvailable,
        shizukuManager.hasPermission,
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
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(
            hasOverlayPermission = checkOverlayPermission(),
            isShizukuAvailable = shizukuManager.isShizukuAvailable.value,
            hasShizukuPermission = shizukuManager.hasPermission.value
        )
    )

    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            DashboardUiEvent.StartOverlay -> startOverlayService()
            DashboardUiEvent.StopOverlay -> stopOverlayService()
            DashboardUiEvent.RefreshPermissions -> refreshPermissions()
        }
    }

    fun refreshPermissions() {
        hasOverlayPermission.value = checkOverlayPermission()
        shizukuManager.refreshState()
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun startOverlayService() {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        context.startService(intent)
    }
}
