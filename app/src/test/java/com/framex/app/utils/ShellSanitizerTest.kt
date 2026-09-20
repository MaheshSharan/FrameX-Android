package com.framex.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellSanitizerTest {

    @Test
    fun `valid package names pass sanitization and are trimmed`() {
        assertEquals("com.tencent.ig", ShellSanitizer.sanitizePackageName("com.tencent.ig"))
        assertEquals("com.dts.freefireth", ShellSanitizer.sanitizePackageName("  com.dts.freefireth  "))
        assertEquals("com.activision.callofduty.shooter_123", ShellSanitizer.sanitizePackageName("com.activision.callofduty.shooter_123"))
    }

    @Test
    fun `null and blank packages return null`() {
        assertNull(ShellSanitizer.sanitizePackageName(null))
        assertNull(ShellSanitizer.sanitizePackageName(""))
        assertNull(ShellSanitizer.sanitizePackageName("   "))
    }

    @Test
    fun `packages with shell injection separators return null`() {
        assertNull(ShellSanitizer.sanitizePackageName("com.game; rm -rf /"))
        assertNull(ShellSanitizer.sanitizePackageName("com.game && echo pwned"))
        assertNull(ShellSanitizer.sanitizePackageName("com.game | cat /etc/passwd"))
        assertNull(ShellSanitizer.sanitizePackageName("com.game\nreboot"))
        assertNull(ShellSanitizer.sanitizePackageName("com.game`reboot`"))
        assertNull(ShellSanitizer.sanitizePackageName("com.game$(reboot)"))
        assertNull(ShellSanitizer.sanitizePackageName("com.game\"test\""))
        assertNull(ShellSanitizer.sanitizePackageName("com.game'test'"))
        assertNull(ShellSanitizer.sanitizePackageName("com.game with spaces"))
        assertNull(ShellSanitizer.sanitizePackageName("com.game\u0000injected"))
    }

    @Test
    fun `excessively long package names return null`() {
        val longPkg = "a".repeat(300)
        assertNull(ShellSanitizer.sanitizePackageName(longPkg))
    }
}
