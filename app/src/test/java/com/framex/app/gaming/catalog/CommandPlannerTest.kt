package com.framex.app.gaming.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPlannerTest {

    private fun togglesOn(vararg on: Toggle) = ToggleState { it == Toggle.ALWAYS || it in on }

    private val everything = ToggleState { true }
    private val nothing = ToggleState { it == Toggle.ALWAYS }
    private val allCapable = CapableLookup { true }
    private val noneCapable = CapableLookup { id -> id.startsWith("settings:secure:user_preferred_display_mode_id") }

    @Test
    fun everyCatalogEntryHasAUniqueId() {
        val ids = GamingCommandCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun switchesOffRemoveTheirEntriesFromThePlan() {
        val plan = CommandPlanner.plan(nothing, allCapable)
        assertTrue(plan.enabled.all { it.toggle == Toggle.ALWAYS })
        assertTrue(plan.disabled.any { it.toggle == Toggle.RAM_CACHE_PRE_TRIM })
        assertEquals(GamingCommandCatalog.entries.size, plan.enabled.size + plan.disabled.size)
    }

    @Test
    fun aCapabilityMissReMovesTheEntryEvenWhenItsSwitchIsOn() {
        val plan = CommandPlanner.plan(everything, noneCapable)
        assertTrue(plan.enabled.all { it.toggle == Toggle.ALWAYS })
        assertTrue(plan.disabled.any { it.toggle == Toggle.TOUCH_BOOST })
    }

    @Test
    fun touchBoostEnablesOnlyTheTouchResponseEntry() {
        val plan = CommandPlanner.plan(togglesOn(Toggle.TOUCH_BOOST), allCapable)
        val ids = plan.enabled.map { it.id }
        assertTrue(ids.any { it.contains("touch_response_speed") })
        assertFalse(ids.any { it.contains("peak_refresh_rate") })
    }

    @Test
    fun restoreSetEqualsApplySetForEverySwitchState() {
        for (toggles in listOf(everything, nothing, togglesOn(Toggle.TOUCH_BOOST))) {
            val plan = CommandPlanner.plan(toggles, allCapable)
            val captured = plan.enabled.map { it.id }.toSet()
            val restored = CommandPlanner.capturedEntries(captured)
            assertEquals(plan.enabled.map { it.id }.toSet(), restored.map { it.id }.toSet())
        }
    }

    @Test
    fun entriesThatNeedAGameAreWaitingUntilItsPackageOrUidAreKnown() {
        val noGame = ResolveContext(null)
        val packageOnly = ResolveContext("com.game.app")
        val packageAndUid = ResolveContext("com.game.app", uid = 4242)
        assertFalse(noGame.satisfies(Need.PACKAGE))
        assertTrue(packageOnly.satisfies(Need.PACKAGE))
        assertFalse(packageOnly.satisfies(Need.PACKAGE_AND_UID))
        assertTrue(packageAndUid.satisfies(Need.PACKAGE_AND_UID))
    }
}
