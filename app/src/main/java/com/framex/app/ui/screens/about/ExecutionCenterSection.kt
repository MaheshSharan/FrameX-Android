package com.framex.app.ui.screens.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.alpha
import com.framex.app.ui.components.WovenNetBackground
import com.framex.app.ui.screens.about.components.CommandToggleCard
import com.framex.app.ui.screens.about.components.ExperimentalSectionDivider

@Composable
fun ExecutionCenterSection(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    val availableTabs = listOf(ExecutionCenterTab.COMMON, ExecutionCenterTab.GENERIC)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "EXECUTION CENTER",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1015)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                WovenNetBackground(modifier = Modifier.matchParentSize())

                Column(modifier = Modifier.padding(20.dp)) {
                    ExecutionCenterHeader(
                        isExpanded = isExpanded,
                        chevronRotation = chevronRotation,
                        onToggleExpand = { isExpanded = !isExpanded }
                    )

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            ExecutionTabBar(
                                tabs = availableTabs,
                                selectedTab = state.selectedExecutionTab,
                                onSelectTab = { onEvent(AboutUiEvent.SelectExecutionCenterTab(it)) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            when (state.selectedExecutionTab) {
                                ExecutionCenterTab.COMMON -> CommonCommandsTab(state, onEvent)
                                ExecutionCenterTab.GENERIC -> GenericCommandsTab(state, onEvent)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            RescanDeviceAction(
                                isRescanning = state.isRescanningDevice,
                                onRescan = { onEvent(AboutUiEvent.RescanDevice) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionCenterHeader(
    isExpanded: Boolean,
    chevronRotation: Float,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggleExpand() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Command Execution Center",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isExpanded) "Tap to collapse options" else "Manage individual optimization commands",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.5.sp
                )
            }
        }

        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .size(24.dp)
                .rotate(chevronRotation)
        )
    }
}

@Composable
private fun ExecutionTabBar(
    tabs: List<ExecutionCenterTab>,
    selectedTab: ExecutionCenterTab,
    onSelectTab: (ExecutionCenterTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F0F16))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab || (!tabs.contains(selectedTab) && tab == ExecutionCenterTab.COMMON)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                        else Color.Transparent
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun CommonCommandsTab(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ExecutionCenterCapabilities.THERMAL_OVERRIDE in state.capableCommandIds) {
            CommandToggleCard(
                title = "Bypass Thermal Throttling",
                commandSummary = "• 'cmd thermalservice override-status 0'",
                statusText = if (state.disableThermalThrottling) "Override Active (Aggressive)" else "Safe Mode (Protected)",
                statusColor = if (state.disableThermalThrottling) Color(0xFFEF4444) else Color(0xFF10B981),
                isChecked = state.disableThermalThrottling,
                onCheckedChange = { onEvent(AboutUiEvent.SetDisableThermalThrottling(it)) },
                icon = Icons.Default.Thermostat,
                warningText = "Bypassing thermal safety prevents CPU/GPU downclocking during heat buildup, which can increase device temperature and long-term hardware wear."
            )
        }

        if (ExecutionCenterCapabilities.RAM_TRIM in state.capableCommandIds) {
            CommandToggleCard(
                title = "RAM Cache Pre-Trimming",
                commandSummary = "• 'pm trim-caches', 'am compact background', 'cmd pinner repin'\n• Halts background cache bloat before match render loop",
                statusText = if (state.ramCachePreTrimEnabled) "Pre-Trim Active" else "Trim Skipped",
                statusColor = if (state.ramCachePreTrimEnabled) Color(0xFF10B981) else Color.Gray,
                isChecked = state.ramCachePreTrimEnabled,
                onCheckedChange = { onEvent(AboutUiEvent.SetRamCachePreTrim(it)) },
                icon = Icons.Default.Memory
            )
        }

        ExperimentalSectionDivider(
            description = "Low-level system and cgroup overrides. Safe for standard operation."
        )

        if (ExecutionCenterCapabilities.PHANTOM_PROC_KILLER in state.capableCommandIds) {
            CommandToggleCard(
                title = "Disable Phantom Process Killer",
                commandSummary = "• 'device_config put activity_manager max_phantom_processes 2147483647'\n• Disables 32 child process limit for game engines",
                statusText = if (state.disablePhantomProcKiller) "PPK Disabled (Max Limit)" else "AOSP Default (32 Procs)",
                statusColor = if (state.disablePhantomProcKiller) Color(0xFF10B981) else Color.Gray,
                isChecked = state.disablePhantomProcKiller,
                onCheckedChange = { onEvent(AboutUiEvent.SetDisablePhantomProcKiller(it)) },
                icon = Icons.Default.Security
            )
        }

        if (ExecutionCenterCapabilities.CPU_PRIORITY_LOCK in state.capableCommandIds) {
            CommandToggleCard(
                title = "Cgroup CPU Priority Lock",
                commandSummary = "• 'cmd activity set-bg-restriction-level <pkg> unrestricted'\n• Locks game process to top-app cpusets & prevents down-migration",
                statusText = if (state.cpuPriorityLock) "Top-App Cpuset Locked" else "Standard Priority",
                statusColor = if (state.cpuPriorityLock) Color(0xFF10B981) else Color.Gray,
                isChecked = state.cpuPriorityLock,
                onCheckedChange = { onEvent(AboutUiEvent.SetCpuPriorityLock(it)) },
                icon = Icons.Default.Speed
            )
        }
    }
}

