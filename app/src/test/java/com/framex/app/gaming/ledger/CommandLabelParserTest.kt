package com.framex.app.gaming.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandLabelParserTest {

    @Test
    fun parse_contentInsert_extractsKeyAndValue() {
        val cmd = "content insert --uri content://settings/system --bind name:s:sdk_game_target_fps --bind value:i:120"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("system:sdk_game_target_fps", parsed.key)
        assertEquals("120", parsed.value)
    }

    @Test
    fun parse_settingsPut_extractsKeyAndValue() {
        val cmd = "settings put system peak_refresh_rate 120"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("system:peak_refresh_rate", parsed.key)
        assertEquals("120", parsed.value)
    }

    @Test
    fun parse_settingsPut_namespaceDifferentiatesKeys() {
        val systemCmd = "settings put system power_save_type 0"
        val secureCmd = "settings put secure power_save_type 0"
        val parsedSystem = CommandLabelParser.parse(systemCmd)
        val parsedSecure = CommandLabelParser.parse(secureCmd)
        assertEquals("system:power_save_type", parsedSystem.key)
        assertEquals("secure:power_save_type", parsedSecure.key)
    }

    @Test
    fun parse_settingsPut_withQuotes_stripsQuotes() {
        val cmd = "settings put system \"touch_sensitivity\" \"high\""
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("system:touch_sensitivity", parsed.key)
        assertEquals("high", parsed.value)
    }

    @Test
    fun parse_deviceConfigPut_extractsKeyAndValue() {
        val cmd = "cmd device_config put game_overlay com.example.game mode=2,fps=120"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("game_overlay:com.example.game", parsed.key)
        assertEquals("mode=2,fps=120", parsed.value)
    }

    @Test
    fun parse_pmTrimCaches_extractsVerbAndValue() {
        val cmd = "pm trim-caches 4G"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("trim-caches", parsed.key)
        assertEquals("4G", parsed.value)
    }

    @Test
    fun parse_amKillAll_extractsVerbWithEmptyValue() {
        val cmd = "am kill-all"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("kill-all", parsed.key)
        assertEquals("", parsed.value)
    }

    @Test
    fun parse_cmdThermalservice_extractsVerbAndArgs() {
        val cmd = "cmd thermalservice override-status 0"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("thermalservice:override-status", parsed.key)
        assertEquals("0", parsed.value)
    }

    @Test
    fun formatLabel_formatsKeyValueProperly() {
        assertEquals("sdk_game_target_fps = 120", CommandLabelParser.formatLabel("sdk_game_target_fps", "120"))
        assertEquals("kill-all", CommandLabelParser.formatLabel("kill-all", ""))
        assertEquals("kill-all", CommandLabelParser.formatLabel("kill-all", "active"))
    }

    @Test
    fun formatMiddleEllipsis_truncatesLongString() {
        val longText = "com.framex.very.long.package.name.that.exceeds.the.limit"
        val truncated = CommandLabelParser.formatMiddleEllipsis(longText, 25)
        assertEquals(25, truncated.length)
        assert(truncated.contains("..."))
    }

    @Test
    fun parse_cmdNetpolicy_extractsKeyAndValue() {
        val cmd = "cmd netpolicy add restrict-background-whitelist 10234"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("netpolicy:add", parsed.key)
        assertEquals("restrict-background-whitelist 10234", parsed.value)
    }

    @Test
    fun parse_cmdDeviceidleWhitelist_extractsKeyAndValue() {
        val cmd = "cmd deviceidle whitelist +com.example.game"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("deviceidle:whitelist", parsed.key)
        assertEquals("+com.example.game", parsed.value)
    }

    @Test
    fun parse_cmdDeviceidleForceIdle_extractsKeyAndValue() {
        val cmd = "cmd deviceidle force-idle"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("deviceidle:force-idle", parsed.key)
        assertEquals("", parsed.value)
    }

    @Test
    fun parse_cmdActivitySetBgRestrictionLevel_extractsKeyAndValue() {
        val cmd = "cmd activity set-bg-restriction-level --user 0 com.example.game unrestricted"
        val parsed = CommandLabelParser.parse(cmd)
        assertEquals("activity:set-bg-restriction-level", parsed.key)
        assertEquals("--user 0 com.example.game unrestricted", parsed.value)
    }
}
