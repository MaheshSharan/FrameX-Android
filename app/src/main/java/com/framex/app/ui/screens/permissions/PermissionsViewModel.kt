package com.framex.app.ui.screens.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.R
import com.framex.app.permissions.AppPermission
import com.framex.app.permissions.PermissionsManager
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.ui.screens.permissions.components.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionsManager: PermissionsManager,
    private val privilegeManager: PrivilegeManager
) : ViewModel() {

    private val _toasts = Channel<Int>(Channel.BUFFERED)

    /** String resources to show as one-shot toasts. */
    val toasts: Flow<Int> = _toasts.receiveAsFlow()

    val uiState: StateFlow<PermissionsUiState> = combine(
        privilegeManager.isAvailable,
        privilegeManager.isReady,
        privilegeManager.currentMode,
        permissionsManager.status,
        permissionsManager.isReady,
        ::buildState
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = buildState(
            privilegeManager.isAvailable.value,
            privilegeManager.isReady.value,
            privilegeManager.currentMode.value,
            permissionsManager.status.value,
            permissionsManager.isReady.value
        )
    )

    fun onEvent(event: PermissionsUiEvent) {
        when (event) {
            is PermissionsUiEvent.SelectMode -> selectMode(event.mode)
            PermissionsUiEvent.RequestPrivilegeAccess -> requestPrivilegeAccess()
            PermissionsUiEvent.RefreshPermissions -> permissionsManager.refresh()
        }
    }

    private fun selectMode(mode: PrivilegeMode) {
        viewModelScope.launch {
            privilegeManager.selectMode(mode).messageRes()?.let(_toasts::trySend)
        }
    }

    private fun requestPrivilegeAccess() {
        val shizukuNotRunning = privilegeManager.currentMode.value == PrivilegeMode.SHIZUKU &&
            !privilegeManager.isAvailable.value
        if (shizukuNotRunning) {
            _toasts.trySend(R.string.perm_start_shizuku_first)
        } else {
            viewModelScope.launch { privilegeManager.requestAccess() }
        }
    }

    private fun buildState(
        isAvailable: Boolean,
        isPrivilegeReady: Boolean,
        mode: PrivilegeMode,
        status: Map<AppPermission, Boolean>,
        isReady: Boolean
    ) = PermissionsUiState(
        selectedMode = mode,
        isPrivilegeAvailable = isAvailable,
        isPrivilegeReady = isPrivilegeReady,
        items = AppPermission.entries.map { it.toItem(status[it] == true) },
        isReady = isReady
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
