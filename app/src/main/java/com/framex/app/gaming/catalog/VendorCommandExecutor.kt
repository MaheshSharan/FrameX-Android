package com.framex.app.gaming.catalog

import com.framex.app.gaming.ledger.CommandSpec
import com.framex.app.gaming.ledger.LedgerExecutor
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the vendor commands that the plan allows. Baselines are captured first, so a failure
 * to read them stops the session before anything on the device has changed.
 */
@Singleton
class VendorCommandExecutor @Inject constructor(
    private val ledgerExecutor: LedgerExecutor,
    private val restorer: VendorCommandRestorer,
    settings: SettingsRepository
) {
    private val toggles = SettingsToggleState(settings)

    /** Applies the activation entries. False when nothing could be written or a command failed. */
    suspend fun activate(platform: Platform, context: ResolveContext): Boolean {
        val plan = CommandPlanner.plan(platform, toggles)
        if (!restorer.capture(plan.enabled)) return false
        val skipped = plan.disabled.filter { it.phase == Phase.ACTIVATION }
        return write(plan.activation(), skipped, context)
    }

    /** Applies the entries that need the running game, now that its package and PID are known. */
    suspend fun promote(platform: Platform, context: ResolveContext): Boolean {
        val plan = CommandPlanner.plan(platform, toggles)
        val perGame = plan.activation().filter { it.need != Need.NONE }
        if (perGame.isEmpty() || !restorer.capture(perGame)) return perGame.isEmpty()
        return write(perGame, emptyList(), context)
    }

    /** Repeats the maintenance-pulse entries. True when every command passed. */
    suspend fun pulse(platform: Platform, context: ResolveContext): Boolean {
        val plan = CommandPlanner.plan(platform, toggles)
        if (!restorer.capture(plan.pulse())) return false
        return write(plan.pulse(), emptyList(), context)
    }

    private suspend fun write(
        entries: List<CommandEntry>,
        disabled: List<CommandEntry>,
        context: ResolveContext
    ): Boolean {
        val (ready, waiting) = entries.partition { context.satisfies(it.need) }
        var allPassed = true
        for ((stage, group) in ready.groupBy { it.stage }) {
            allPassed = ledgerExecutor.executeBatch(stage, specs(group, context)) && allPassed
        }
        for ((stage, group) in (waiting + disabled).groupBy { it.stage }) {
            ledgerExecutor.recordSkipped(stage, specs(group, context))
        }
        return allPassed
    }

    private fun specs(entries: List<CommandEntry>, context: ResolveContext): List<CommandSpec> =
        entries.mapNotNull { entry ->
            val command = CommandRenderer.apply(entry, context)
            if (command == null) FrameXLog.w("Skipped ${entry.id}: value is not safe to write", tag = TAG)
            command?.let { CommandSpec(it, entry.priority) }
        }

    private companion object {
        const val TAG = "VendorCommandExecutor"
    }
}
