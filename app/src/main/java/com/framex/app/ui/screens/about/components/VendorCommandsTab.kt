package com.framex.app.ui.screens.about.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.framex.app.gaming.catalog.GamingCommandCatalog
import com.framex.app.gaming.catalog.Platform
import com.framex.app.gaming.catalog.Toggle
import com.framex.app.ui.screens.about.AboutUiEvent
import com.framex.app.ui.screens.about.AboutUiState
import com.framex.app.ui.screens.about.CommandSummary
import com.framex.app.ui.screens.about.PulseChild
import com.framex.app.ui.screens.about.ToggleDescriptor
import com.framex.app.ui.screens.about.VendorTabDescriptor
import com.framex.app.ui.screens.about.isOn
import com.framex.app.ui.screens.about.toEvent

private val ActiveGreen = Color(0xFF10B981)

/**
 * One vendor tab of the Execution Center. The switches, their order and their wording come from
 * the [tab] descriptor and the commands listed on each switch come from the catalog, so the Vivo
 * and iQOO tabs share this single implementation.
 */
@Composable
fun VendorCommandsTab(
    platform: Platform,
    tab: VendorTabDescriptor,
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tab.toggles.forEach { ToggleCard(platform, it, state, onEvent) }
        if (tab.dividerToggles.isNotEmpty() || tab.divider.isNotEmpty()) {
            ExperimentalSectionDivider(description = tab.divider)
        }
        tab.dividerToggles.forEach { ToggleCard(platform, it, state, onEvent) }
        PulseCard(platform, tab, state, onEvent)
    }
}

@Composable
private fun ToggleCard(
    platform: Platform,
    descriptor: ToggleDescriptor,
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit
) {
    if (state.maxRefreshRate < descriptor.minRefreshRateHz) return
    val checked = state.isOn(descriptor.toggle)
    CommandToggleCard(
        title = descriptor.title,
        commandSummary = CommandSummary.text(platform, descriptor.toggle, descriptor.detail),
        footnoteText = descriptor.footnote,
        statusText = if (checked) descriptor.onLabel else descriptor.offLabel,
        statusColor = if (checked) descriptor.accent ?: ActiveGreen else Color.Gray,
        isChecked = checked,
        onCheckedChange = { enabled -> descriptor.toggle.toEvent(enabled)?.let(onEvent) },
        icon = descriptor.icon
    )
}

@Composable
private fun PulseCard(
    platform: Platform,
    tab: VendorTabDescriptor,
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit
) {
    CommandToggleCard(
        title = "2-Minute Maintenance Pulse",
        commandSummary = tab.pulse.summary,
        statusText = if (state.vivoMaintenancePulse) "2-Min Pulse Active" else "Pulse Disabled (Single Shot)",
        statusColor = if (state.vivoMaintenancePulse) ActiveGreen else Color.Gray,
        isChecked = state.vivoMaintenancePulse,
        onCheckedChange = { onEvent(AboutUiEvent.SetVivoMaintenancePulse(it)) },
        icon = Icons.Default.Timer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tab.pulse.children.forEach { child -> PulseChildRow(platform, child, state, onEvent) }
        }
    }
}

@Composable
private fun PulseChildRow(
    platform: Platform,
    child: PulseChild,
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit
) {
    val key = GamingCommandCatalog.forPlatform(platform).firstOrNull { it.toggle == child.toggle }?.key ?: return
    ChildCommandToggleRow(
        title = key,
        commandSummary = child.detail,
        isChecked = state.isOn(child.toggle),
        enabled = state.vivoMaintenancePulse,
        onCheckedChange = { enabled -> child.toggle.toEvent(enabled)?.let(onEvent) }
    )
}
