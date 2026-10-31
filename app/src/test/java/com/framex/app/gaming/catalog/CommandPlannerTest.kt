package com.framex.app.gaming.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPlannerTest {

    private fun togglesOn(vararg on: Toggle) = ToggleState { it == Toggle.ALWAYS || it in on }

    private val everything = ToggleState { true }
    private val nothing = ToggleState { it == Toggle.ALWAYS }

    @Test
    fun everyPlatformHasUniqueEntryIds() {
        Platform.values().forEach { platform ->
            val ids = GamingCommandCatalog.forPlatform(platform).map { it.id }
            assertEquals("duplicate id on $platform", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun switchesOffRemoveTheirEntriesFromThePlan() {
        val plan = CommandPlanner.plan(Platform.VIVO, nothing)
        assertTrue(plan.enabled.all { it.toggle == Toggle.ALWAYS })
        assertTrue(plan.disabled.any { it.key == "system_property_power_mode_type" })
        assertEquals(
            GamingCommandCatalog.forPlatform(Platform.VIVO).size,
            plan.enabled.size + plan.disabled.size
        )
    }

    @Test
    fun monsterModeEnablesThePowerEntriesOnly() {
        val plan = CommandPlanner.plan(Platform.VIVO, togglesOn(Toggle.MONSTER_MODE))
        val keys = plan.enabled.map { it.key }
        assertTrue("power_save_type" in keys)
        assertFalse("vts_game_para_adjust" in keys)
    }

    @Test
    fun restoreSetEqualsApplySetForEveryPlatformAndSwitchState() {
        for (platform in Platform.values()) {
            for (toggles in listOf(everything, nothing, togglesOn(Toggle.TOUCH_OPTIMIZATION))) {
                val plan = CommandPlanner.plan(platform, toggles)
                val captured = plan.enabled.map { it.id }.toSet()
                val restored = CommandPlanner.capturedEntries(platform, captured)
                assertEquals(plan.enabled.map { it.id }, restored.map { it.id })
            }
        }
    }

    @Test
    fun pulseEntriesAreSeparatedFromActivationEntries() {
        val plan = CommandPlanner.plan(Platform.VIVO, everything)
        assertTrue(plan.pulse().all { it.phase == Phase.PULSE })
        assertTrue(plan.activation().none { it.phase == Phase.PULSE })
        assertEquals(setOf("game_plus_mode_key", "game_standard_promotion_mode", "game_scene_more_fps"), plan.pulse().map { it.key }.toSet())
    }

    @Test
    fun iqooPlanNeverContainsVivoOnlyKeys() {
        val keys = CommandPlanner.plan(Platform.IQOO, everything).enabled.map { it.key }
        assertFalse("system_property_power_mode_type" in keys)
        assertFalse("speed_mode_apps" in keys)
        assertTrue("gamecube_frame_interpolation_for_sr" in keys)
    }

    @Test
    fun entriesThatNeedAGameAreWaitingUntilItsPackageAndPidAreKnown() {
        val noGame = ResolveContext(null, 0, 120)
        val packageOnly = ResolveContext("com.game.app", 0, 120)
        val running = ResolveContext("com.game.app", 4242, 120)
        assertFalse(noGame.satisfies(Need.PACKAGE))
        assertTrue(packageOnly.satisfies(Need.PACKAGE))
        assertFalse(packageOnly.satisfies(Need.PACKAGE_AND_PID))
        assertTrue(running.satisfies(Need.PACKAGE_AND_PID))
    }
}