@Composable
private fun GenericCommandsTab(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ExecutionCenterCapabilities.FIXED_PERFORMANCE_MODE in state.capableCommandIds) {
            CommandToggleCard(
                title = "Fixed Performance Governor",
                commandSummary = "• 'cmd power set-fixed-performance-mode-enabled true'\n• Disables dynamic frequency scaling on supported devices",
                statusText = if (state.fixedPerformanceMode) "Fixed Mode Active" else "Default Dynamic Scaling",
                statusColor = if (state.fixedPerformanceMode) Color(0xFFF59E0B) else Color.Gray,
                isChecked = state.fixedPerformanceMode,
                onCheckedChange = { onEvent(AboutUiEvent.SetFixedPerformanceMode(it)) },
                icon = Icons.Default.Bolt
            )
        }

        if (ExecutionCenterCapabilities.NETWORK_FIREWALL in state.capableCommandIds) {
            CommandToggleCard(
                title = "Network & Doze Firewall",
                commandSummary = "• 'cmd netpolicy add restrict-background-whitelist', 'cmd deviceidle whitelist +<pkg>'\n• Grants network and sleep immunity to active match package",
                statusText = if (state.networkFirewall) "Doze Whitelist Active" else "Default Network Rules",
                statusColor = if (state.networkFirewall) Color(0xFF10B981) else Color.Gray,
                isChecked = state.networkFirewall,
                onCheckedChange = { onEvent(AboutUiEvent.SetNetworkFirewall(it)) },
                icon = Icons.Default.Wifi
            )
        }

        if (ExecutionCenterCapabilities.REFRESH_RATE_LOCK in state.capableCommandIds) {
            CommandToggleCard(
                title = "Peak Refresh Rate Lock",
                commandSummary = "• 'settings put system peak_refresh_rate / min_refresh_rate'\n• Locks framework refresh rate floor and ceiling to highest display mode",
                statusText = if (state.refreshRateLock) "Display Mode Pinned" else "Adaptive Refresh Rate",
                statusColor = if (state.refreshRateLock) Color(0xFF10B981) else Color.Gray,
                isChecked = state.refreshRateLock,
                onCheckedChange = { onEvent(AboutUiEvent.SetRefreshRateLock(it)) },
                icon = Icons.Default.Refresh
            )
        }

        if (ExecutionCenterCapabilities.TOUCH_BOOST in state.capableCommandIds) {
            CommandToggleCard(
                title = "Touch Response Boost",
                commandSummary = "• 'settings put system touch_response_speed 2'\n• Minimizes touch latency and optimizes digitizer dispatch",
                statusText = if (state.touchBoost) "Low Latency Touch" else "Default Touch Filtering",
                statusColor = if (state.touchBoost) Color(0xFF10B981) else Color.Gray,
                isChecked = state.touchBoost,
                onCheckedChange = { onEvent(AboutUiEvent.SetTouchBoost(it)) },
                icon = Icons.Default.TouchApp
            )
        }
    }
}

/** The catalog ids that gate each Execution Center card's visibility. */
private object ExecutionCenterCapabilities {
    const val THERMAL_OVERRIDE = "service:thermal_override"
    const val RAM_TRIM = "service:ram_trim_caches"
    const val PHANTOM_PROC_KILLER = "service:phantom_proc_max_override"
    const val CPU_PRIORITY_LOCK = "service:cpu_priority_bg_restriction"
    const val FIXED_PERFORMANCE_MODE = "service:fixed_performance_mode"
    const val NETWORK_FIREWALL = "service:network_restrict_bg_whitelist"
    const val REFRESH_RATE_LOCK = "settings:system:peak_refresh_rate"
    const val TOUCH_BOOST = "settings:system:touch_response_speed"
}

@Composable
private fun RescanDeviceAction(isRescanning: Boolean, onRescan: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !isRescanning) { onRescan() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isRescanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Autorenew,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Rescan Device",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.alpha(if (isRescanning) 0.6f else 1f)
        )
    }
}
