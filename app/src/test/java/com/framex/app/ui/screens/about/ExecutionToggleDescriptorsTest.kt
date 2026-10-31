package com.framex.app.ui.screens.about

import com.framex.app.gaming.catalog.GamingCommandCatalog
import com.framex.app.gaming.catalog.Phase
import com.framex.app.gaming.catalog.Platform
import com.framex.app.gaming.catalog.Toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionToggleDescriptorsTest {

    private val tabs = mapOf(Platform.VIVO to VIVO_TAB, Platform.IQOO to IQOO_TAB)

    /** Thermal unlock for Vivo is offered on the universal tab, not the Vivo tab. */
    private val shownElsewhere = setOf(Platform.VIVO to Toggle.THERMAL_UNLOCK)

    @Test
    fun everyCatalogSwitchHasACardOnItsPlatformTab() {
        tabs.forEach { (platform, tab) ->
            val shown = (tab.toggles + tab.dividerToggles).map { it.toggle }.toSet()
            val needed = GamingCommandCatalog.forPlatform(platform)
                .filter { it.phase == Phase.ACTIVATION && it.toggle != Toggle.ALWAYS }
                .map { it.toggle }
                .filter { (platform to it) !in shownElsewhere }
                .toSet()
            assertTrue("$platform is missing cards for ${needed - shown}", shown.containsAll(needed))
        }
    }

    @Test
    fun everyCardDrivesAtLeastOneCatalogCommand() {
        tabs.forEach { (platform, tab) ->
            val inCatalog = GamingCommandCatalog.forPlatform(platform).map { it.toggle }.toSet()
            (tab.toggles + tab.dividerToggles).forEach {
                assertTrue("${it.title} has no command on $platform", it.toggle in inCatalog)
            }
        }
    }

    @Test
    fun pulseChildrenMatchTheCatalogPulseEntries() {
        tabs.forEach { (platform, tab) ->
            val catalogPulse = GamingCommandCatalog.forPlatform(platform)
                .filter { it.phase == Phase.PULSE }.map { it.toggle }.toSet()
            assertEquals(catalogPulse, tab.pulse.children.map { it.toggle }.toSet())
        }
    }

    @Test
    fun summaryListsMainCommandsWithStaticValuesOnly() {
        val lines = CommandSummary.lines(Platform.VIVO, Toggle.GAME_HANDSHAKE)
        assertTrue(lines.any { it.contains("sdk_game_target_fps") && !it.contains("=") })
        assertEquals(
            listOf("• Settings Global: game_cube_vip_thread = 1"),
            CommandSummary.lines(Platform.VIVO, Toggle.VIP_THREAD)
        )
    }

    @Test
    fun everyToggleHasAnEventExceptAlways() {
        Toggle.values().forEach { toggle ->
            if (toggle == Toggle.ALWAYS) {
                assertEquals(null, toggle.toEvent(true))
            } else {
                assertNotNull(toggle.toEvent(true))
            }
        }
    }

    @Test
    fun stateReadsTheMatchingSwitch() {
        val state = AboutUiState(vivoVipThread = true, disableThermalThrottling = true)
        assertTrue(state.isOn(Toggle.VIP_THREAD))
        assertTrue(state.isOn(Toggle.THERMAL_UNLOCK))
        assertTrue(!state.isOn(Toggle.MONSTER_MODE))
    }
}
