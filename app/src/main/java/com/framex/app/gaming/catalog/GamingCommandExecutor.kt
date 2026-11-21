package com.framex.app.gaming.catalog

import com.framex.app.gaming.GenericOverride
import com.framex.app.gaming.OemOverridePolicy
import com.framex.app.gaming.ledger.CommandSpec
import com.framex.app.gaming.ledger.LedgerExecutor
import com.framex.app.privilege.DeviceCapabilityProbe
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the catalog commands that the plan allows. Baselines are captured first, so a failure
 * to read them stops the session before anything on the device has changed.
 */
@Singleton
class GamingCommandExecutor @Inject constructor(
    private val ledgerExecutor: LedgerExecutor,
    private val restorer: GamingCommandRestorer,
    private val capabilities: DeviceCapabilityProbe,
    private val settings: SettingsRepository
) {
    private val toggles = SettingsToggleState(settings)

    /**
     * Applies the activation entries. Distinguishes an infrastructure failure (baselines could
     * not be read, so nothing was changed — the only case that should block the session) from one
     * or more individual commands failing to apply (every other enabled command still runs; a
     * single optimization failing must never take down the whole gaming-mode session).
     */
    suspend fun activate(context: ResolveContext): ActivationOutcome {
        val plan = plan()
        FrameXLog.i(
            "activate(): enabled=${plan.enabled.map { it.id }} disabled=${
                plan.disabled.joinToString { entry ->
                    val reason = if (!toggles.isOn(entry.toggle)) "toggle off" else "not capable"
                    "${entry.id}($reason)"
                }
            }",
            tag = TAG
        )
        // Only capture a baseline for an entry that will actually be written now (its [Need] is
        // satisfied by this context). An entry waiting on a game package/uid that isn't known yet
        // (e.g. CPU Priority Lock before any game is promoted) must not be captured here — it
        // would record a baseline for a command that never ran, and restoring it later would try
        // to revert FrameX's own placeholder package (which doesn't exist on the device) instead
        // of doing nothing, as it correctly would if [promote] captures and applies it for real
        // once a game is actually running.
        val capturable = plan.enabled.filter { context.satisfies(it.need) }
        if (capturable.size != plan.enabled.size) {
            FrameXLog.i(
                "activate(): ${plan.enabled.size - capturable.size} entries not captured yet " +
                    "(need not satisfied by current context): ${(plan.enabled - capturable.toSet()).map { it.id }}",
                tag = TAG
            )
        }
        if (!restorer.capture(capturable)) {
            FrameXLog.w("activate(): capture() failed, aborting before any write", tag = TAG)
            return ActivationOutcome(baselinesCaptured = false, allCommandsApplied = false, failedIds = emptyList())
        }
        val (allPassed, failedIds) = write(plan.enabled, plan.disabled, context)
        FrameXLog.i("activate(): write() allPassed=$allPassed failed=$failedIds", tag = TAG)
        return ActivationOutcome(baselinesCaptured = true, allCommandsApplied = allPassed, failedIds = failedIds)
    }

    /** Re-applies the entries that need the running game, now that its package/uid are known. */
    suspend fun promote(context: ResolveContext): Boolean {
        val plan = plan()
        val perGame = plan.enabled.filter { it.need != Need.NONE }
        if (perGame.isEmpty()) return true
        if (!restorer.capture(perGame)) return false
        return write(perGame, emptyList(), context).first
    }

    /** Fixed-performance-mode is excluded here when the device risk flag blocks it outright. */
    private fun plan(): CommandPlan {
        val raw = CommandPlanner.plan(toggles, capabilities)
        val (risky, safe) = raw.enabled.partition { it.toggle == Toggle.FIXED_PERFORMANCE_MODE }
        val blocked = risky.filterNot { OemOverridePolicy.isAllowed(GenericOverride.FIXED_PERFORMANCE_MODE, capabilities.isRisky(it.id)) }
        if (blocked.isEmpty()) return raw
        FrameXLog.w("Fixed performance mode withheld: device flagged risky", tag = TAG)
        return raw.copy(enabled = safe + (risky - blocked), disabled = raw.disabled + blocked)
    }

    /**
     * Every stage's batch runs independently (a failure in one, e.g. MEMORY, never prevents the
     * next, e.g. KERNEL, from running) — this just also reports which stages failed instead of
     * collapsing everything into one boolean the caller would have to treat as all-or-nothing.
     */
    private suspend fun write(
        entries: List<CommandEntry>,
        disabled: List<CommandEntry>,
        context: ResolveContext
    ): Pair<Boolean, List<String>> {
        val (ready, waiting) = entries.filter { it.appliesOnActivation }.partition { context.satisfies(it.need) }
        if (waiting.isNotEmpty()) {
            FrameXLog.i("write(): ${waiting.size} entries waiting on context: ${waiting.map { it.id }}", tag = TAG)
        }
        // The recovery flag must be on disk before the override that needs it is applied (Rule
        // 2.8): PrivilegeManager blocks a Shizuku<->Root switch while this is set, so the user
        // can't abandon a backend mid-override and leave thermal throttling disabled with no way
        // to revert it. This must track the thermal_override entry actually being in this
        // activation's ready set, not just "gaming mode is on" — only run when it will really run.
        if (ready.any { it.isThermalOverride }) {
            settings.setNeedsThermalOverrideActive(true)
        }
        var allPassed = true
        val failedIds = mutableListOf<String>()
        for ((stage, group) in ready.groupBy { it.stage }) {
            val stagePassed = ledgerExecutor.executeBatch(stage, specs(group, context))
            FrameXLog.i("write(): stage=$stage entries=${group.map { it.id }} passed=$stagePassed", tag = TAG)
            if (!stagePassed) failedIds += group.map { it.id }
            allPassed = stagePassed && allPassed
        }
        for ((stage, group) in (waiting + disabled).groupBy { it.stage }) {
            ledgerExecutor.recordSkipped(stage, specs(group, context))
        }
        return allPassed to failedIds
    }

    private fun specs(entries: List<CommandEntry>, context: ResolveContext): List<CommandSpec> =
        entries.mapNotNull { entry ->
            val command = CommandRenderer.apply(entry, context)
            if (command == null) FrameXLog.w("Skipped ${entry.id}: value is not safe to write", tag = TAG)
            command?.let { CommandSpec(it, entry.priority) }
        }

    private companion object {
        const val TAG = "GamingCommandExecutor"
    }
}

/**
 * [baselinesCaptured] false means an infrastructure failure (baselines could not be read) —
 * nothing was written and the session must not proceed. When true, the session proceeds
 * regardless of [allCommandsApplied]; a command failing to apply is reported, not fatal, so one
 * broken optimization never blocks every other one or the whole gaming-mode session.
 */
data class ActivationOutcome(
    val baselinesCaptured: Boolean,
    val allCommandsApplied: Boolean,
    val failedIds: List<String>
)
