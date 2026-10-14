package com.framex.app.metrics

import androidx.compose.runtime.Immutable

/**
 * Readiness of a diagnostic channel (FPS, CPU, thermal sensors, top process, etc.).
 * Lets the UI distinguish real measurements from restricted or unsupported states.
 */
enum class MetricReadStatus {
    /** First poll has not completed yet. */
    Loading,

    /** Parsed usable data on this cycle. */
    Ok,

    /** Shizuku binder missing or permission not granted. */
    NoShizuku,

    /** Kernel or SELinux policy blocked access to the underlying sysfs/procfs node. */
    PermissionDenied,

    /** Device firmware or hardware HAL does not expose this sensor or feature. */
    HardwareUnsupported,

    /** Command ran but returned blank output. */
    EmptyOutput,

    /** Non-blank dump that did not match any known entries. */
    ParseFailed,

    /** Channel is live but this sample/sensor has nothing to show. */
    NoData,

    /** Showing last successful sample after a short run of transient failures. */
    Stale
}

/**
 * High-level capability classification for UI cards, module toggles, and diagnostics.
 */
@Immutable
data class MetricCapability(
    val isAvailable: Boolean,
    val status: MetricReadStatus,
    val shortBadge: String,
    val userExplanation: String
)

fun MetricReadStatus.toCapability(requiresShizuku: Boolean = false): MetricCapability = when (this) {
    MetricReadStatus.Ok,
    MetricReadStatus.Stale -> MetricCapability(
        isAvailable = true,
        status = this,
        shortBadge = "Active",
        userExplanation = "Live hardware telemetry active."
    )
    MetricReadStatus.Loading -> MetricCapability(
        isAvailable = true,
        status = this,
        shortBadge = "Initializing",
        userExplanation = "Waiting for initial sensor sample…"
    )
    MetricReadStatus.NoShizuku -> MetricCapability(
        isAvailable = false,
        status = this,
        shortBadge = "Requires Shizuku",
        userExplanation = "Connect and authorize Shizuku to read privileged system telemetry."
    )
    MetricReadStatus.PermissionDenied -> MetricCapability(
        isAvailable = false,
        status = this,
        shortBadge = if (requiresShizuku) "Requires Shizuku" else "SELinux Restricted",
        userExplanation = if (requiresShizuku) {
            "Restricted by Android SELinux policy. Enable Shizuku for privileged access."
        } else {
            "Blocked by device kernel SELinux policy on non-root firmware."
        }
    )
    MetricReadStatus.HardwareUnsupported,
    MetricReadStatus.EmptyOutput,
    MetricReadStatus.ParseFailed,
    MetricReadStatus.NoData -> MetricCapability(
        isAvailable = false,
        status = this,
        shortBadge = "Unsupported on Device",
        userExplanation = "This sensor is not exposed by your device's vendor HAL or kernel."
    )
}
