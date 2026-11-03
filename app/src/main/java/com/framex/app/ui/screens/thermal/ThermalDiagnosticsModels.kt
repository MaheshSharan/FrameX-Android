package com.framex.app.ui.screens.thermal

import androidx.compose.runtime.Immutable

/**
 * Target app option for per-app session profiling.
 * Populated strictly from System-Wide + games added in Performance -> Game Launcher.
 */
@Immutable
data class TargetAppOption(
    val packageName: String?,
    val label: String,
    val versionName: String = ""
)

/**
 * 4-column hardware identity strip: Chipset (SoC) | Device (Model) | System (OS) | Display (Max Hz).
 */
@Immutable
data class DevicePlatformInfo(
    val socName: String = "SoC",
    val modelName: String = "Android",
    val osVersion: String = "Android",
    val displayRefreshHz: Int = 60
)
