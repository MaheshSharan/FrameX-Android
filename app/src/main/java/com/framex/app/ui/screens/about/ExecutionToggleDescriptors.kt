package com.framex.app.ui.screens.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.Color
import com.framex.app.gaming.catalog.Toggle

private val Amber = Color(0xFFF59E0B)
private val Mint = Color(0xFF4FDCB8)

private val pulseChildren = listOf(
    PulseChild(Toggle.PULSE_GAME_PLUS, "• Monster+ Display Render Manager"),
    PulseChild(Toggle.PULSE_STANDARD_PROMOTION, "• Panel High-Refresh Promotion"),
    PulseChild(Toggle.PULSE_SCENE_MORE_FPS, "• 3D Match Scene Frame Unlock")
)

/** Wording for the Vivo tab. */
val VIVO_TAB = VendorTabDescriptor(
    toggles = listOf(
        ToggleDescriptor(
            Toggle.MONSTER_MODE, "Monster Mode & BBK HAL State", "Monster Mode 5 Active", "Standard Power Mode",
            Icons.Default.Bolt, detail = "vivo-vperf-hal power profile",
            footnote = "Activate if your device is Vivo. Most iQOO devices already provide native Monster Mode in system settings.",
            accent = Amber
        ),
        ToggleDescriptor(
            Toggle.VIP_THREAD, "GameCube VIP Thread Scheduler", "VIP Scheduling Active", "Standard Scheduler",
            Icons.Default.Speed, detail = "RMServerExtension prioritizes RenderThread to Prime Core"
        ),
        ToggleDescriptor(
            Toggle.GAME_HANDSHAKE, "Live Target FPS Handshake", "Target FPS Handshake Active", "Handshake Inactive",
            Icons.Default.SportsEsports, detail = "Real-time match PID handshake with GameManagerService"
        ),
        ToggleDescriptor(
            Toggle.GYRO_PROMOTION, "Sensor HAL Gyroscope Delay", "Level 2 Low Latency Active", "Standard Gyro Delay",
            Icons.Default.ScreenRotation, detail = "Delay promotion #2 (lowest), anti-shake #1 (recoil smoothing)"
        ),
        ToggleDescriptor(
            Toggle.TOUCH_OPTIMIZATION, "Touch Latency & 180Hz Sampling", "Raw Digitizer Polling Active",
            "Standard Touch Polling", Icons.Default.TouchApp, detail = "Per-game click and touch delay promotion"
        )
    ),
    divider = "Advanced display arbitration and periodic maintenance loop controls.",
    dividerToggles = listOf(
        ToggleDescriptor(
            Toggle.FPS_144_UNLOCK, "144Hz Frame Interpolation Unlock", "144Hz MEMC Override Active",
            "Default Refresh Arbitration", Icons.Default.Refresh,
            detail = "OriginOS 6 144Hz MEMC override (replaces 90 FPS cap)", accent = Mint, minRefreshRateHz = 144
        )
    ),
    pulse = PulseDescriptor(
        "2-minute periodic maintenance pulses these commands. Experiment with these toggles if encountering " +
            "display arbitration or refresh rate lock issues.",
        pulseChildren
    )
)

/** Wording for the iQOO tab. */
val IQOO_TAB = VendorTabDescriptor(
    toggles = listOf(
        ToggleDescriptor(
            Toggle.VIP_THREAD, "GameCube VIP Thread Scheduler", "VIP Scheduling Active", "Standard Scheduler",
            Icons.Default.Speed, detail = "Prioritizes the main render thread to prime CPU cores"
        ),
        ToggleDescriptor(
            Toggle.GAME_HANDSHAKE, "Live Target FPS Handshake", "Target FPS Handshake Active", "Handshake Inactive",
            Icons.Default.SportsEsports, detail = "Real-time match PID handshake with GameWatch arbiter"
        ),
        ToggleDescriptor(
            Toggle.TOUCH_OPTIMIZATION, "Touch Digitizer & 180Hz Polling", "Touch Acceleration Active",
            "Standard Touch Polling", Icons.Default.TouchApp
        ),
        ToggleDescriptor(
            Toggle.GYRO_PROMOTION, "Hardware Gyroscope Prediction", "Gyro Prediction Active", "Standard Gyroscope",
            Icons.Default.ScreenRotation, detail = "Low-latency sensor prediction filtering"
        ),
        ToggleDescriptor(
            Toggle.THERMAL_UNLOCK, "Display Thermals & Brightness Guard", "Thermal Guard Active",
            "Standard Thermal Throttling", Icons.Default.Thermostat
        ),
        ToggleDescriptor(
            Toggle.FPS_144_UNLOCK, "144 FPS Frame Interpolation", "144 FPS Forced", "Native Panel Profile",
            Icons.Default.Refresh, detail = "OriginOS 144Hz MEMC override", minRefreshRateHz = 144
        )
    ),
    divider = "Advanced periodic maintenance loop controls.",
    dividerToggles = emptyList(),
    pulse = PulseDescriptor(
        "2-minute periodic maintenance pulses the 3D scene frame unlock flag during gameplay.",
        listOf(pulseChildren.last())
    )
)
