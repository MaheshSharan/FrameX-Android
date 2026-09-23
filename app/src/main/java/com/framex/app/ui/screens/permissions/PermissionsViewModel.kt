package com.framex.app.ui.screens.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.R
import com.framex.app.repository.SystemPermissionRepository
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val systemPermissionRepository: SystemPermissionRepository,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    private val _effectChannel = Channel<PermissionsUiEffect>(Channel.BUFFERED)
    val effect = _effectChannel.receiveAsFlow()

    private val systemPermissionsState = MutableStateFlow(systemPermissionRepository.getPermissionsSnapshot())

    val uiState: StateFlow<PermissionsUiState> = combine(
        shizukuManager.isShizukuAvailable,
        shizukuManager.hasPermission,
        systemPermissionsState
    ) { isAvailable, hasPermission, sysState ->
        PermissionsUiState(
            isShizukuAvailable = isAvailable,
            hasShizukuPermission = hasPermission,
            hasOverlayPermission = sysState.hasOverlayPermission,
            hasUsageStatsPermission = sysState.hasUsageStatsPermission,
            hasBatteryOptDisabled = sysState.hasBatteryOptDisabled,
            hasNotificationPermission = sysState.hasNotificationPermission,
            hasWriteSettingsPermission = sysState.hasWriteSettingsPermission
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PermissionsUiState()
    )

    fun onEvent(event: PermissionsUiEvent) {
        when (event) {
            PermissionsUiEvent.LaunchShizukuApp -> launchShizukuApp()
            PermissionsUiEvent.RequestShizukuPermission -> requestShizukuPermission()
            is PermissionsUiEvent.RequestPermission -> requestPermission(event.id)
            PermissionsUiEvent.RefreshPermissions -> refreshPermissions()
            is PermissionsUiEvent.UpdateNotificationPermission -> updateNotificationPermission(event.granted)
        }
    }

    private fun launchShizukuApp() {
        if (systemPermissionRepository.isShizukuAppInstalled()) {
            _effectChannel.trySend(PermissionsUiEffect.LaunchShizukuApp)
        } else {
            _effectChannel.trySend(PermissionsUiEffect.ShowToast(R.string.perm_shizuku_not_found))
        }
    }

    private fun requestShizukuPermission() {
        if (shizukuManager.isShizukuAvailable.value) {
            shizukuManager.requestPermission()
        } else {
            _effectChannel.trySend(PermissionsUiEffect.ShowToast(R.string.perm_start_shizuku_first))
        }
    }

    private fun requestPermission(id: PermissionId) {
        val effect = when (id) {
            PermissionId.OVERLAY -> PermissionsUiEffect.OpenOverlaySettings
            PermissionId.USAGE_STATS -> PermissionsUiEffect.OpenUsageSettings
            PermissionId.BATTERY_OPT -> PermissionsUiEffect.OpenBatterySettings
            PermissionId.NOTIFICATIONS -> PermissionsUiEffect.RequestNotificationPermission
            PermissionId.WRITE_SETTINGS -> PermissionsUiEffect.OpenWriteSettings
        }
        _effectChannel.trySend(effect)
    }

    fun refreshPermissions() {
        shizukuManager.refreshState()
        systemPermissionsState.value = systemPermissionRepository.getPermissionsSnapshot()
    }

    fun updateNotificationPermission(granted: Boolean) {
        systemPermissionsState.value = systemPermissionsState.value.copy(
            hasNotificationPermission = granted
        )
    }
}
