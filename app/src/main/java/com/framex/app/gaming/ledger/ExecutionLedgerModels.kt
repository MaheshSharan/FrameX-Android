package com.framex.app.gaming.ledger

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable

enum class Stage(val label: String) {
    MEMORY("Memory & RAM"),
    APPS("Background Apps"),
    POWER("Power & Monster"),
    DISPLAY("Display & Thermal"),
    TOUCH("Touch & Sampling"),
    GYRO("Hardware Gyro"),
    KERNEL("Kernel & Sched"),
    DND("Do Not Disturb"),
    HANDSHAKE("Live Handshake")
}

enum class OpStatus {
    APPLIED,
    FAILED,
    SKIPPED
}

enum class OpPriority {
    PRIMARY,
    DETAIL
}

@Keep
@Immutable
data class AppliedOp(
    val stage: Stage,
    val key: String,
    val displayValue: String,
    val rawCommand: String,
    val status: OpStatus,
    val priority: OpPriority,
    val timestamp: Long = System.currentTimeMillis()
)

@Immutable
data class StageSummary(
    val stage: Stage,
    val primaryOps: List<AppliedOp>,
    val detailOps: List<AppliedOp>,
    val totalCount: Int,
    val ranCount: Int,
    val appliedCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val status: StageStatus
)

enum class StageStatus {
    ACTIVE,
    PARTIAL,
    FAILED,
    SKIPPED
}

@Immutable
data class LedgerSummary(
    val stages: List<StageSummary>,
    val totalApplied: Int,
    val totalFailed: Int,
    val totalSkipped: Int,
    val totalOps: Int
)
