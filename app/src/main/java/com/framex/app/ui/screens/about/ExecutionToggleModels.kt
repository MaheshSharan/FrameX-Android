package com.framex.app.ui.screens.about

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.framex.app.gaming.catalog.Toggle

/** Wording and icon for one Execution Center switch; the commands come from the catalog. */
data class ToggleDescriptor(
    val toggle: Toggle,
    val title: String,
    val onLabel: String,
    val offLabel: String,
    val icon: ImageVector,
    val detail: String? = null,
    val footnote: String? = null,
    val accent: Color? = null,
    /** Hidden below this panel refresh rate, in Hz. */
    val minRefreshRateHz: Int = 0
)

/** The maintenance-pulse switch and the per-command switches that depend on it. */
data class PulseDescriptor(
    val summary: String,
    val children: List<PulseChild>
)

data class PulseChild(
    val toggle: Toggle,
    val detail: String
)

/** Everything one vendor tab shows, in display order. */
data class VendorTabDescriptor(
    val toggles: List<ToggleDescriptor>,
    val divider: String,
    val dividerToggles: List<ToggleDescriptor>,
    val pulse: PulseDescriptor
)
