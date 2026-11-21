package com.framex.app.gaming.catalog

import com.framex.app.gaming.ledger.OpPriority
import com.framex.app.gaming.ledger.Stage

/** The three Android settings tables a [CommandTarget.SettingsKey] entry may write. */
enum class SettingsTable(val argument: String) {
    SYSTEM("system"),
    SECURE("secure"),
    GLOBAL("global")
}

/** The Execution Center switches that decide whether an entry takes part in a session. */
enum class Toggle {
    /** Always on; used only by entries that are not gated by a switch (see [ResolveContext]). */
    ALWAYS,
    RAM_CACHE_PRE_TRIM,
    THERMAL_OVERRIDE,
    CPU_PRIORITY_LOCK,
    NETWORK_FIREWALL,
    PHANTOM_PROC_KILLER,
    FIXED_PERFORMANCE_MODE,
    REFRESH_RATE_LOCK,
    TOUCH_BOOST
}

/** What an entry needs to know about the running game before it can be written. */
enum class Need {
    NONE,
    PACKAGE,
    PACKAGE_AND_UID
}

/** How the value that existed before the session is put back. */
enum class Restore {
    /** Write the captured value back, or delete/revert when it did not exist. */
    BASELINE,

    /** The key belongs to FrameX for the session; always remove/revert it, ignoring the baseline. */
    DELETE,

    /** One-shot action with nothing to undo (e.g. `pm trim-caches`); restore emits nothing. */
    NONE
}

/** Values that are only known while a game runs or from the display profile. */
data class ResolveContext(
    val targetPackage: String?,
    val uid: Int? = null,
    val refreshRateHz: Int = 60
) {
    /** Package used when only the shape of a command is needed, for example in the ledger. */
    val packageOrDefault: String get() = targetPackage ?: DEFAULT_PACKAGE

    fun satisfies(need: Need): Boolean = when (need) {
        Need.NONE -> true
        Need.PACKAGE -> targetPackage != null
        Need.PACKAGE_AND_UID -> targetPackage != null && uid != null
    }

    private companion object {
        const val DEFAULT_PACKAGE = "com.framex.sample.game"
    }
}

/**
 * What a [CommandEntry] writes: either a single settings-table key (capturable and restorable
 * via `settings`/`content`), or a `cmd`/`pm`/`am`/`device_config` invocation with its own
 * capture/restore shape, since such a command has no settings row to read back generically.
 */
sealed class CommandTarget {

    /** A single settings-table key; capturable and restorable via settings get/put/delete. */
    data class SettingsKey(val table: SettingsTable, val key: String) : CommandTarget()

    /**
     * A `cmd`/`pm`/`am`/`device_config` invocation. [capture]/[restoreFromCapture] are null when
     * the command has no meaningful prior value to read back (e.g. `pm trim-caches` is a one-shot
     * action, not a toggle with a "before" state) — such entries use [fixedRevert] instead, a
     * deterministic undo command that does not depend on a captured baseline.
     */
    data class ServiceCommand(
        /** Stable identity for this command, since there is no table/key pair to derive one from. */
        val stableId: String,
        val activate: (ResolveContext) -> String,
        val capture: ((ResolveContext) -> String)? = null,
        val restoreFromCapture: ((ResolveContext, String?) -> String)? = null,
        val fixedRevert: ((ResolveContext) -> String)? = null
    ) : CommandTarget()
}

/**
 * One command and how it is undone. The catalog is the only place a command is described;
 * activation, the capability probe, rollback and the Execution Center all read it.
 */
data class CommandEntry(
    val target: CommandTarget,
    val stage: Stage,
    val toggle: Toggle,
    val priority: OpPriority,
    val need: Need = Need.NONE,
    val restore: Restore = Restore.BASELINE,
    /** Used only when [target] is [CommandTarget.SettingsKey]; ignored for [CommandTarget.ServiceCommand]. */
    val value: (ResolveContext) -> String = { "" },
    /**
     * False for an entry that is only ever captured and restored, never written on activation
     * (e.g. `user_preferred_display_mode_id`, which FrameX never sets but always puts back).
     */
    val appliesOnActivation: Boolean = true
) {
    /** Stable identity, also the key of the persisted baseline and the capability-probe cache. */
    val id: String get() = when (val t = target) {
        is CommandTarget.SettingsKey -> "settings:${t.table.argument}:${t.key}"
        is CommandTarget.ServiceCommand -> "service:${t.stableId}"
    }

    /**
     * Identifies the thermal-override entry specifically, so the engine can tie
     * [com.framex.app.repository.SettingsRepository]'s thermal recovery flag — which blocks a
     * privilege-mode switch while thermal throttling is artificially disabled — to this one
     * command's real activation/revert, instead of to "gaming mode is on" in general.
     */
    val isThermalOverride: Boolean get() =
        (target as? CommandTarget.ServiceCommand)?.stableId == THERMAL_OVERRIDE_STABLE_ID

    companion object {
        const val THERMAL_OVERRIDE_STABLE_ID = "thermal_override"
    }
}
