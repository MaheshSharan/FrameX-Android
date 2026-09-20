package com.framex.app.gaming.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionLedgerTest {

    @Test
    fun upsert_updatesRowInPlaceByKeyAndStage_avoidsDuplicates() {
        val ledger = ExecutionLedger()

        val initialOp = AppliedOp(
            stage = Stage.HANDSHAKE,
            key = "sdk_game_target_fps",
            displayValue = "com.vivo.game_0_120",
            rawCommand = "content insert ... 0_120",
            status = OpStatus.APPLIED,
            priority = OpPriority.PRIMARY
        )
        ledger.upsert(initialOp)
        assertEquals(1, ledger.ops.value.size)
        assertEquals("com.vivo.game_0_120", ledger.ops.value[0].displayValue)

        // Game PID promoted later via maintenance pulse / handshake
        val updatedOp = AppliedOp(
            stage = Stage.HANDSHAKE,
            key = "sdk_game_target_fps",
            displayValue = "com.vivo.game_4812_120",
            rawCommand = "content insert ... 4812_120",
            status = OpStatus.APPLIED,
            priority = OpPriority.PRIMARY
        )
        ledger.upsert(updatedOp)

        // Must still be 1 row, updated in place!
        assertEquals(1, ledger.ops.value.size)
        assertEquals("com.vivo.game_4812_120", ledger.ops.value[0].displayValue)
    }

    @Test
    fun getSummary_sortsFailedAndPartialStagesFirst() {
        val ledger = ExecutionLedger()

        ledger.upsert(
            AppliedOp(
                stage = Stage.DISPLAY,
                key = "peak_refresh_rate",
                displayValue = "120",
                rawCommand = "settings put system peak_refresh_rate 120",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )

        ledger.upsert(
            AppliedOp(
                stage = Stage.POWER,
                key = "monster_mode",
                displayValue = "5",
                rawCommand = "content insert ...",
                status = OpStatus.FAILED,
                priority = OpPriority.PRIMARY
            )
        )

        val summary = ledger.getSummary()
        assertEquals(2, summary.stages.size)
        // Stage.POWER (FAILED) must be sorted before Stage.DISPLAY (ACTIVE)
        assertEquals(Stage.POWER, summary.stages[0].stage)
        assertEquals(StageStatus.FAILED, summary.stages[0].status)
        assertEquals(Stage.DISPLAY, summary.stages[1].stage)
        assertEquals(StageStatus.ACTIVE, summary.stages[1].status)
    }

    @Test
    fun clear_resetsAllOps() {
        val ledger = ExecutionLedger()
        ledger.upsert(
            AppliedOp(
                stage = Stage.MEMORY,
                key = "pm trim-caches",
                displayValue = "4G",
                rawCommand = "pm trim-caches 4G",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )
        assertEquals(1, ledger.ops.value.size)

        ledger.clear()
        assertEquals(0, ledger.ops.value.size)
        assertEquals(0, ledger.getSummary().totalOps)
    }

    @Test
    fun getSummary_computesPillStatesAndRanCountsCorrectly() {
        val ledger = ExecutionLedger()

        // 1. ACTIVE: all ran commands applied
        ledger.upsert(AppliedOp(Stage.DISPLAY, "peak_refresh_rate", "120", "", OpStatus.APPLIED, OpPriority.PRIMARY))
        ledger.upsert(AppliedOp(Stage.DISPLAY, "min_refresh_rate", "120", "", OpStatus.APPLIED, OpPriority.DETAIL))

        // 2. PARTIAL: some applied, some failed
        ledger.upsert(AppliedOp(Stage.POWER, "monster_mode", "5", "", OpStatus.APPLIED, OpPriority.PRIMARY))
        ledger.upsert(AppliedOp(Stage.POWER, "perf_mode", "1", "", OpStatus.FAILED, OpPriority.DETAIL))

        // 3. FAILED: all ran commands failed
        ledger.upsert(AppliedOp(Stage.KERNEL, "vip_thread", "1", "", OpStatus.FAILED, OpPriority.PRIMARY))

        // 4. SKIPPED: all skipped
        ledger.upsert(AppliedOp(Stage.GYRO, "gyro_opt", "1", "", OpStatus.SKIPPED, OpPriority.PRIMARY))

        val summary = ledger.getSummary()
        assertEquals(4, summary.stages.size)
        assertEquals(6, summary.totalOps)
        assertEquals(3, summary.totalApplied)
        assertEquals(2, summary.totalFailed)
        assertEquals(1, summary.totalSkipped)

        val power = summary.stages.first { it.stage == Stage.POWER }
        assertEquals(StageStatus.PARTIAL, power.status)
        assertEquals(2, power.totalCount)
        assertEquals(2, power.ranCount)
        assertEquals(1, power.appliedCount)
        assertEquals(1, power.failedCount)

        val display = summary.stages.first { it.stage == Stage.DISPLAY }
        assertEquals(StageStatus.ACTIVE, display.status)
        assertEquals(2, display.totalCount)
        assertEquals(2, display.ranCount)
        assertEquals(2, display.appliedCount)

        val kernel = summary.stages.first { it.stage == Stage.KERNEL }
        assertEquals(StageStatus.FAILED, kernel.status)
        assertEquals(1, kernel.totalCount)
        assertEquals(1, kernel.ranCount)
        assertEquals(0, kernel.appliedCount)
        assertEquals(1, kernel.failedCount)

        val gyro = summary.stages.first { it.stage == Stage.GYRO }
        assertEquals(StageStatus.SKIPPED, gyro.status)
        assertEquals(1, gyro.totalCount)
        assertEquals(0, gyro.ranCount)
        assertEquals(0, gyro.appliedCount)
        assertEquals(1, gyro.skippedCount)
    }

    @Test
    fun upsert_distinguishesKeysWithDifferentNamespacesInSameStage() {
        val ledger = ExecutionLedger()
        ledger.upsert(AppliedOp(Stage.POWER, "system:power_save_type", "0", "", OpStatus.APPLIED, OpPriority.PRIMARY))
        ledger.upsert(AppliedOp(Stage.POWER, "secure:power_save_type", "0", "", OpStatus.APPLIED, OpPriority.PRIMARY))

        // Different namespaces must remain distinct rows in the same stage
        assertEquals(2, ledger.ops.value.size)
        val keys = ledger.ops.value.map { it.key }
        assert(keys.contains("system:power_save_type"))
        assert(keys.contains("secure:power_save_type"))
    }

    @Test
    fun getSummary_suspensionSplit_computesPartialStageStatus() {
        val ledger = ExecutionLedger()
        ledger.upsert(AppliedOp(Stage.APPS, "suspended_apps", "33 apps", "", OpStatus.APPLIED, OpPriority.PRIMARY))
        ledger.upsert(AppliedOp(Stage.APPS, "suspension_failures", "1 failed", "", OpStatus.FAILED, OpPriority.DETAIL))

        val summary = ledger.getSummary()
        val apps = summary.stages.first { it.stage == Stage.APPS }
        assertEquals(StageStatus.PARTIAL, apps.status)
        assertEquals(2, apps.totalCount)
        assertEquals(1, apps.appliedCount)
        assertEquals(1, apps.failedCount)
    }
}
