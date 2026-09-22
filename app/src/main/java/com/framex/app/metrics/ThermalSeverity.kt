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

        /**
         * Resolves effective system thermal severity from Android framework status.
         * Sensor temperature advisory levels are maintained separately in [SensorTemperatureAdvisory].
         */
        fun resolveEffective(
            status: Int,
            cpuTempC: Float = 0f,
            skinTempC: Float = 0f,
            isThrottling: Boolean = false
        ): ThermalSeverity {
            if (status > 0) return fromStatus(status)
            if (isThrottling) return SEVERE
            val advisory = SensorTemperatureAdvisory.fromTemperatures(cpuTempC, skinTempC)
            return when (advisory) {
                SensorTemperatureAdvisory.EXTREME -> CRITICAL
                SensorTemperatureAdvisory.HIGH -> SEVERE
                SensorTemperatureAdvisory.ELEVATED -> MODERATE
                SensorTemperatureAdvisory.NOMINAL -> NONE
            }
        }
    }
}

/**
 * Isolated, configurable temperature thresholds for advisory alerts.
 * Kept separate from Android system thermal throttling status to avoid false conflation.
 */
object ThermalThresholds {
    const val DEFAULT_CPU_WARM_C = 65f
    const val DEFAULT_CPU_HOT_C = 75f
    const val DEFAULT_CPU_CRIT_C = 85f

    const val DEFAULT_SKIN_WARM_C = 40f
    const val DEFAULT_SKIN_HOT_C = 43f
    const val DEFAULT_SKIN_CRIT_C = 48f
}

/**
 * Explicit sensor-temperature advisory level.
 * Communicates raw temperature heat level, distinct from OS thermal throttling.
 */
enum class SensorTemperatureAdvisory(
    val shortLabel: String,
    val displayLabel: String,
    val color: Color
) {
    NOMINAL("OK", "Nominal", Color(0xFF10B981)),
    ELEVATED("WARM", "Elevated Temperature", Color(0xFFF59E0B)),
    HIGH("HOT", "High Temperature", Color(0xFFEF4444)),
    EXTREME("CRIT", "Critical Temperature", Color(0xFFDC2626));

    companion object {
        fun fromTemperatures(
            cpuTempC: Float,
            skinTempC: Float = 0f,
            warmCpu: Float = ThermalThresholds.DEFAULT_CPU_WARM_C,
            hotCpu: Float = ThermalThresholds.DEFAULT_CPU_HOT_C,
            critCpu: Float = ThermalThresholds.DEFAULT_CPU_CRIT_C
        ): SensorTemperatureAdvisory = when {
            cpuTempC >= critCpu || skinTempC >= ThermalThresholds.DEFAULT_SKIN_CRIT_C -> EXTREME
            cpuTempC >= hotCpu || skinTempC >= ThermalThresholds.DEFAULT_SKIN_HOT_C -> HIGH
            cpuTempC >= warmCpu || skinTempC >= ThermalThresholds.DEFAULT_SKIN_WARM_C -> ELEVATED
            else -> NOMINAL
        }
    }
}
