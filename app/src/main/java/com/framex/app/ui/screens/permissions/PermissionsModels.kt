package com.framex.app.ui.screens.permissions

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.framex.app.R
import com.framex.app.permissions.AppPermission
import com.framex.app.privilege.PrivilegeMode

@Immutable
data class PermissionItem(
    val permission: AppPermission,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val isGranted: Boolean,
    @StringRes val actionTextRes: Int = R.string.action_grant
)

@Immutable
data class PermissionsUiState(
    val selectedMode: PrivilegeMode = PrivilegeMode.SHIZUKU,
    val isPrivilegeAvailable: Boolean = false,
    val isPrivilegeReady: Boolean = false,
    val items: List<PermissionItem> = emptyList(),
    /** The shared readiness rule: privilege access plus every essential permission. */
    val isReady: Boolean = false
) {
    val grantedCount: Int get() = items.count { it.isGranted }
}

sealed interface PermissionsUiEvent {
    data class SelectMode(val mode: PrivilegeMode) : PermissionsUiEvent
    data object RequestPrivilegeAccess : PermissionsUiEvent
    data object RefreshPermissions : PermissionsUiEvent
}
