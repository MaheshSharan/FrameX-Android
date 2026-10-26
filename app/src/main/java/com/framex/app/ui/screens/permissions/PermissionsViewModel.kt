package com.framex.app.ui.screens.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.R
import com.framex.app.repository.SystemPermissionRepository
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.privilege.SwitchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val systemPermissionRepository: SystemPermissionRepository,
    private val privilegeManager: PrivilegeManager
) : ViewModel() {

    private val _effectChannel = Channel<PermissionsUiEffect>(Channel.BUFFERED)
    val effect = _effectChannel.receiveAsFlow()

    private val systemPermissionsState = MutableStateFlow(systemPermissionRepository.getPermissionsSnapshot())

    val uiState: StateFlow<PermissionsUiState> = combine(
        privilegeManager.isAvailable,
        privilegeManager.isReady,
        privilegeManager.currentMode,
        systemPermissionsState
    ) { isAvailable, hasPermission, mode, sysState ->
        PermissionsUiState(
            selectedMode = mode,
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
            is PermissionsUiEvent.SelectMode -> selectMode(event.mode)
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

    private fun selectMode(mode: PrivilegeMode) {
        viewModelScope.launch {
            when (privilegeManager.switchMode(mode)) {
                SwitchResult.SWITCHED -> if (mode == PrivilegeMode.ROOT) privilegeManager.requestAccess()
                SwitchResult.BLOCKED_GAMING_ACTIVE ->
                    _effectChannel.trySend(PermissionsUiEffect.ShowToast(R.string.perm_mode_blocked_gaming))
                SwitchResult.BLOCKED_RECOVERY_PENDING ->
                    _effectChannel.trySend(PermissionsUiEffect.ShowToast(R.string.perm_mode_blocked_recovery))
            }
        }
    }

    private fun requestShizukuPermission() {
        if (privilegeManager.currentMode.value == PrivilegeMode.ROOT) {
            viewModelScope.launch { privilegeManager.requestAccess() }
            return
        }
        if (privilegeManager.isAvailable.value) {
            viewModelScope.launch { privilegeManager.requestAccess() }
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
        privilegeManager.refresh()
        systemPermissionsState.value = systemPermissionRepository.getPermissionsSnapshot()
    }

    fun updateNotificationPermission(granted: Boolean) {
        systemPermissionsState.value = systemPermissionsState.value.copy(
            hasNotificationPermission = granted
        )
    }
}
