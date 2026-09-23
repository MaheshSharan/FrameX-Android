package com.framex.app.metrics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery3Bar
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/**
 * Every metric the overlay can display. [storageKey] is the identifier persisted to
 * SharedPreferences (both in the enabled-modules set and the module-order list) and
 * must never change once shipped, or existing users' saved configuration breaks.
 */
enum class MetricModuleId(val storageKey: String) {
    FPS("fps"),
    CPU_FREQUENCY("cpu"),
    CPU_CLUSTERS("cpu_cluster"),
    RAM_USAGE("ram"),
    BATTERY_TEMPERATURE("temp"),
    THERMAL_MONITOR("thermal"),
    BATTERY_LEVEL("battery_level"),
    CLOCK("clock"),
    SESSION_TIMER("session_timer"),
    NETWORK_SPEED("net"),
    PING("ping");

    companion object {
        fun fromStorageKey(storageKey: String): MetricModuleId? =
            entries.firstOrNull { it.storageKey == storageKey }
    }
}

/** Static identity of a metric module: how it looks and reads before any live data arrives. */
data class MetricModuleInfo(
    val id: MetricModuleId,
    val displayName: String,
    /** Compact caps label shown in the on-screen overlay itself, where space is tight. */
    val overlayShortLabel: String,
    val icon: ImageVector,
    val previewSampleValue: String
)

/**
 * Canonical fallback order. Used verbatim for first-run users, and to place any
 * module the app introduces in a future release that isn't yet in a returning
 * user's persisted order (see [resolveMetricModuleOrder]).
 */
val DEFAULT_METRIC_MODULE_ORDER: List<MetricModuleId> = listOf(
    MetricModuleId.FPS,
    MetricModuleId.CPU_FREQUENCY,
    MetricModuleId.CPU_CLUSTERS,
    MetricModuleId.RAM_USAGE,
    MetricModuleId.BATTERY_TEMPERATURE,
    MetricModuleId.THERMAL_MONITOR,
    MetricModuleId.BATTERY_LEVEL,
    MetricModuleId.CLOCK,
    MetricModuleId.SESSION_TIMER,
    MetricModuleId.NETWORK_SPEED,
    MetricModuleId.PING
)

val METRIC_MODULE_REGISTRY: Map<MetricModuleId, MetricModuleInfo> = listOf(
    MetricModuleInfo(MetricModuleId.FPS, "Frames Per Second", "FPS", Icons.Outlined.Speed, "120"),
    MetricModuleInfo(MetricModuleId.CPU_FREQUENCY, "CPU Frequency", "CPU", Icons.Outlined.Memory, "2.8 GHz"),
    MetricModuleInfo(
        MetricModuleId.CPU_CLUSTERS,
        "CPU Clusters",
        "CPU Clusters",
        Icons.Outlined.DeveloperBoard,
        "U: 2.8G | P: 2.2G | E: 1.6G"
    ),
    MetricModuleInfo(MetricModuleId.RAM_USAGE, "RAM Usage", "RAM", Icons.Outlined.Storage, "4.2 GB"),
    MetricModuleInfo(MetricModuleId.BATTERY_TEMPERATURE, "Battery Temp", "TEMP", Icons.Outlined.DeviceThermostat, "38°C"),
    MetricModuleInfo(
        MetricModuleId.THERMAL_MONITOR,
        "Thermal Monitor",
        "THERMAL",
        Icons.Outlined.LocalFireDepartment,
        "CPU 63°C · MODERATE"
    ),
    MetricModuleInfo(MetricModuleId.BATTERY_LEVEL, "Battery Level", "BAT", Icons.Outlined.BatteryStd, "85%"),
    MetricModuleInfo(MetricModuleId.CLOCK, "Clock (Time)", "TIME", Icons.Outlined.Schedule, "18:08"),
    MetricModuleInfo(MetricModuleId.SESSION_TIMER, "Session Timer", "SESSION", Icons.Outlined.Timer, "24:10"),
    MetricModuleInfo(MetricModuleId.NETWORK_SPEED, "Network Speed", "NET", Icons.Outlined.SwapVert, "1.2 MB"),
    MetricModuleInfo(MetricModuleId.PING, "Ping Latency", "PING", Icons.Outlined.Sensors, "35 ms")
).associateBy { it.id }

/**
 * Resolves whether an icon should be shown for [storageKey].
 * If [savedIcons] is empty (fresh install or uncustomized state), all icons are shown by default.
 */
fun isIconShown(savedIcons: Set<String>, storageKey: String): Boolean =
    savedIcons.isEmpty() || savedIcons.contains(storageKey)

/**
 * Returns dynamic battery level icon based on charge percentage.
 */
fun getBatteryIcon(batteryLevel: Int): ImageVector = when {
    batteryLevel in 0..20 -> Icons.Outlined.BatteryAlert
    batteryLevel in 21..60 -> Icons.Outlined.Battery3Bar
    batteryLevel > 60 -> Icons.Outlined.BatteryFull
    else -> Icons.Outlined.BatteryStd
}

