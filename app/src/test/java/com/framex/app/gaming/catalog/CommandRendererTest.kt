package com.framex.app.gaming.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommandRendererTest {

    private val running = ResolveContext("com.game.app", 4242, 144)

    private fun entry(key: String, platform: Platform = Platform.VIVO) =
        GamingCommandCatalog.forPlatform(platform).first { it.key == key }

    @Test
    fun plainValuesAreWrittenWithoutQuotes() {
        assertEquals(
            "content insert --uri content://settings/global --bind name:s:bbb_perf_mode --bind value:s:1",
            CommandRenderer.apply(entry("bbb_perf_mode"), running)
        )
    }

    @Test
    fun valuesWithSeparatorsAreQuoted() {
        assertEquals(
            "content insert --uri content://settings/system --bind name:s:vts_game_para_adjust --bind value:s:\"1,5,5,5\"",
            CommandRenderer.apply(entry("vts_game_para_adjust"), running)
        )
    }

    @Test
    fun handshakeValueCarriesPackagePidAndRefreshRate() {
        val command = CommandRenderer.apply(entry("sdk_game_target_fps"), running)
        assertEquals(
            "content insert --uri content://settings/system --bind name:s:sdk_game_target_fps --bind value:s:com.game.app_4242_144",
            command
        )
    }

    @Test
    fun gyroValuesUseTheTargetPackage() {
        assertEquals(
            "content insert --uri content://settings/system --bind name:s:vivo_game_gyro_promotion --bind value:s:\"1@com.game.app\"",
            CommandRenderer.apply(entry("vivo_game_gyro_promotion"), running)
        )
    }

    @Test
    fun unsafeValuesAreRefused() {
        listOf("a b", "x;reboot", "\$(id)", "a\"b", "", "`id`", "a|b").forEach { value ->
            val unsafe = entry("bbb_perf_mode").copy(value = { value })
            assertNull("accepted: $value", CommandRenderer.apply(unsafe, running))
        }
    }

    @Test
    fun baselineIsWrittenBackWhenItExisted() {
        assertEquals(
            "content insert --uri content://settings/system --bind name:s:touch_smooth --bind value:s:0",
            CommandRenderer.restore(entry("touch_smooth"), "0")
        )
    }

    @Test
    fun missingBaselineDeletesTheKey() {
        assertEquals(
            "content delete --uri content://settings/system --where \"name='touch_smooth'\"",
            CommandRenderer.restore(entry("touch_smooth"), null)
        )
    }

    @Test
    fun sessionOwnedKeysAreAlwaysDeleted() {
        val command = CommandRenderer.restore(entry("speed_mode_apps"), "something")
        assertEquals("content delete --uri content://settings/global --where \"name='speed_mode_apps'\"", command)
    }

    @Test
    fun unsafeBaselineIsNeverTurnedIntoADelete() {
        assertNull(CommandRenderer.restore(entry("touch_smooth"), "a b; reboot"))
    }

    @Test
    fun captureLineReadsTheKeyThroughSettingsGet() {
        val line = CommandRenderer.captureLine(entry("touch_smooth"))
        assertNotNull(line)
        assertEquals("echo \"FXB|system:touch_smooth|\$(settings get system touch_smooth 2>/dev/null)\"", line)
    }
}
