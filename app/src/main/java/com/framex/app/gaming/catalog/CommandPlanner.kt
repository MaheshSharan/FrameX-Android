package com.framex.app.gaming.catalog

import com.framex.app.gaming.ledger.Stage

/** Reads the Execution Center switches. The pulse master switch is folded in by the reader. */
fun interface ToggleState {
    fun isOn(toggle: Toggle): Boolean
}

/**
 * The entries one session works with. [enabled] is what the switches allow; [disabled] is what
 * they do not, kept so the ledger can show those commands as skipped.
 */
data class CommandPlan(
    val platform: Platform,
    val enabled: List<CommandEntry>,
    val disabled: List<CommandEntry>
) {
    fun activation(): List<CommandEntry> = enabled.filter { it.phase == Phase.ACTIVATION }

    fun pulse(): List<CommandEntry> = enabled.filter { it.phase == Phase.PULSE }

    fun byStage(entries: List<CommandEntry>): Map<Stage, List<CommandEntry>> =
        entries.groupBy { it.stage }
}

/**
 * Chooses which catalog entries are active. Activation, the pulse and the rollback all start
 * from the same plan, so a switch that is off is off everywhere.
 */
object CommandPlanner {

    fun plan(platform: Platform, toggles: ToggleState): CommandPlan {
        val (enabled, disabled) = GamingCommandCatalog.forPlatform(platform)
            .partition { toggles.isOn(it.toggle) }
        return CommandPlan(platform, enabled, disabled)
    }

    /** Entries whose baseline was captured, in catalog order. Used to undo exactly what ran. */
    fun capturedEntries(platform: Platform, capturedIds: Set<String>): List<CommandEntry> =
        GamingCommandCatalog.forPlatform(platform).filter { it.id in capturedIds }
}
