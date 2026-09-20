package com.framex.app.ui.screens.performance

import androidx.compose.runtime.Immutable

/**
 * Immutable UI model representing an individual system optimization or safeguard item.
 */
@Immutable
data class ActiveOptimizationItem(
    val title: String,
    val detail: String,
    val isProtectedOrBypassed: Boolean = false
)

/**
 * Immutable UI model representing the live state of an active gaming session,
 * derived dynamically from actual system operations executed on the device.
 */
@Immutable
data class ActiveGamingSession(
    val title: String,
    val isVivoDevice: Boolean,
    val activeGamePackage: String?,
    val suspendedAppsCount: Int,
    val items: List<ActiveOptimizationItem>
)
