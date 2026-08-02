package com.framex.app.ui.screens.thermal

import androidx.compose.ui.graphics.Color
import com.framex.app.metrics.MetricsEngine

enum class TimeWindow(val label: String, val seconds: Int) {
    SEC_60("60 Seconds", 60),
    MIN_5("5 Minutes", 300),
    MIN_15("15 Minutes", 900),
    MIN_30("30 Minutes", 1800),
    HOUR_1("1 Hour", 3600),
    FULL("Full Session", Int.MAX_VALUE)
}

enum class GraphMetricMode(val label: String) {
    FPS_THERMAL("FPS vs Thermal (CPU + Skin)"),
    FPS_JANK("FPS + Jank Frames"),
    FPS_ONLY("FPS Only"),
    THERMAL_ONLY("Thermal Only (CPU, GPU, Skin, Battery)"),
    FPS_TOP_PROCESS("FPS + Top Process CPU%")
}

data class DiagnosticCause(
    val title: String,
    val description: String,
    val color: Color
)

/** One plotted line on the graph: its display name, theme color, unit suffix, and value extractor. */
data class GraphSeries(
    val label: String,
    val color: Color,
    val unitSuffix: String,
    val maxScale: Float,
    val fillArea: Boolean,
    val valueOf: (MetricsEngine.MetricsSnapshot) -> Float
)
