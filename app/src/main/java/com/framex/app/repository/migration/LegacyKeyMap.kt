package com.framex.app.repository.migration

/**
 * Key names written by the pre-refactor build (`SharedPreferences("framex_settings")`) and
 * their DataStore equivalents. Keys that are not listed here kept their name.
 */
internal object LegacyKeyMap {

    const val MIGRATION_DONE_KEY = "legacy_settings_migration_v3_done"

    // Declared before [renamed], which reads it during initialisation.
    private val VIVO_SUFFIXES = listOf(
        "monster_mode", "vip_thread", "touch_optimization", "game_handshake",
        "gyro_promotion", "maintenance_pulse", "pulse_game_plus_mode",
        "pulse_scene_more_fps", "pulse_standard_promotion"
    )

    /** Old key to new key for every setting that was renamed by the refactor. */
    val renamed: Map<String, String> = buildMap {
        put("esports_cpu_priority_lock", "exec_cpu_priority_lock")
        put("esports_fixed_performance_mode", "exec_fixed_performance_mode")
        put("esports_network_firewall", "exec_network_firewall")
        put("esports_refresh_rate_lock", "exec_refresh_rate_lock")
        put("esports_touch_boost", "exec_touch_boost")
        put("esports_vivo_opt_enabled", "vivo_opt_enabled")
        put("gaming_disable_thermal_throttling", "exec_disable_thermal_throttling")
        put("gaming_disable_phantom_proc_killer", "exec_disable_phantom_proc_killer")
        put("gaming_ram_cache_pre_trim_enabled", "exec_ram_cache_pre_trim")
        put("gaming_deep_freeze_enabled", "deep_freeze_enabled")
        put("gaming_vivo_144fps_unlock", "exec_vivo_144_fps_unlock")
        for (suffix in VIVO_SUFFIXES) put("gaming_vivo_$suffix", "exec_vivo_$suffix")
        put("gaming_mode_active", "is_gaming_mode_active")
        put("gaming_affected_pkgs", "gaming_affected_packages")
        put("gaming_opt_snapshot", "gaming_optimization_snapshot_json")
        put("thermal_override_active", "needs_thermal_override_recovery")
        put("is_onboarding_completed", "onboarding_completed")
        put("overlay_x", "overlay_x_port")
        put("overlay_y", "overlay_y_port")
        put("overlay_landscape_x", "overlay_x_land")
        put("overlay_landscape_y", "overlay_y_land")
    }

    /** Old keys that have no reader in the current code and are intentionally not carried over. */
    val dropped: Set<String> = setOf(
        "legacy_settings_cleanup_version",
        "thermal_override_recovery_version",
        "esports_frame_pacing_overlay"
    )

    /** Position keys whose old default of -1 meant "not set yet". */
    val positionKeys: Set<String> = setOf(
        "overlay_x", "overlay_y", "overlay_landscape_x", "overlay_landscape_y"
    )
}
