package com.framex.app.gaming.catalog

import com.framex.app.gaming.ledger.OpPriority.PRIMARY
import com.framex.app.gaming.ledger.Stage

private val IQOO = setOf(Platform.IQOO)

private fun iqoo(
    table: SettingsTable,
    key: String,
    stage: Stage,
    toggle: Toggle,
    phase: Phase = Phase.ACTIVATION,
    need: Need = Need.NONE,
    restore: Restore = Restore.BASELINE,
    value: (ResolveContext) -> String
) = CommandEntry(table, key, stage, toggle, IQOO, PRIMARY, phase, need, restore, value)

private fun fixed(value: String): (ResolveContext) -> String = { value }

/** Settings written on iQOO hardware, in the order they are applied. */
internal val IQOO_COMMANDS: List<CommandEntry> = listOf(
    iqoo(SettingsTable.SYSTEM, "game_optimize_brightness", Stage.DISPLAY, Toggle.THERMAL_UNLOCK, value = fixed("0")),
    iqoo(SettingsTable.SECURE, "game_cube_temper_control", Stage.DISPLAY, Toggle.THERMAL_UNLOCK, value = fixed("0")),
    iqoo(SettingsTable.SYSTEM, "gamecube_frame_interpolation_for_sr", Stage.DISPLAY, Toggle.FPS_144_UNLOCK, value = fixed("1:1::72:144")),
    iqoo(SettingsTable.SYSTEM, "sdk_game_target_fps", Stage.HANDSHAKE, Toggle.GAME_HANDSHAKE, need = Need.PACKAGE_AND_PID, restore = Restore.DELETE) {
        "${it.packageOrDefault}_${it.pid}_${it.refreshRateHz}"
    },
    iqoo(SettingsTable.SYSTEM, "sdk_game_scene", Stage.HANDSHAKE, Toggle.GAME_HANDSHAKE, need = Need.PACKAGE_AND_PID, restore = Restore.DELETE) {
        "${it.packageOrDefault}_${it.pid}_0"
    },
    iqoo(SettingsTable.SECURE, "sdk_game_scene", Stage.HANDSHAKE, Toggle.GAME_HANDSHAKE, need = Need.PACKAGE_AND_PID, restore = Restore.DELETE) {
        "${it.packageOrDefault}_${it.pid}_0"
    },
    iqoo(SettingsTable.SYSTEM, "vts_game_para_adjust", Stage.TOUCH, Toggle.TOUCH_OPTIMIZATION, value = fixed("1,5,5,5")),
    iqoo(SettingsTable.GLOBAL, "game_memc_request_touch_rate", Stage.TOUCH, Toggle.TOUCH_OPTIMIZATION, value = fixed("180")),
    iqoo(SettingsTable.SYSTEM, "vivo_game_gyro_data_prediction", Stage.GYRO, Toggle.GYRO_PROMOTION, value = fixed("1")),
    iqoo(SettingsTable.GLOBAL, "game_cube_vip_thread", Stage.KERNEL, Toggle.VIP_THREAD, value = fixed("1")),
    iqoo(SettingsTable.SYSTEM, "game_scene_more_fps", Stage.DISPLAY, Toggle.PULSE_SCENE_MORE_FPS, phase = Phase.PULSE, value = fixed("1"))
)
