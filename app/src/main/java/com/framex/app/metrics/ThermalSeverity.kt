package com.framex.app.metrics

import androidx.compose.ui.graphics.Color

enum class ThermalSeverity(
    val statusLevel: Int,
    val label: String,
    val shortLabel: String,
    val description: String,
    val color: Color
) {
    NONE(
        statusLevel = 0,
        label = "NONE",
        shortLabel = "NOR",
        description = "NONE — no elevated thermal status reported",
        color = Color(0xFF10B981)
    ),
    LIGHT(
        statusLevel = 1,
        label = "LIGHT",
        shortLabel = "LGT",
        description = "LIGHT — slight temperature increase",
        color = Color(0xFF3B82F6)
    ),
    MODERATE(
        statusLevel = 2,
        label = "MODERATE",
        shortLabel = "MOD",
        description = "MODERATE — performance may be slightly throttled",
        color = Color(0xFFF59E0B)
    ),
    SEVERE(
        statusLevel = 3,
        label = "SEVERE",
        shortLabel = "SEV",
        description = "SEVERE — throttling is active to reduce heat",
        color = Color(0xFFEF4444)
    ),
    CRITICAL(
        statusLevel = 4,
        label = "CRITICAL",
        shortLabel = "CRT",
        description = "CRITICAL — severe throttling active",
        color = Color(0xFFDC2626)
    ),
    EMERGENCY(
        statusLevel = 5,
        label = "EMERGENCY",
        shortLabel = "EMG",
        description = "EMERGENCY — critical safety limits reached",
        color = Color(0xFFB91C1C)
    ),
    SHUTDOWN(
        statusLevel = 6,
        label = "SHUTDOWN",
        shortLabel = "SHT",
        description = "SHUTDOWN — device shutting down due to heat",
        color = Color(0xFF7F1D1D)
    );

    companion object {
        fun fromStatus(status: Int): ThermalSeverity =
            entries.firstOrNull { it.statusLevel == status } ?: NONE
    }
}
