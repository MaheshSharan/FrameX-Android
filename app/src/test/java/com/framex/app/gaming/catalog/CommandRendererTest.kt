package com.framex.app.gaming.catalog

import com.framex.app.gaming.ledger.OpPriority
import com.framex.app.gaming.ledger.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommandRendererTest {

    private val running = ResolveContext("com.game.app", uid = 4242, refreshRateHz = 144)

    private fun settingsEntry(key: String) =
        GamingCommandCatalog.entries.first {
            val t = it.target
            t is CommandTarget.SettingsKey && t.key == key
        }

    private fun serviceEntry(stableId: String) =
        GamingCommandCatalog.entries.first {
            val t = it.target
            t is CommandTarget.ServiceCommand && t.stableId == stableId
        }

    @Test
    fun settingsValuesAreWrittenWithoutQuotesWhenBare() {
        assertEquals(
            "settings put system touch_response_speed 2",
            CommandRenderer.apply(settingsEntry("touch_response_speed"), running)
        )
    }

    @Test
    fun refreshRateValueCarriesTheResolvedHz() {
        assertEquals(
            "settings put system peak_refresh_rate 144",
            CommandRenderer.apply(settingsEntry("peak_refresh_rate"), running)
        )
    }

    @Test
    fun serviceCommandsReturnTheirActivateLineDirectly() {
        assertEquals(
            "cmd thermalservice override-status 0",
            CommandRenderer.apply(serviceEntry("thermal_override"), running)
        )
    }

    @Test
    fun perGameServiceCommandsCarryTheResolvedPackage() {
        assertEquals(
            "cmd activity set-bg-restriction-level --user 0 com.game.app unrestricted",
            CommandRenderer.apply(serviceEntry("cpu_priority_bg_restriction"), running)
        )
    }

    @Test
    fun unsafeSettingsValuesAreRefused() {
        listOf("a b", "x;reboot", "\$(id)", "a\"b", "", "`id`", "a|b").forEach { value ->
            val unsafe = settingsEntry("touch_response_speed").copy(value = { value })
            assertNull("accepted: $value", CommandRenderer.apply(unsafe, running))
        }
    }

    @Test
    fun baselineIsWrittenBackWhenItExisted() {
        assertEquals(
            "settings put system touch_response_speed 0",
            CommandRenderer.restore(settingsEntry("touch_response_speed"), "0", running)
        )
    }

    @Test
    fun missingBaselineDeletesTheSettingsKey() {
        assertEquals(
            "settings delete system touch_response_speed",
            CommandRenderer.restore(settingsEntry("touch_response_speed"), null, running)
        )
    }

    @Test
    fun fixedRevertIgnoresTheBaselineAndCarriesThePackage() {
        val command = CommandRenderer.restore(serviceEntry("cpu_priority_bg_restriction"), "irrelevant", running)
        assertEquals("cmd activity set-bg-restriction-level --user 0 com.game.app adaptive_bucket", command)
    }

    @Test
    fun oneShotEntriesNeverRestore() {
        val entry = GamingCommandCatalog.entries.first {
            (it.target as? CommandTarget.ServiceCommand)?.stableId == "ram_trim_caches"
        }
        assertEquals(Restore.NONE, entry.restore)
        assertNull(CommandRenderer.restore(entry, "anything", running))
    }

    @Test
    fun unsafeBaselineIsNeverTurnedIntoADelete() {
        assertNull(CommandRenderer.restore(settingsEntry("touch_response_speed"), "a b; reboot", running))
    }

    @Test
    fun captureLineReadsSettingsKeysThroughSettingsGet() {
        val line = CommandRenderer.captureLine(settingsEntry("touch_response_speed"))
        assertNotNull(line)
        assertEquals(
            "echo \"FXB|settings:system:touch_response_speed|\$(settings get system touch_response_speed 2>/dev/null)\"",
            line
        )
    }

    @Test
    fun captureLineIsNullForAServiceCommandWithNoCapture() {
        assertNull(CommandRenderer.captureLine(serviceEntry("thermal_override")))
    }

    @Test
    fun phantomProcOverrideHasNoCaptureAndRestoresViaFixedRevert() {
        val entry = GamingCommandCatalog.entries.first {
            (it.target as? CommandTarget.ServiceCommand)?.stableId == "phantom_proc_max_override"
        }
        assertNull(CommandRenderer.captureLine(entry))
        assertEquals(
            "device_config delete activity_manager max_phantom_processes",
            CommandRenderer.restore(entry, null, running)
        )
    }

    @Test
    fun priorityAndStageSurviveACopy() {
        val entry = settingsEntry("peak_refresh_rate")
        assertEquals(Stage.DISPLAY, entry.stage)
        assertEquals(OpPriority.PRIMARY, entry.priority)
    }
}
