package com.framex.app.metrics

import androidx.compose.ui.graphics.Color

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
