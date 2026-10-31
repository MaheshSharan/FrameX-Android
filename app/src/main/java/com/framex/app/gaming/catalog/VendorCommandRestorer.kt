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
class VendorCommandRestorer @Inject constructor(
    private val privilegeManager: PrivilegeManager,
    settings: SettingsRepository
) {
    private val store: BaselineStore = SettingsBaselineStore(settings)

    /**
     * Reads the current value of every entry that has no recorded baseline yet and stores them.
     * Returns false, with nothing written to the device, when the values cannot be read.
     */
    suspend fun capture(entries: List<CommandEntry>): Boolean {
        val pending = entries.filter { it.id !in store.capturedIds() }
        if (pending.isEmpty()) return true
        val script = pending.joinToString("\n") { CommandRenderer.captureLine(it) }
        val outcome = privilegeManager.exec(script, CAPTURE_TIMEOUT_MS)
        if (!outcome.isSuccess) {
            FrameXLog.w("Baseline capture failed (exit=${outcome.exitCode}); nothing was changed", tag = TAG)
            return false
        }
        val captured = parseCapture(outcome.output)
        if (captured.keys != pending.map { it.id }.toSet()) {
            FrameXLog.w("Baseline capture incomplete: ${captured.size} of ${pending.size}", tag = TAG)
            return false
        }
        store.save(captured)
        return true
    }

    /** Restores every recorded entry of [platform]. True only when each one was put back. */
    suspend fun restore(platform: Platform): Boolean {
        val entries = CommandPlanner.capturedEntries(platform, store.capturedIds())
        if (entries.isEmpty()) return true
        val commands = entries.map { CommandRenderer.restore(it, store.baselineOf(it.id)) }
        val runnable = commands.filterNotNull()
        val report = if (runnable.isEmpty()) {
            ParallelShell.Report(emptyList(), emptyMap())
        } else {
            val outcome = privilegeManager.exec(ParallelShell.script(runnable), RESTORE_TIMEOUT_MS)
            if (!outcome.isSuccess) {
                FrameXLog.w("Restore failed to run (exit=${outcome.exitCode})", tag = TAG)
                return false
            }
            ParallelShell.parse(outcome.output).also { logTimings(runnable, it) }
        }
        val unsafe = commands.count { it == null }
        if (report.failedIndexes.isNotEmpty() || unsafe > 0) {
            FrameXLog.w("Restore incomplete: ${report.failedIndexes.size} failed, $unsafe unsafe values", tag = TAG)
            return false
        }
        store.clear(entries.map { it.id }.toSet())
        return true
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
        const val TAG = "VendorCommandRestorer"
        const val CAPTURE_PREFIX = "FXB|"
        const val NULL_VALUE = "null"
        const val CAPTURE_TIMEOUT_MS = 15_000L
        const val RESTORE_TIMEOUT_MS = 25_000L
    }
}
