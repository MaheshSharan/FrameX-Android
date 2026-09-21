package com.framex.app.utils

/**
 * Defensive shell sanitization and validation utilities to defend against
 * command injection and IPC argument corruption (AGENTS.md Rule 2.2).
 */
object ShellSanitizer {

    private val SAFE_PACKAGE_REGEX = Regex("^[a-zA-Z0-9_.]+$")

    /**
     * Strictly validates and trims an Android package name before shell interpolation.
     * Returns trimmed package name if valid; returns null if null, blank, excessively long,
     * or contains illegal characters (`\0`, whitespace, `;`, `&`, `|`, quotes, backticks).
     */
    fun sanitizePackageName(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        val trimmed = packageName.trim()
        if (trimmed.length > 256) {
            FrameXLog.w("Package name exceeds max length (256): $trimmed", tag = "ShellSanitizer")
            return null
        }
        if (!SAFE_PACKAGE_REGEX.matches(trimmed)) {
            FrameXLog.w("Rejected package name with invalid shell characters: $trimmed", tag = "ShellSanitizer")
            return null
        }
        return trimmed
    }
}
