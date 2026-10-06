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
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
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
import com.framex.app.ui.components.WovenNetBackground
import com.framex.app.ui.screens.about.components.ChildCommandToggleRow
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

    val availableTabs = remember(state.isVivoDevice, state.isIqooDevice, state.isVivoOptActive) {
        val tabs = mutableListOf(ExecutionCenterTab.COMMON, ExecutionCenterTab.GENERIC)
        if (state.isVivoOptActive && state.isIqooDevice) {
            tabs.add(ExecutionCenterTab.IQOO)
        } else if (state.isVivoOptActive && state.isVivoDevice) {
            tabs.add(ExecutionCenterTab.VIVO)
        }
        tabs
    }

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
                                ExecutionCenterTab.VIVO -> VivoCommandsTab(state, onEvent)
                                ExecutionCenterTab.IQOO -> IqooCommandsTab(state, onEvent)
                            }
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
        CommandToggleCard(
            title = "Bypass Thermal Throttling",
            commandSummary = "• Universal: 'cmd thermalservice override-status 0'\n• Vivo/iQOO: Disables 'game_cube_temper_control'",
            statusText = if (state.disableThermalThrottling) "Override Active (Aggressive)" else "Safe Mode (Protected)",
            statusColor = if (state.disableThermalThrottling) Color(0xFFEF4444) else Color(0xFF10B981),
            isChecked = state.disableThermalThrottling,
            onCheckedChange = { onEvent(AboutUiEvent.SetDisableThermalThrottling(it)) },
            icon = Icons.Default.Thermostat,
            warningText = "Bypassing thermal safety prevents CPU/GPU downclocking during heat buildup, which can increase device temperature and long-term hardware wear."
        )

        CommandToggleCard(
            title = "RAM Cache Pre-Trimming",
            commandSummary = "• Universal: 'am trim-memory <pkg> RUNNING_CRITICAL'\n• Halts background cache bloat before match render loop",
            statusText = if (state.ramCachePreTrimEnabled) "Pre-Trim Active" else "Trim Skipped",
            statusColor = if (state.ramCachePreTrimEnabled) Color(0xFF10B981) else Color.Gray,
            isChecked = state.ramCachePreTrimEnabled,
            onCheckedChange = { onEvent(AboutUiEvent.SetRamCachePreTrim(it)) },
            icon = Icons.Default.Memory
        )

        ExperimentalSectionDivider(
            description = "Low-level system and cgroup overrides. Safe for standard operation."
        )

        CommandToggleCard(
            title = "Disable Phantom Process Killer",
            commandSummary = "• Universal: 'device_config put activity_manager max_phantom_processes 2147483647'\n• Disables 32 child process limit for game engines",
            statusText = if (state.disablePhantomProcKiller) "PPK Disabled (Max Limit)" else "AOSP Default (32 Procs)",
            statusColor = if (state.disablePhantomProcKiller) Color(0xFF10B981) else Color.Gray,
            isChecked = state.disablePhantomProcKiller,
            onCheckedChange = { onEvent(AboutUiEvent.SetDisablePhantomProcKiller(it)) },
            icon = Icons.Default.Security
        )

        CommandToggleCard(
            title = "Cgroup CPU Priority Lock",
            commandSummary = "• Universal: 'cmd activity set-bg-restriction-level <pkg> unrestricted'\n• Locks game process to top-app cpusets & prevents down-migration",
            statusText = if (state.cpuPriorityLock) "Top-App Cpuset Locked" else "Standard Priority",
            statusColor = if (state.cpuPriorityLock) Color(0xFF10B981) else Color.Gray,
            isChecked = state.cpuPriorityLock,
            onCheckedChange = { onEvent(AboutUiEvent.SetCpuPriorityLock(it)) },
            icon = Icons.Default.Speed
        )
    }
}

