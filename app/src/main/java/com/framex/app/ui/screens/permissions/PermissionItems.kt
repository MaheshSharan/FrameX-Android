package com.framex.app.ui.screens.permissions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import com.framex.app.R
import com.framex.app.permissions.AppPermission

/** Display model for one permission row; shared by System Status and onboarding. */
fun AppPermission.toItem(isGranted: Boolean): PermissionItem = when (this) {
    AppPermission.OVERLAY -> PermissionItem(
        this, R.string.perm_overlay_title, R.string.perm_overlay_desc, Icons.Default.Layers, isGranted
    )
    AppPermission.NOTIFICATIONS -> PermissionItem(
        this, R.string.perm_notifications_title, R.string.perm_notifications_desc,
        Icons.Default.Notifications, isGranted
    )
    AppPermission.USAGE_STATS -> PermissionItem(
        this, R.string.perm_usage_title, R.string.perm_usage_desc, Icons.Default.BarChart, isGranted
    )
    AppPermission.BATTERY_OPT -> PermissionItem(
        this, R.string.perm_battery_title, R.string.perm_battery_desc, Icons.Default.BatteryFull, isGranted,
        actionTextRes = R.string.perm_battery_disable
    )
    AppPermission.WRITE_SETTINGS -> PermissionItem(
        this, R.string.perm_write_settings_title, R.string.perm_write_settings_desc,
        Icons.Default.Settings, isGranted
    )
    AppPermission.DND -> PermissionItem(
        this, R.string.perm_dnd_title, R.string.perm_dnd_desc, Icons.Default.DoNotDisturbOn, isGranted
    )
    AppPermission.NOTIF_LISTENER -> PermissionItem(
        this, R.string.perm_listener_title, R.string.perm_listener_desc,
        Icons.Default.NotificationsActive, isGranted,
        actionTextRes = R.string.perm_listener_enable
    )
}
