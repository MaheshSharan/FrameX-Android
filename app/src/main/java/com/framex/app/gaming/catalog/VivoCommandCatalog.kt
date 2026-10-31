package com.framex.app.gaming.catalog

import com.framex.app.gaming.ledger.OpPriority.DETAIL
import com.framex.app.gaming.ledger.OpPriority.PRIMARY
import com.framex.app.gaming.ledger.Stage

private val VIVO = setOf(Platform.VIVO)

private fun vivo(
    table: SettingsTable,
    key: String,
    stage: Stage,
    toggle: Toggle,
    priority: com.framex.app.gaming.ledger.OpPriority,
    phase: Phase = Phase.ACTIVATION,
    need: Need = Need.NONE,
    restore: Restore = Restore.BASELINE,
    value: (ResolveContext) -> String
) = CommandEntry(table, key, stage, toggle, VIVO, priority, phase, need, restore, value)

private fun fixed(value: String): (ResolveContext) -> String = { value }

/** Settings written on Vivo hardware, in the order they are applied. */
internal val VIVO_COMMANDS: List<CommandEntry> = listOf(
    // Monster Mode power profile.
    vivo(SettingsTable.SECURE, "system_property_power_mode_type", Stage.POWER, Toggle.MONSTER_MODE, PRIMARY, value = fixed("5")),
    vivo(SettingsTable.GLOBAL, "bbb_perf_mode", Stage.POWER, Toggle.MONSTER_MODE, PRIMARY, value = fixed("1")),
    vivo(SettingsTable.SYSTEM, "power_save_type", Stage.POWER, Toggle.MONSTER_MODE, DETAIL, value = fixed("5")),
    vivo(SettingsTable.SECURE, "power_save_type", Stage.POWER, Toggle.MONSTER_MODE, DETAIL, value = fixed("5")),
    vivo(SettingsTable.SYSTEM, "power_save_auto_exit", Stage.POWER, Toggle.MONSTER_MODE, DETAIL, value = fixed("0")),
    vivo(SettingsTable.SYSTEM, "power_sleep_mode_enabled", Stage.POWER, Toggle.MONSTER_MODE, DETAIL, value = fixed("0")),
    vivo(SettingsTable.GLOBAL, "low_power_mode_opened", Stage.POWER, Toggle.MONSTER_MODE, DETAIL, value = fixed("0")),
    // Display and Game Space.
    vivo(SettingsTable.SYSTEM, "com.vivo.vivoconsole.icon.status", Stage.DISPLAY, Toggle.ALWAYS, PRIMARY, value = fixed("2")),
    vivo(SettingsTable.SYSTEM, "game_optimize_brightness", Stage.DISPLAY, Toggle.ALWAYS, DETAIL, value = fixed("0")),
    vivo(SettingsTable.SECURE, "game_cube_temper_control", Stage.DISPLAY, Toggle.THERMAL_UNLOCK, PRIMARY, value = fixed("0")),
    // Live target-FPS handshake with the running game.
    vivo(SettingsTable.SYSTEM, "sdk_game_target_fps", Stage.HANDSHAKE, Toggle.GAME_HANDSHAKE, PRIMARY, need = Need.PACKAGE_AND_PID, restore = Restore.DELETE) {
        "${it.packageOrDefault}_${it.pid}_${it.refreshRateHz}"
    },
    vivo(SettingsTable.SYSTEM, "sdk_game_scene", Stage.HANDSHAKE, Toggle.GAME_HANDSHAKE, PRIMARY, need = Need.PACKAGE_AND_PID, restore = Restore.DELETE) {
        "${it.packageOrDefault}_${it.pid}_0"
    },
    vivo(SettingsTable.SECURE, "sdk_game_scene", Stage.HANDSHAKE, Toggle.GAME_HANDSHAKE, DETAIL, need = Need.PACKAGE_AND_PID, restore = Restore.DELETE) {
        "${it.packageOrDefault}_${it.pid}_0"
    },
    // Hardware gyroscope promotion.
    vivo(SettingsTable.SYSTEM, "vivo_game_gyro_promotion", Stage.GYRO, Toggle.GYRO_PROMOTION, PRIMARY, need = Need.PACKAGE, restore = Restore.DELETE) {
        "1@${it.packageOrDefault}"
    },
    vivo(SettingsTable.SYSTEM, "vivo_game_gyro_dealy_promotion", Stage.GYRO, Toggle.GYRO_PROMOTION, DETAIL, need = Need.PACKAGE, restore = Restore.DELETE) {
        "${it.packageOrDefault}#2"
    },
    vivo(SettingsTable.SYSTEM, "vivo_game_gyro_anti_shake_promotion", Stage.GYRO, Toggle.GYRO_PROMOTION, DETAIL, need = Need.PACKAGE, restore = Restore.DELETE) {
        "${it.packageOrDefault}#1"
    },
    vivo(SettingsTable.SYSTEM, "vivo_game_gyro_data_prediction", Stage.GYRO, Toggle.GYRO_PROMOTION, DETAIL, value = fixed("1")),
    // Touch digitizer.
    vivo(SettingsTable.SYSTEM, "vts_game_para_adjust", Stage.TOUCH, Toggle.TOUCH_OPTIMIZATION, PRIMARY, value = fixed("1,5,5,5")),
    vivo(SettingsTable.SYSTEM, "touch_smooth", Stage.TOUCH, Toggle.TOUCH_OPTIMIZATION, DETAIL, value = fixed("1")),
    vivo(SettingsTable.GLOBAL, "game_memc_request_touch_rate", Stage.TOUCH, Toggle.TOUCH_OPTIMIZATION, PRIMARY, value = fixed("180")),
    vivo(SettingsTable.SYSTEM, "vivo_game_click_delay_promotion", Stage.TOUCH, Toggle.TOUCH_OPTIMIZATION, DETAIL, need = Need.PACKAGE, restore = Restore.DELETE) {
        "${it.packageOrDefault}#2"
    },
    vivo(SettingsTable.SYSTEM, "vivo_game_touch_delay_promotion", Stage.TOUCH, Toggle.TOUCH_OPTIMIZATION, DETAIL, need = Need.PACKAGE, restore = Restore.DELETE) {
        "${it.packageOrDefault}#2"
    },
    // Kernel scheduler.
    vivo(SettingsTable.GLOBAL, "game_cube_vip_thread", Stage.KERNEL, Toggle.VIP_THREAD, PRIMARY, value = fixed("1")),
    vivo(SettingsTable.GLOBAL, "speed_mode_apps", Stage.KERNEL, Toggle.VIP_THREAD, DETAIL, need = Need.PACKAGE, restore = Restore.DELETE) {
        it.packageOrDefault
    },
    // Maintenance pulse, repeated while the session runs.
    vivo(SettingsTable.SYSTEM, "game_plus_mode_key", Stage.POWER, Toggle.PULSE_GAME_PLUS, PRIMARY, phase = Phase.PULSE, value = fixed("1")),
    vivo(SettingsTable.SYSTEM, "game_standard_promotion_mode", Stage.POWER, Toggle.PULSE_STANDARD_PROMOTION, DETAIL, phase = Phase.PULSE, value = fixed("1")),
    vivo(SettingsTable.SYSTEM, "game_scene_more_fps", Stage.POWER, Toggle.PULSE_SCENE_MORE_FPS, PRIMARY, phase = Phase.PULSE, value = fixed("1"))
)