@Composable
private fun GenericCommandsTab(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CommandToggleCard(
            title = "Fixed Performance Governor",
            commandSummary = "• Universal: 'cmd performance_hint set-fixed-performance-mode-enabled true'\n• Disables dynamic frequency scaling on supported devices",
            statusText = if (state.fixedPerformanceMode) "Fixed Mode Active" else "Default Dynamic Scaling",
            statusColor = if (state.fixedPerformanceMode) Color(0xFFF59E0B) else Color.Gray,
            isChecked = state.fixedPerformanceMode,
            onCheckedChange = { onEvent(AboutUiEvent.SetFixedPerformanceMode(it)) },
            icon = Icons.Default.Bolt
        )

        CommandToggleCard(
            title = "Network & Doze Firewall",
            commandSummary = "• Universal: 'dumpsys deviceidle whitelist +<pkg>'\n• Grants network and sleep immunity to active match package",
            statusText = if (state.networkFirewall) "Doze Whitelist Active" else "Default Network Rules",
            statusColor = if (state.networkFirewall) Color(0xFF10B981) else Color.Gray,
            isChecked = state.networkFirewall,
            onCheckedChange = { onEvent(AboutUiEvent.SetNetworkFirewall(it)) },
            icon = Icons.Default.Wifi
        )

        CommandToggleCard(
            title = "Peak Refresh Rate Lock",
            commandSummary = "• Universal: 'settings put system peak_refresh_rate / min_refresh_rate'\n• Locks framework refresh rate floor and ceiling to highest display mode",
            statusText = if (state.refreshRateLock) "Display Mode Pinned" else "Adaptive Refresh Rate",
            statusColor = if (state.refreshRateLock) Color(0xFF10B981) else Color.Gray,
            isChecked = state.refreshRateLock,
            onCheckedChange = { onEvent(AboutUiEvent.SetRefreshRateLock(it)) },
            icon = Icons.Default.Refresh
        )

        CommandToggleCard(
            title = "Touch Response Boost",
            commandSummary = "• Universal: 'settings put secure/system touch_response_boost / touch_smooth'\n• Minimizes touch latency and optimizes digitizer dispatch",
            statusText = if (state.touchBoost) "Low Latency Touch" else "Default Touch Filtering",
            statusColor = if (state.touchBoost) Color(0xFF10B981) else Color.Gray,
            isChecked = state.touchBoost,
            onCheckedChange = { onEvent(AboutUiEvent.SetTouchBoost(it)) },
            icon = Icons.Default.TouchApp
        )
    }
}

