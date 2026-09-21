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

    @Test
    fun stageLabels_verifyRenamedPowerAndAddedNetwork() {
        assertEquals("Power & Performance", Stage.POWER.label)
        assertEquals("Network & Doze", Stage.NETWORK.label)
    }

    @Test
    fun getSummary_withNetworkStage_summarizesAndSortsCorrectly() {
        val ledger = ExecutionLedger()
        ledger.upsert(
            AppliedOp(
                stage = Stage.NETWORK,
                key = "netpolicy:add",
                displayValue = "restrict-background-whitelist 10234",
                rawCommand = "cmd netpolicy add restrict-background-whitelist 10234",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )
        ledger.upsert(
            AppliedOp(
                stage = Stage.NETWORK,
                key = "deviceidle:whitelist",
                displayValue = "+com.example.game",
                rawCommand = "cmd deviceidle whitelist +com.example.game",
                status = OpStatus.APPLIED,
                priority = OpPriority.DETAIL
            )
        )
        ledger.upsert(
            AppliedOp(
                stage = Stage.NETWORK,
                key = "deviceidle:force-idle",
                displayValue = "",
                rawCommand = "cmd deviceidle force-idle",
                status = OpStatus.APPLIED,
                priority = OpPriority.DETAIL
            )
        )

        val summary = ledger.getSummary()
        assertEquals(1, summary.stages.size)
        val netStage = summary.stages[0]
        assertEquals(Stage.NETWORK, netStage.stage)
        assertEquals(StageStatus.ACTIVE, netStage.status)
        assertEquals(3, netStage.totalCount)
        assertEquals(3, netStage.appliedCount)
        assertEquals(1, netStage.primaryOps.size)
        assertEquals(2, netStage.detailOps.size)
    }

    @Test
    fun getSummary_genericDeviceExpectedStageOrder() {
        val ledger = ExecutionLedger()
        val stages = listOf(
            Stage.MEMORY, Stage.APPS, Stage.POWER, Stage.NETWORK, Stage.DISPLAY, Stage.TOUCH, Stage.DND
        )
        stages.forEach { stage ->
            ledger.upsert(
                AppliedOp(
                    stage = stage,
                    key = "test_key",
                    displayValue = "active",
                    rawCommand = "",
                    status = OpStatus.APPLIED,
                    priority = OpPriority.PRIMARY
                )
            )
        }

        val summary = ledger.getSummary()
        val resultStages = summary.stages.map { it.stage }
        assertEquals(stages, resultStages)
    }

    @Test
    fun getSummary_vivoDeviceExpectedStageOrder() {
        val ledger = ExecutionLedger()
        val stages = listOf(
            Stage.MEMORY, Stage.APPS, Stage.POWER, Stage.DISPLAY, Stage.TOUCH, Stage.GYRO, Stage.KERNEL, Stage.HANDSHAKE, Stage.DND
        )
        stages.forEach { stage ->
            ledger.upsert(
                AppliedOp(
                    stage = stage,
                    key = "test_key",
                    displayValue = "active",
                    rawCommand = "",
                    status = OpStatus.APPLIED,
                    priority = OpPriority.PRIMARY
                )
            )
        }

        val summary = ledger.getSummary()
        val resultStages = summary.stages.map { it.stage }
        assertEquals(stages, resultStages)
    }

    @Test
    fun getSummary_genericDeviceWithSkippedNetwork_retainsInPlaceOrder() {
        val ledger = ExecutionLedger()
        val stages = listOf(
            Stage.MEMORY, Stage.APPS, Stage.POWER, Stage.NETWORK, Stage.DISPLAY, Stage.TOUCH, Stage.DND
        )
        stages.forEach { stage ->
            val status = if (stage == Stage.NETWORK) OpStatus.SKIPPED else OpStatus.APPLIED
            ledger.upsert(
                AppliedOp(
                    stage = stage,
                    key = "test_key",
                    displayValue = if (status == OpStatus.SKIPPED) "skipped" else "active",
                    rawCommand = "",
                    status = status,
                    priority = OpPriority.PRIMARY
                )
            )
        }

        val initialSummary = ledger.getSummary()
        assertEquals(stages, initialSummary.stages.map { it.stage })
        assertEquals(StageStatus.SKIPPED, initialSummary.stages.first { it.stage == Stage.NETWORK }.status)

        // Game launched: Network flips to APPLIED
        ledger.upsert(
            AppliedOp(
                stage = Stage.NETWORK,
                key = "test_key",
                displayValue = "10234",
                rawCommand = "cmd netpolicy add restrict-background-whitelist 10234",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )

        val updatedSummary = ledger.getSummary()
        assertEquals(stages, updatedSummary.stages.map { it.stage })
        assertEquals(StageStatus.ACTIVE, updatedSummary.stages.first { it.stage == Stage.NETWORK }.status)
    }

    @Test
    fun getSummary_vivoDeviceWithSkippedGyroAndHandshake_retainsInPlaceOrder() {
        val ledger = ExecutionLedger()
        val stages = listOf(
            Stage.MEMORY, Stage.APPS, Stage.POWER, Stage.DISPLAY, Stage.TOUCH, Stage.GYRO, Stage.KERNEL, Stage.HANDSHAKE, Stage.DND
        )
        stages.forEach { stage ->
            val status = if (stage == Stage.GYRO || stage == Stage.HANDSHAKE) OpStatus.SKIPPED else OpStatus.APPLIED
            ledger.upsert(
                AppliedOp(
                    stage = stage,
                    key = "test_key",
                    displayValue = if (status == OpStatus.SKIPPED) "skipped" else "active",
                    rawCommand = "",
                    status = status,
                    priority = OpPriority.PRIMARY
                )
            )
        }

        val initialSummary = ledger.getSummary()
        assertEquals(stages, initialSummary.stages.map { it.stage })
        assertEquals(StageStatus.SKIPPED, initialSummary.stages.first { it.stage == Stage.GYRO }.status)
        assertEquals(StageStatus.SKIPPED, initialSummary.stages.first { it.stage == Stage.HANDSHAKE }.status)

        // Game launched: Gyro & Handshake flip to APPLIED
        ledger.upsert(
            AppliedOp(
                stage = Stage.GYRO,
                key = "test_key",
                displayValue = "1@com.example.game",
                rawCommand = "",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )
        ledger.upsert(
            AppliedOp(
                stage = Stage.HANDSHAKE,
                key = "test_key",
                displayValue = "com.example.game_1234_120",
                rawCommand = "",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY
            )
        )

        val updatedSummary = ledger.getSummary()
        assertEquals(stages, updatedSummary.stages.map { it.stage })
        assertEquals(StageStatus.ACTIVE, updatedSummary.stages.first { it.stage == Stage.GYRO }.status)
        assertEquals(StageStatus.ACTIVE, updatedSummary.stages.first { it.stage == Stage.HANDSHAKE }.status)
    }

    @Test
    fun skippedAndRealCommands_shareParsedKeysAndFlipInPlace() {
        val ledger = ExecutionLedger()

        // 1. Skipped specs with <no active game> placeholder
        val skippedActivityCmd = "cmd activity set-bg-restriction-level --user 0 <no active game> unrestricted"
        val skippedNetpolicyCmd = "cmd netpolicy add restrict-background-whitelist <no active game>"
        val skippedDeviceidleCmd = "cmd deviceidle whitelist +<no active game>"

        // 2. Real specs when game launches
        val realActivityCmd = "cmd activity set-bg-restriction-level --user 0 com.example.game unrestricted"
        val realNetpolicyCmd = "cmd netpolicy add restrict-background-whitelist 10234"
        val realDeviceidleCmd = "cmd deviceidle whitelist +com.example.game"

        val parsedSkippedActivity = CommandLabelParser.parse(skippedActivityCmd)
        val parsedRealActivity = CommandLabelParser.parse(realActivityCmd)
        assertEquals("activity:set-bg-restriction-level", parsedSkippedActivity.key)
        assertEquals(parsedSkippedActivity.key, parsedRealActivity.key)

        val parsedSkippedNet = CommandLabelParser.parse(skippedNetpolicyCmd)
        val parsedRealNet = CommandLabelParser.parse(realNetpolicyCmd)
        assertEquals("netpolicy:add", parsedSkippedNet.key)
        assertEquals(parsedSkippedNet.key, parsedRealNet.key)

        val parsedSkippedIdle = CommandLabelParser.parse(skippedDeviceidleCmd)
        val parsedRealIdle = CommandLabelParser.parse(realDeviceidleCmd)
        assertEquals("deviceidle:whitelist", parsedSkippedIdle.key)
        assertEquals(parsedSkippedIdle.key, parsedRealIdle.key)

        // 3. Upsert skipped ops
        ledger.upsert(
            AppliedOp(Stage.POWER, parsedSkippedActivity.key, parsedSkippedActivity.value, skippedActivityCmd, OpStatus.SKIPPED, OpPriority.PRIMARY)
        )
        ledger.upsert(
            AppliedOp(Stage.NETWORK, parsedSkippedNet.key, parsedSkippedNet.value, skippedNetpolicyCmd, OpStatus.SKIPPED, OpPriority.PRIMARY)
        )
        ledger.upsert(
            AppliedOp(Stage.NETWORK, parsedSkippedIdle.key, parsedSkippedIdle.value, skippedDeviceidleCmd, OpStatus.SKIPPED, OpPriority.DETAIL)
        )

        assertEquals(3, ledger.ops.value.size)
        val initialSummary = ledger.getSummary()
        assertEquals(StageStatus.SKIPPED, initialSummary.stages.first { it.stage == Stage.POWER }.status)
        assertEquals(StageStatus.SKIPPED, initialSummary.stages.first { it.stage == Stage.NETWORK }.status)

        // 4. Game launches: real commands executed and upserted
        ledger.upsert(
            AppliedOp(Stage.POWER, parsedRealActivity.key, parsedRealActivity.value, realActivityCmd, OpStatus.APPLIED, OpPriority.PRIMARY)
        )
        ledger.upsert(
            AppliedOp(Stage.NETWORK, parsedRealNet.key, parsedRealNet.value, realNetpolicyCmd, OpStatus.APPLIED, OpPriority.PRIMARY)
        )
        ledger.upsert(
            AppliedOp(Stage.NETWORK, parsedRealIdle.key, parsedRealIdle.value, realDeviceidleCmd, OpStatus.APPLIED, OpPriority.DETAIL)
        )

        // Must still be 3 ops (in-place replacement, NO duplicates)
        assertEquals(3, ledger.ops.value.size)
        val flippedSummary = ledger.getSummary()
        val powerStage = flippedSummary.stages.first { it.stage == Stage.POWER }
        val netStage = flippedSummary.stages.first { it.stage == Stage.NETWORK }
        assertEquals(StageStatus.ACTIVE, powerStage.status)
        assertEquals(StageStatus.ACTIVE, netStage.status)
        assertEquals(1, powerStage.appliedCount)
        assertEquals(2, netStage.appliedCount)
    }

    @Test
    fun removeStages_removesOnlyTargetStagesAndKeepsOthers() {
        val ledger = ExecutionLedger()
        ledger.upsert(AppliedOp(Stage.MEMORY, "trim-caches", "4G", "", OpStatus.APPLIED, OpPriority.PRIMARY))
        ledger.upsert(AppliedOp(Stage.APPS, "suspend", "25", "", OpStatus.APPLIED, OpPriority.PRIMARY))
        ledger.upsert(AppliedOp(Stage.POWER, "monster_mode", "5", "", OpStatus.APPLIED, OpPriority.PRIMARY))
        ledger.upsert(AppliedOp(Stage.DISPLAY, "peak_refresh_rate", "120", "", OpStatus.APPLIED, OpPriority.PRIMARY))
        ledger.upsert(AppliedOp(Stage.DND, "interruption_filter", "priority", "", OpStatus.APPLIED, OpPriority.PRIMARY))

        assertEquals(5, ledger.ops.value.size)

        // Remove Vivo platform stages: POWER and DISPLAY
        ledger.removeStages(setOf(Stage.POWER, Stage.DISPLAY))

        assertEquals(3, ledger.ops.value.size)
        val remainingStages = ledger.ops.value.map { it.stage }.toSet()
        assertEquals(setOf(Stage.MEMORY, Stage.APPS, Stage.DND), remainingStages)
    }
}
