package com.framex.app.gaming.catalog

import com.framex.app.privilege.PrivilegeManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures the values a session is about to change and puts them back. The captured ids are the
 * only record of what a session touched, so restoring works the same after a crash.
 */
@Singleton
class GamingCommandRestorer @Inject constructor(
    private val privilegeManager: PrivilegeManager,
    private val settings: SettingsRepository
) {
    private val store: BaselineStore = SettingsBaselineStore(settings)

    /**
     * Reads the current value of every entry that has no recorded baseline yet and stores them.
     * Returns false, with nothing written to the device, only on a genuine infrastructure
     * failure (the shell invocation itself did not come back successfully). An entry whose
     * capture line is simply missing from the output — because the key does not exist on this
     * device (#118) — is captured as absent (`null`), not treated as a failure of the batch.
     */
    suspend fun capture(entries: List<CommandEntry>): Boolean {
        FrameXLog.i("capture() called with ${entries.size} entries: ${entries.map { it.id }}", tag = TAG)
        val pending = entries.filter { it.id !in store.capturedIds() }
        if (pending.isEmpty()) {
            FrameXLog.i("capture(): nothing pending (all ${entries.size} already have a baseline)", tag = TAG)
            return true
        }
        FrameXLog.i("capture(): ${pending.size} pending: ${pending.map { it.id }}", tag = TAG)

        val resolveless = ResolveContext(null)
        val captureLines: List<Pair<CommandEntry, String>> = pending.mapNotNull { entry ->
            CommandRenderer.captureLine(entry, resolveless)?.let { entry to it }
        }
        val capturable = captureLines.map { it.first }.toSet()
        val noCaptureNeeded = pending.filterNot { it in capturable }

        val captured = mutableMapOf<String, String?>()
        // Nothing to read for these (e.g. a one-shot action); still mark them as "touched this
        // session" so the restorer knows to run their fixed revert, if any, on deactivation.
        noCaptureNeeded.forEach { captured[it.id] = null }
        FrameXLog.i(
            "capture(): ${capturable.size} capturable (${capturable.map { it.id }}), " +
                "${noCaptureNeeded.size} no-capture-needed (${noCaptureNeeded.map { it.id }})",
            tag = TAG
        )

        if (captureLines.isNotEmpty()) {
            val script = captureLines.joinToString("\n") { it.second }
            FrameXLog.i("capture(): running script:\n$script", tag = TAG)
            val outcome = privilegeManager.exec(script, CAPTURE_TIMEOUT_MS)
            FrameXLog.i(
                "capture(): outcome=${outcome::class.simpleName} exit=${outcome.exitCode} " +
                    "output=${outcome.output.take(2000)}",
                tag = TAG
            )
            if (!outcome.isSuccess) {
                FrameXLog.w(
                    "Baseline capture failed: outcome=${outcome::class.simpleName} exit=${outcome.exitCode} " +
                        "output=${outcome.output.take(500)}; nothing was changed",
                    tag = TAG
                )
                return false
            }
            val parsed = parseCapture(outcome.output)
            FrameXLog.i("capture(): parsed ${parsed.size} of ${capturable.size} capture lines: $parsed", tag = TAG)
            capturable.forEach { entry ->
                // Missing line => captured as absent, consistent with an existing-but-null value.
                captured[entry.id] = parsed[entry.id]
                if (entry.id !in parsed) {
                    FrameXLog.w("capture(): no FXB line for ${entry.id}, treating as absent", tag = TAG)
                }
            }
        }

        store.save(captured)
        FrameXLog.i("capture(): saved ${captured.size} baselines: $captured", tag = TAG)
        return true
    }

    /**
     * Restores every recorded entry. Clears the baseline for each one that was confirmed put
     * back, keeps only the genuinely failed ones for a future retry — a single entry that can
     * never be reverted (e.g. its baseline belongs to a package that no longer exists) must not
     * block every other entry from being cleared and must not block deactivation from completing,
     * the same "one failure doesn't stop the rest" rule activation follows.
     * [RestoreOutcome.ranAtAll] false means the batch itself could not run at all (an
     * infrastructure failure, e.g. the privileged channel dropped mid-call) — nothing is cleared
     * and the whole session should be treated as still pending, since there's no way to know what,
     * if anything, succeeded. Any other case clears what it safely can.
     */
    suspend fun restore(context: ResolveContext): RestoreOutcome {
        val entries = CommandPlanner.capturedEntries(store.capturedIds())
        FrameXLog.i("restore(): ${entries.size} captured entries: ${entries.map { it.id }}", tag = TAG)
        if (entries.isEmpty()) return RestoreOutcome(ranAtAll = true, allReverted = true, failedIds = emptyList())
        // Restore.NONE entries (e.g. one-shot RAM trim) have nothing to undo by design — they are
        // always clearable, never a failure.
        val (noop, undoable) = entries.partition { it.restore == Restore.NONE }
        val runnable = undoable.mapNotNull { entry ->
            CommandRenderer.restore(entry, store.baselineOf(entry.id), context)?.let { entry to it }
        }
        val unresolved = undoable - runnable.map { it.first }.toSet()
        if (unresolved.isNotEmpty()) {
            FrameXLog.w(
                "restore(): ${unresolved.size} entries had no safe restore command, kept for retry: " +
                    unresolved.map { it.id },
                tag = TAG
            )
        }
        val report = if (runnable.isEmpty()) {
            ParallelShell.Report(emptyList(), emptyMap())
        } else {
            val commands = runnable.map { it.second }
            FrameXLog.i("restore(): running ${commands.size} commands: $commands", tag = TAG)
            val outcome = privilegeManager.exec(ParallelShell.script(commands), RESTORE_TIMEOUT_MS)
            if (!outcome.isSuccess) {
                FrameXLog.w(
                    "restore(): batch could not run at all (infrastructure failure): " +
                        "outcome=${outcome::class.simpleName} exit=${outcome.exitCode} output=${outcome.output.take(500)}",
                    tag = TAG
                )
                return RestoreOutcome(ranAtAll = false, allReverted = false, failedIds = undoable.map { it.id })
            }
            FrameXLog.i("restore(): raw output=${outcome.output}", tag = TAG)
            ParallelShell.parse(outcome.output).also { logTimings(commands, it) }
        }
        val failedIds = (report.failedIndexes.mapNotNull { runnable.getOrNull(it)?.first?.id } + unresolved.map { it.id }).toSet()
        val clearable = (undoable.map { it.id }.toSet() - failedIds) + noop.map { it.id }
        if (clearable.isNotEmpty()) store.clear(clearable)
        // The recovery flag mirrors the thermal_override entry's own real state: only clear it
        // once that specific entry's revert is confirmed, not just because restore() ran.
        val thermalEntry = undoable.firstOrNull { it.isThermalOverride }
        if (thermalEntry != null && thermalEntry.id !in failedIds) {
            settings.markThermalOverrideRecoveryComplete()
        }
        FrameXLog.i(
            "restore(): cleared ${clearable.size} baselines, kept ${failedIds.size} for retry: $failedIds",
            tag = TAG
        )
        return RestoreOutcome(ranAtAll = true, allReverted = failedIds.isEmpty(), failedIds = failedIds.toList())
    }

    private fun parseCapture(output: String): Map<String, String?> =
        output.lineSequence()
            .filter { it.startsWith(CAPTURE_PREFIX) }
            .mapNotNull { line ->
                val body = line.removePrefix(CAPTURE_PREFIX)
                val split = body.indexOf('|')
                if (split <= 0) return@mapNotNull null
                val value = body.substring(split + 1).trim()
                body.substring(0, split) to value.takeUnless { it.isEmpty() || it == NULL_VALUE }
            }
            .toMap()

    private fun logTimings(commands: List<String>, report: ParallelShell.Report) {
        report.durationsMs.toSortedMap().forEach { (index, ms) ->
            FrameXLog.i("restore #$index ${ms}ms: ${commands.getOrNull(index)?.take(110)}", tag = TAG)
        }
        FrameXLog.i(
            "restore batch: ${commands.size} commands, slowest=${report.durationsMs.values.maxOrNull()}ms",
            tag = TAG
        )
    }

    private companion object {
        const val TAG = "GamingCommandRestorer"
        const val CAPTURE_PREFIX = "FXB|"
        const val NULL_VALUE = "null"
        const val CAPTURE_TIMEOUT_MS = 15_000L
        const val RESTORE_TIMEOUT_MS = 25_000L
    }
}

/**
 * [ranAtAll] false means the restore batch could not execute at all (infrastructure failure) —
 * the session must still be treated as pending. When true, [allReverted] false means one or more
 * individual entries could not be reverted and were kept for a future retry, but deactivation
 * should still proceed; a single stuck entry must never block every other one, or the session,
 * from completing.
 */
data class RestoreOutcome(
    val ranAtAll: Boolean,
    val allReverted: Boolean,
    val failedIds: List<String>
)
