package com.framex.app.metrics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector

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
    NETWORK_SPEED("net");

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
    MetricModuleId.NETWORK_SPEED
)

val METRIC_MODULE_REGISTRY: Map<MetricModuleId, MetricModuleInfo> = listOf(
    MetricModuleInfo(MetricModuleId.FPS, "Frames Per Second", "FPS", Icons.Default.Speed, "120"),
    MetricModuleInfo(MetricModuleId.CPU_FREQUENCY, "CPU Frequency", "CPU", Icons.Default.Memory, "2.8 GHz"),
    MetricModuleInfo(
        MetricModuleId.CPU_CLUSTERS,
        "CPU Clusters",
        "CPU Clusters",
        Icons.Default.Memory,
        "U: 2.8G | P: 2.2G | E: 1.6G"
    ),
    MetricModuleInfo(MetricModuleId.RAM_USAGE, "RAM Usage", "RAM", Icons.Default.DeveloperBoard, "4.2 GB"),
    MetricModuleInfo(MetricModuleId.BATTERY_TEMPERATURE, "Battery Temp", "TEMP", Icons.Default.DeviceThermostat, "38°C"),
    MetricModuleInfo(
        MetricModuleId.THERMAL_MONITOR,
        "Thermal Monitor",
        "THERMAL",
        Icons.Default.LocalFireDepartment,
        "CPU 63°C · MODERATE"
    ),
    MetricModuleInfo(MetricModuleId.NETWORK_SPEED, "Network Speed", "NET", Icons.Default.NetworkCheck, "1.2 MB")
).associateBy { it.id }

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
fun metricValueFor(id: MetricModuleId, metricsState: MetricsState): String = when (id) {
    MetricModuleId.FPS -> "${metricsState.fps}"
    MetricModuleId.CPU_FREQUENCY -> "${metricsState.cpuMhz} MHz"
    MetricModuleId.CPU_CLUSTERS -> String.format(
        "U:%d P:%d E:%d",
        metricsState.cpuClusterUltraMhz,
        metricsState.cpuClusterPerfMhz,
        metricsState.cpuClusterEffMhz
    )
    MetricModuleId.RAM_USAGE -> String.format("%.1f GB", metricsState.ramUsedGb)
    MetricModuleId.BATTERY_TEMPERATURE -> String.format("%.1f°C", metricsState.batteryTempC)
    MetricModuleId.THERMAL_MONITOR -> String.format(
        "%.0f°C %s",
        metricsState.thermalCpuC,
        thermalStatusShortLabel(metricsState.thermalStatus)
    )
    MetricModuleId.NETWORK_SPEED -> {
        val totalKbps = metricsState.networkRxKbps + metricsState.networkTxKbps
        if (totalKbps > KBPS_PER_MBPS) {
            String.format("%.1f MB/s", totalKbps / KBPS_PER_MBPS)
        } else {
            String.format("%.0f KB/s", totalKbps)
        }
    }
}

/** Short label for the overlay's compact/minimal display — full names are used in
 *  the Performance/detail screens where there's room to show "MODERATE" in full. */
fun thermalStatusShortLabel(status: Int): String = when (status) {
    0, 1 -> "OK"
    2 -> "WARM"
    3 -> "HOT"
    4 -> "CRIT"
    5, 6 -> "!!!"
    else -> "?"
}

private const val KBPS_PER_MBPS = 1024f
