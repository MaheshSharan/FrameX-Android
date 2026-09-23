package com.framex.app.ui.screens.permissions

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.framex.app.R

enum class PermissionId {
    OVERLAY,
    USAGE_STATS,
    BATTERY_OPT,
    NOTIFICATIONS,
    WRITE_SETTINGS
}

@Immutable
data class PermissionItem(
    val id: PermissionId,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val isGranted: Boolean,
    @StringRes val actionTextRes: Int = R.string.action_grant
)

@Immutable
data class PermissionsUiState(
    val isShizukuAvailable: Boolean = false,
    val hasShizukuPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val hasBatteryOptDisabled: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasWriteSettingsPermission: Boolean = false
) {
    val isShizukuReady: Boolean
        get() = isShizukuAvailable && hasShizukuPermission

    val totalPermissionsCount: Int = 5

    val grantedPermissionsCount: Int
        get() = (if (hasOverlayPermission) 1 else 0) +
                (if (hasUsageStatsPermission) 1 else 0) +
                (if (hasBatteryOptDisabled) 1 else 0) +
                (if (hasNotificationPermission) 1 else 0) +
                (if (hasWriteSettingsPermission) 1 else 0)

    val isAllReady: Boolean
        get() = isShizukuReady && hasOverlayPermission && hasNotificationPermission
}

sealed interface PermissionsUiEvent {
    object RequestShizukuPermission : PermissionsUiEvent
    object RefreshPermissions : PermissionsUiEvent
}