@Composable
private fun VivoCommandsTab(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CommandToggleCard(
            title = "Monster Mode & BBK HAL State",
            commandSummary = "• System Property Power Mode: 5 (True Monster)\n• Settings Global: bbb_perf_mode = 1 (vivo-vperf-hal)",
            footnoteText = "Activate if your device is Vivo. Most iQOO devices already provide native Monster Mode in system settings.",
            statusText = if (state.vivoMonsterMode) "Monster Mode 5 Active" else "Standard Power Mode",
            statusColor = if (state.vivoMonsterMode) Color(0xFFF59E0B) else Color.Gray,
            isChecked = state.vivoMonsterMode,
            onCheckedChange = { onEvent(AboutUiEvent.SetVivoMonsterMode(it)) },
            icon = Icons.Default.Bolt
        )

        CommandToggleCard(
            title = "GameCube VIP Thread Scheduler",
            commandSummary = "• Settings Global: game_cube_vip_thread = 1\n• RMServerExtension prioritizes RenderThread to Prime Core",
            statusText = if (state.vivoVipThread) "VIP Scheduling Active" else "Standard Scheduler",
            statusColor = if (state.vivoVipThread) Color(0xFF10B981) else Color.Gray,
            isChecked = state.vivoVipThread,
            onCheckedChange = { onEvent(AboutUiEvent.SetVivoVipThread(it)) },
            icon = Icons.Default.Speed
        )

        CommandToggleCard(
            title = "Live Target FPS Handshake",
            commandSummary = "• Settings System: sdk_game_target_fps & sdk_game_scene\n• Real-time match PID handshake with GameManagerService",
            statusText = if (state.vivoGameHandshake) "Target FPS Handshake Active" else "Handshake Inactive",
            statusColor = if (state.vivoGameHandshake) Color(0xFF10B981) else Color.Gray,
            isChecked = state.vivoGameHandshake,
            onCheckedChange = { onEvent(AboutUiEvent.SetVivoGameHandshake(it)) },
            icon = Icons.Default.SportsEsports
        )

        CommandToggleCard(
            title = "Sensor HAL Gyroscope Delay",
            commandSummary = "• Settings System: vivo_game_gyro_dealy_promotion (#2 Lowest)\n• vivo_game_gyro_anti_shake_promotion (#1 Recoil Smoothing)",
            statusText = if (state.vivoGyroPromotion) "Level 2 Low Latency Active" else "Standard Gyro Delay",
            statusColor = if (state.vivoGyroPromotion) Color(0xFF10B981) else Color.Gray,
            isChecked = state.vivoGyroPromotion,
            onCheckedChange = { onEvent(AboutUiEvent.SetVivoGyroPromotion(it)) },
            icon = Icons.Default.ScreenRotation
        )

        CommandToggleCard(
            title = "Touch Latency & 180Hz Sampling",
            commandSummary = "• Settings System: touch_smooth 0, vivo_game_click_delay_promotion\n• Settings Global: game_memc_request_touch_rate = 180",
            statusText = if (state.vivoTouchOptimization) "Raw Digitizer Polling Active" else "Standard Touch Polling",
            statusColor = if (state.vivoTouchOptimization) Color(0xFF10B981) else Color.Gray,
            isChecked = state.vivoTouchOptimization,
            onCheckedChange = { onEvent(AboutUiEvent.SetVivoTouchOptimization(it)) },
            icon = Icons.Default.TouchApp
        )

        ExperimentalSectionDivider(
            description = "Advanced display arbitration and periodic maintenance loop controls."
        )

        if (state.maxRefreshRate >= 144) {
            CommandToggleCard(
                title = "144Hz Frame Interpolation Unlock",
                commandSummary = "• Settings System: gamecube_frame_interpolation_for_sr = 1:1::72:144\n• OriginOS 6 144Hz MEMC override (replaces 90 FPS cap)",
                statusText = if (state.vivo144FpsUnlock) "144Hz MEMC Override Active" else "Default Refresh Arbitration",
                statusColor = if (state.vivo144FpsUnlock) Color(0xFF4FDCB8) else Color.Gray,
                isChecked = state.vivo144FpsUnlock,
                onCheckedChange = { onEvent(AboutUiEvent.SetVivo144FpsUnlock(it)) },
                icon = Icons.Default.Refresh
            )
        }

        CommandToggleCard(
            title = "2-Minute Maintenance Pulse",
            commandSummary = "2-minute periodic maintenance pulses these 3 commands. Experiment with these toggles if encountering display arbitration or refresh rate lock issues.",
            statusText = if (state.vivoMaintenancePulse) "2-Min Pulse Active" else "Pulse Disabled (Single Shot)",
            statusColor = if (state.vivoMaintenancePulse) Color(0xFF10B981) else Color.Gray,
            isChecked = state.vivoMaintenancePulse,
            onCheckedChange = { onEvent(AboutUiEvent.SetVivoMaintenancePulse(it)) },
            icon = Icons.Default.Timer
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChildCommandToggleRow(
                    title = "game_plus_mode_key",
                    commandSummary = "• Monster+ Display Render Manager",
                    isChecked = state.vivoPulseGamePlusMode,
                    enabled = state.vivoMaintenancePulse,
                    onCheckedChange = { onEvent(AboutUiEvent.SetVivoPulseGamePlusMode(it)) }
                )

                ChildCommandToggleRow(
                    title = "game_standard_promotion_mode",
                    commandSummary = "• Panel High-Refresh Promotion",
                    isChecked = state.vivoPulseStandardPromotion,
                    enabled = state.vivoMaintenancePulse,
                    onCheckedChange = { onEvent(AboutUiEvent.SetVivoPulseStandardPromotion(it)) }
                )

                ChildCommandToggleRow(
                    title = "game_scene_more_fps",
                    commandSummary = "• 3D Match Scene Frame Unlock",
                    isChecked = state.vivoPulseSceneMoreFps,
                    enabled = state.vivoMaintenancePulse,
                    onCheckedChange = { onEvent(AboutUiEvent.SetVivoPulseSceneMoreFps(it)) }
                )
            }
        }
    }
}

@Composable
private fun IqooCommandsTab(
    state: AboutUiState,
    onEvent: (AboutUiEvent) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.maxRefreshRate >= 144) {
            CommandToggleCard(
                title = "144 FPS Frame Interpolation",
                commandSummary = "system:gamecube_frame_interpolation_for_sr = 1:1::72:144",
                statusText = if (state.vivo144FpsUnlock) "144 FPS Forced" else "Native Panel Profile",
                statusColor = if (state.vivo144FpsUnlock) Color(0xFF10B981) else Color.Gray,
                isChecked = state.vivo144FpsUnlock,
                onCheckedChange = { onEvent(AboutUiEvent.SetVivo144FpsUnlock(it)) },
                icon = Icons.Default.Speed
            )
        }
    }
}
