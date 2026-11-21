package com.framex.app.repository.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyMigrationPlannerTest {

    private val icons = setOf("fps", "cpu")

    private fun plan(
        legacy: Map<String, Any?> = emptyMap(),
        intermediate: Map<String, Any?> = emptyMap(),
        current: Map<String, Any> = emptyMap()
    ) = LegacyMigrationPlanner.plan(LegacySources(legacy, intermediate, current, icons))

    @Test
    fun renamedKeysAreCarriedOverWithTheirValues() {
        val result = plan(
            legacy = mapOf(
                "esports_vivo_opt_enabled" to false,
                "gaming_vivo_144fps_unlock" to true,
                "gaming_vivo_monster_mode" to true,
                "is_onboarding_completed" to true
            )
        )
        assertEquals(false, result["vivo_opt_enabled"])
        assertEquals(true, result["exec_vivo_144_fps_unlock"])
        assertEquals(true, result["exec_vivo_monster_mode"])
        assertEquals(true, result["onboarding_completed"])
    }

    @Test
    fun crashRecoveryKeysAreMigrated() {
        val result = plan(
            legacy = mapOf(
                "gaming_mode_active" to true,
                "gaming_affected_pkgs" to setOf("a.b", "c.d"),
                "gaming_opt_snapshot" to "{\"x\":1}",
                "thermal_override_active" to true
            )
        )
        assertEquals(true, result["is_gaming_mode_active"])
        assertEquals(setOf("a.b", "c.d"), result["gaming_affected_packages"])
        assertEquals("{\"x\":1}", result["gaming_optimization_snapshot_json"])
        assertEquals(true, result["needs_thermal_override_recovery"])
    }

    @Test
    fun affectedPackagesAreMergedWithExistingNotReplaced() {
        val result = plan(
            legacy = mapOf("gaming_affected_pkgs" to setOf("old.app")),
            current = mapOf("gaming_affected_packages" to setOf("new.app"))
        )
        assertEquals(setOf("old.app", "new.app"), result["gaming_affected_packages"])
    }

    @Test
    fun valueAlreadyInDataStoreIsNeverOverwritten() {
        val result = plan(
            legacy = mapOf("esports_touch_boost" to true),
            intermediate = mapOf("exec_touch_boost" to true),
            current = mapOf("exec_touch_boost" to false)
        )
        assertFalse(result.containsKey("exec_touch_boost"))
    }

    @Test
    fun intermediateMirrorBeatsOldFile() {
        val result = plan(
            legacy = mapOf("esports_touch_boost" to true),
            intermediate = mapOf("exec_touch_boost" to false)
        )
        assertEquals(false, result["exec_touch_boost"])
    }

    @Test
    fun upgradersKeepOldDefaultsForAbsentKeys() {
        val result = plan(legacy = mapOf("overlay_mode" to "Compact"))
        assertEquals(setOf("fps"), result["enabled_modules"])
        assertEquals(icons, result["enabled_module_icons"])
        assertEquals(0.75f, result["overlay_opacity"])
    }

    @Test
    fun explicitOldValuesAreNotReplacedByOldDefaults() {
        val result = plan(legacy = mapOf("overlay_opacity" to 0.5f, "enabled_modules" to setOf("cpu")))
        assertEquals(0.5f, result["overlay_opacity"])
        assertEquals(setOf("cpu"), result["enabled_modules"])
    }

    @Test
    fun freshInstallGetsNoMaterializedDefaults() {
        val result = plan()
        assertNull(result["enabled_modules"])
        assertNull(result["overlay_opacity"])
    }

    @Test
    fun legacyUnsetPositionSentinelIsSkipped() {
        val result = plan(legacy = mapOf("overlay_x" to -1, "overlay_y" to 250, "overlay_landscape_x" to 30))
        assertFalse(result.containsKey("overlay_x_port"))
        assertEquals(250, result["overlay_y_port"])
        assertEquals(30, result["overlay_x_land"])
    }

    @Test
    fun outOfRangeTextSizeFromIntermediateBuildIsReset() {
        val result = plan(current = mapOf("overlay_text_size" to 11))
        assertEquals(1, result["overlay_text_size"])
    }

    @Test
    fun validTextSizeIsKept() {
        val result = plan(legacy = mapOf("overlay_text_size" to 2))
        assertEquals(2, result["overlay_text_size"])
    }

    @Test
    fun droppedKeysAreNotMigrated() {
        val result = plan(legacy = mapOf("legacy_settings_cleanup_version" to 2, "esports_frame_pacing_overlay" to true))
        assertFalse(result.containsKey("legacy_settings_cleanup_version"))
        assertFalse(result.containsKey("esports_frame_pacing_overlay"))
    }

    @Test
    fun existingUserIsSeededToShizukuMode() {
        val result = plan(legacy = mapOf("is_onboarding_completed" to true))
        assertEquals("shizuku", result["privilege_mode"])
    }

    @Test
    fun explicitPrivilegeModeIsKept() {
        val result = plan(
            legacy = mapOf("is_onboarding_completed" to true),
            current = mapOf("privilege_mode" to "root")
        )
        assertFalse(result.containsKey("privilege_mode"))
    }

    @Test
    fun freshInstallIsNotSeededWithAPrivilegeMode() {
        assertNull(plan()["privilege_mode"])
    }

    @Test
    fun discoveryFlagsKeepTheirNames() {
        val result = plan(legacy = mapOf("globally_dismissed" to true, "completed_dashboard" to true))
        assertEquals(true, result["globally_dismissed"])
        assertEquals(true, result["completed_dashboard"])
    }
}
