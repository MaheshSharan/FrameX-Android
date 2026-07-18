package com.framex.app.metrics

/**
 * Readiness of a diagnostic channel (thermal sensors, top process, etc.).
 * Lets the UI distinguish "real zero / real value" from "we could not read".
 */
enum class MetricReadStatus {
    /** First poll has not completed yet. */
    Loading,

    /** Parsed usable data on this cycle. */
    Ok,

    /** Shizuku binder missing or permission not granted. */
    NoShizuku,

    /** Command ran but returned blank output. */
    EmptyOutput,

    /** Non-blank dump that did not match any known entries. */
    ParseFailed,

    /** Channel is live but this sample/sensor has nothing to show. */
    NoData,

    /** Showing last successful sample after a short run of failures. */
    Stale
}
