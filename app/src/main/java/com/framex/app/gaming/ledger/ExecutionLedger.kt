package com.framex.app.gaming.ledger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutionLedger @Inject constructor() {

    private val _ops = MutableStateFlow<List<AppliedOp>>(emptyList())
    val ops: StateFlow<List<AppliedOp>> = _ops.asStateFlow()

    fun upsert(op: AppliedOp) {
        _ops.update { currentList ->
            val index = currentList.indexOfFirst { it.stage == op.stage && it.key == op.key }
            if (index >= 0) {
                currentList.toMutableList().apply { set(index, op) }
            } else {
                currentList + op
            }
        }
    }

    fun upsertAll(newOps: List<AppliedOp>) {
        _ops.update { currentList ->
            val map = currentList.associateBy { it.stage to it.key }.toMutableMap()
            for (op in newOps) {
                map[op.stage to op.key] = op
            }
            map.values.toList()
        }
    }

    fun clear() {
        _ops.value = emptyList()
    }

    fun removeStages(stages: Set<Stage>) {
        _ops.update { currentList ->
            currentList.filter { it.stage !in stages }
        }
    }

    fun getSummary(): LedgerSummary {
        val currentOps = _ops.value
        if (currentOps.isEmpty()) {
            return LedgerSummary(emptyList(), 0, 0, 0, 0)
        }

        val grouped = currentOps.groupBy { it.stage }
        val stageSummaries = grouped.map { (stage, stageOps) ->
            val primaries = stageOps.filter { it.priority == OpPriority.PRIMARY }
            val details = stageOps.filter { it.priority == OpPriority.DETAIL }

            val applied = stageOps.count { it.status == OpStatus.APPLIED }
            val failed = stageOps.count { it.status == OpStatus.FAILED }
            val skipped = stageOps.count { it.status == OpStatus.SKIPPED }
            val ranCount = applied + failed

            val status = when {
                stageOps.all { it.status == OpStatus.SKIPPED } -> StageStatus.SKIPPED
                failed == 0 && applied > 0 -> StageStatus.ACTIVE
                applied > 0 && failed > 0 -> StageStatus.PARTIAL
                failed > 0 && applied == 0 -> StageStatus.FAILED
                else -> StageStatus.SKIPPED
            }

            StageSummary(
                stage = stage,
                primaryOps = primaries,
                detailOps = details,
                totalCount = stageOps.size,
                ranCount = ranCount,
                appliedCount = applied,
                failedCount = failed,
                skippedCount = skipped,
                status = status
            )
        }.sortedWith(
            compareBy(
                { when (it.status) {
                    StageStatus.FAILED -> 0
                    StageStatus.PARTIAL -> 1
                    StageStatus.ACTIVE, StageStatus.SKIPPED -> 2
                } },
                { it.stage.ordinal }
            )
        )

        val totalApplied = currentOps.count { it.status == OpStatus.APPLIED }
        val totalFailed = currentOps.count { it.status == OpStatus.FAILED }
        val totalSkipped = currentOps.count { it.status == OpStatus.SKIPPED }

        return LedgerSummary(
            stages = stageSummaries,
            totalApplied = totalApplied,
            totalFailed = totalFailed,
            totalSkipped = totalSkipped,
            totalOps = currentOps.size
        )
    }
}