/**
 * Resolves appropriate icon for a module, taking live metric state into account if available.
 */
fun resolveModuleIcon(id: MetricModuleId, metricsState: MetricsState? = null): ImageVector {
    if (id == MetricModuleId.BATTERY_LEVEL && metricsState != null) {
        return getBatteryIcon(metricsState.batteryLevel)
    }
    return METRIC_MODULE_REGISTRY.getValue(id).icon
}

/**
 * Formats elapsed session seconds into a human-readable duration string (e.g. "24:10" or "1:15:30").
 */
fun formatSessionDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val hrs = s / 3600
    val mins = (s % 3600) / 60
    val secs = s % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

/**
 * Reconciles a persisted, user-customized order against the current set of known
 * modules. Unknown keys (e.g. from a downgraded app version) are dropped. Any
 * canonical module missing from [persistedOrder] (e.g. newly added in an update)
 * is appended at its canonical position so it appears somewhere sane without
 * disturbing the user's existing customization.
 */
fun resolveMetricModuleOrder(persistedOrder: List<String>): List<MetricModuleId> {
    if (persistedOrder.isEmpty()) return DEFAULT_METRIC_MODULE_ORDER

    val known = persistedOrder.mapNotNull { MetricModuleId.fromStorageKey(it) }
    val missing = DEFAULT_METRIC_MODULE_ORDER.filterNot { it in known }
    return known + missing
}

/** Formats the live value of [id] from [metricsState] for display in the overlay. */
fun metricValueFor(
    id: MetricModuleId,
    metricsState: MetricsState,
    cpuHotWarningEnabled: Boolean = true
): String = when (id) {
    MetricModuleId.FPS -> "${metricsState.fps}"
    MetricModuleId.CPU_FREQUENCY -> "${metricsState.cpuMhz} MHz"
    MetricModuleId.CPU_CLUSTERS -> String.format(
        Locale.US,
        "U:%d P:%d E:%d",
        metricsState.cpuClusterUltraMhz,
        metricsState.cpuClusterPerfMhz,
        metricsState.cpuClusterEffMhz
    )
    MetricModuleId.RAM_USAGE -> String.format(Locale.US, "%.1f GB", metricsState.ramUsedGb)
    MetricModuleId.BATTERY_TEMPERATURE -> String.format(Locale.US, "%.1f°C", metricsState.batteryTempC)
    MetricModuleId.BATTERY_LEVEL -> if (metricsState.batteryLevel >= 0) "${metricsState.batteryLevel}%" else "--%"
    MetricModuleId.CLOCK -> metricsState.currentTime.ifEmpty { "--:--" }
    MetricModuleId.SESSION_TIMER -> formatSessionDuration(metricsState.sessionElapsedSec)
    MetricModuleId.THERMAL_MONITOR -> {
        if (!cpuHotWarningEnabled) {
            String.format(Locale.US, "%.0f°C", metricsState.thermalCpuC)
        } else {
            val label = thermalStatusShortLabel(
                status = metricsState.thermalStatus,
                cpuTempC = metricsState.thermalCpuC,
                skinTempC = metricsState.thermalSkinC,
                isThrottling = metricsState.isThrottling
            )
            if (label.isBlank() || label == "OK" || label == "NOR") {
                String.format(Locale.US, "%.0f°C", metricsState.thermalCpuC)
            } else {
                String.format(Locale.US, "%.0f°C %s", metricsState.thermalCpuC, label)
            }
        }
    }
    MetricModuleId.NETWORK_SPEED -> {
        val totalKbps = metricsState.networkRxKbps + metricsState.networkTxKbps
        if (totalKbps > KBPS_PER_MBPS) {
            String.format(Locale.US, "%.1f MB/s", totalKbps / KBPS_PER_MBPS)
        } else {
            String.format(Locale.US, "%.0f KB/s", totalKbps)
        }
    }
    MetricModuleId.PING -> when (metricsState.pingReadStatus) {
        MetricReadStatus.Ok -> "${metricsState.pingMs} ms"
        MetricReadStatus.Loading -> "-- ms"
        else -> "timeout"
    }
}

/** Short label for the overlay's compact/minimal display — full names are used in
 *  the Performance/detail screens where there's room to show "MODERATE" in full. */
fun thermalStatusShortLabel(
    status: Int,
    cpuTempC: Float = 0f,
    skinTempC: Float = 0f,
    isThrottling: Boolean = false
): String {
    // 1. Android system thermal status is authoritative when non-zero
    if (status > 0) {
        return when (status) {
            1 -> "LGT"
            2 -> "WARM"
            3 -> "HOT"
            4 -> "CRIT"
            5, 6 -> "!!!"
            else -> "THROT"
        }
    }
    // 2. Hardware CPU cooling throttling
    if (isThrottling) {
        return "THROT"
    }
    // 3. Sensor temperature advisory when system status is nominal (0)
    val advisory = SensorTemperatureAdvisory.fromTemperatures(cpuTempC, skinTempC)
    return advisory.shortLabel
}

private const val KBPS_PER_MBPS = 1024f

