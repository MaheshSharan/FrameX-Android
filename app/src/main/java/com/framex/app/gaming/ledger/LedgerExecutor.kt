package com.framex.app.gaming.ledger

import com.framex.app.gaming.LogStatus
import com.framex.app.gaming.SystemAuditLogRepository
import com.framex.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CommandSpec(
    val command: String,
    val priority: OpPriority
)

@Singleton
class LedgerExecutor @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val executionLedger: ExecutionLedger,
    private val auditLogRepository: SystemAuditLogRepository
) {

    /**
     * Executes a list of commands in ONE batched IPC call, detects individual command exit codes
     * and stderr error messages, upserts each command into ExecutionLedger, and optionally emits
     * to SystemAuditLog if enabled.
     */
    suspend fun executeBatch(
        stage: Stage,
        specs: List<CommandSpec>
    ): Boolean = withContext(Dispatchers.IO) {
        if (specs.isEmpty()) return@withContext true
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
            recordPermissionFailure(stage, specs)
            return@withContext false
        }

        val chained = buildChainedPayload(specs)
        val result = shizukuManager.executeCommandWithResult(chained)
        val parsedResults = parseBatchOutput(result?.output.orEmpty(), specs.size)

        processBatchResults(stage, specs, parsedResults)
    }

    private fun recordPermissionFailure(stage: Stage, specs: List<CommandSpec>) {
        val failedOps = specs.map { spec ->
            val parsed = CommandLabelParser.parse(spec.command)
            AppliedOp(
                stage = stage,
                key = parsed.key,
                displayValue = parsed.value,
                rawCommand = spec.command,
                status = OpStatus.FAILED,
                priority = spec.priority
            )
        }
        executionLedger.upsertAll(failedOps)
    }

    private fun buildChainedPayload(specs: List<CommandSpec>): String {
        return specs.mapIndexed { index, spec ->
            "${spec.command} ; echo $MARKER_PREFIX$index:$?"
        }.joinToString(" ; ")
    }

    private fun processBatchResults(
        stage: Stage,
        specs: List<CommandSpec>,
        parsedResults: List<SingleCommandResult>
    ): Boolean {
        var allPassed = true
        val appliedOps = mutableListOf<AppliedOp>()

        specs.forEachIndexed { index, spec ->
            val parsed = CommandLabelParser.parse(spec.command)
            val cmdResult = parsedResults.getOrNull(index)

            val isSuccess = cmdResult != null && cmdResult.exitCode == 0 && !cmdResult.hasErrorIndicator
            if (!isSuccess) allPassed = false

            val status = if (isSuccess) OpStatus.APPLIED else OpStatus.FAILED

            appliedOps.add(
                AppliedOp(
                    stage = stage,
                    key = parsed.key,
                    displayValue = parsed.value,
                    rawCommand = spec.command,
                    status = status,
                    priority = spec.priority
                )
            )

            auditLogRepository.addLog(
                action = "${stage.name}: ${parsed.key}",
                details = spec.command,
                status = if (isSuccess) LogStatus.SUCCESS else LogStatus.FAILED
            )
        }

        executionLedger.upsertAll(appliedOps)
        return allPassed
    }

    /**
     * Records skipped operations in place with their key/value so future promoteGamePid
     * or pulse invocations can upsert them to APPLIED.
     */
    fun recordSkipped(stage: Stage, specs: List<CommandSpec>) {
        val skippedOps = specs.map { spec ->
            val parsed = CommandLabelParser.parse(spec.command)
            auditLogRepository.addLog(
                action = "${stage.name}: ${parsed.key} (SKIPPED)",
                details = spec.command,
                status = LogStatus.INFO
            )
            AppliedOp(
                stage = stage,
                key = parsed.key,
                displayValue = parsed.value,
                rawCommand = spec.command,
                status = OpStatus.SKIPPED,
                priority = spec.priority
            )
        }
        executionLedger.upsertAll(skippedOps)
    }

    /**
     * Records manual (non-shell) operations like Background Apps suspend count or DND status.
     */
    fun recordManual(
        stage: Stage,
        key: String,
        displayValue: String,
        status: OpStatus,
        priority: OpPriority = OpPriority.PRIMARY,
        rawCommand: String = ""
    ) {
        executionLedger.upsert(
            AppliedOp(
                stage = stage,
                key = key,
                displayValue = displayValue,
                rawCommand = rawCommand,
                status = status,
                priority = priority
            )
        )
        auditLogRepository.addLog(
            action = "${stage.name}: $key",
            details = rawCommand.ifEmpty { "$key = $displayValue" },
            status = when (status) {
                OpStatus.APPLIED -> LogStatus.SUCCESS
                OpStatus.FAILED -> LogStatus.FAILED
                OpStatus.SKIPPED -> LogStatus.INFO
            }
        )
    }

    companion object {
        private const val TAG = "LedgerExecutor"
        private const val MARKER_PREFIX = "__FX#"
        private val MARKER_REGEX = Regex("""__FX#(\d+):(\d+)""")
        private val FATAL_ERROR_REGEX = Regex("""(?i)(Error while accessing provider|java\.lang\.\w*Exception|SecurityException|\[ERROR\])""")

        /**
         * Pure function for parsing `__FX#N:$?` output chunks.
         * Indexes by captured N and selects the last match for each N to defend against
         * fake markers printed in stdout, computing chunks from previous marker end.
         */
        fun parseBatchOutput(output: String, expectedCount: Int): List<SingleCommandResult> {
            val allMatches = MARKER_REGEX.findAll(output).toList()
            val lastMatchByIndex = mutableMapOf<Int, MatchResult>()
            for (match in allMatches) {
                val n = match.groupValues[1].toIntOrNull() ?: continue
                if (n in 0 until expectedCount) {
                    lastMatchByIndex[n] = match
                }
            }

            val results = mutableListOf<SingleCommandResult>()
            var lastEnd = 0

            for (i in 0 until expectedCount) {
                val match = lastMatchByIndex[i]
                if (match != null && match.range.first >= lastEnd) {
                    val chunk = output.substring(lastEnd, match.range.first)
                    lastEnd = match.range.last + 1

                    val exitCode = match.groupValues[2].toIntOrNull() ?: -1
                    val hasErrorIndicator = FATAL_ERROR_REGEX.containsMatchIn(chunk)

                    results.add(SingleCommandResult(exitCode, hasErrorIndicator, chunk.trim()))
                } else if (match != null) {
                    val exitCode = match.groupValues[2].toIntOrNull() ?: -1
                    results.add(SingleCommandResult(exitCode, hasErrorIndicator = exitCode != 0, outputChunk = ""))
                } else {
                    results.add(SingleCommandResult(exitCode = -1, hasErrorIndicator = true, outputChunk = "Missing marker"))
                }
            }

            return results
        }

        data class SingleCommandResult(
            val exitCode: Int,
            val hasErrorIndicator: Boolean,
            val outputChunk: String
        )
    }
}
