package com.framex.app.gaming.catalog

/**
 * The entries one session works with. [enabled] is what the switches and the capability probe
 * allow; [disabled] is what they do not, kept so the ledger can show those commands as skipped.
 */
data class CommandPlan(
    val enabled: List<CommandEntry>,
    val disabled: List<CommandEntry>
)

/**
 * Chooses which catalog entries are active. Activation and the rollback both start from the
 * same plan, so a switch that is off, or a command the device does not support, is off
 * everywhere.
 */
object CommandPlanner {

    fun plan(toggles: ToggleState, capabilities: CapableLookup): CommandPlan {
        val (enabled, disabled) = GamingCommandCatalog.entries.partition {
            toggles.isOn(it.toggle) && capabilities.isCapable(it.id)
        }
        return CommandPlan(enabled, disabled)
    }

    /** Entries whose baseline was captured, in catalog order. Used to undo exactly what ran. */
    fun capturedEntries(capturedIds: Set<String>): List<CommandEntry> =
        GamingCommandCatalog.entries.filter { it.id in capturedIds }
}
