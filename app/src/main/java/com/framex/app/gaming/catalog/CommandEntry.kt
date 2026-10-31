package com.framex.app.gaming.catalog

import com.framex.app.gaming.ledger.OpPriority
import com.framex.app.gaming.ledger.Stage

/** The three Android settings tables that the vendor suites write to. */
enum class SettingsTable(val argument: String) {
    SYSTEM("system"),
    SECURE("secure"),
    GLOBAL("global")
}

/** Vendor suites that share one catalog. Generic optimizations are not settings writes. */
enum class Platform { VIVO, IQOO }

/** The Execution Center switches that decide whether an entry takes part in a session. */
enum class Toggle {
    ALWAYS,
    MONSTER_MODE,
    THERMAL_UNLOCK,
    GAME_HANDSHAKE,
    GYRO_PROMOTION,
    TOUCH_OPTIMIZATION,
    FPS_144_UNLOCK,
    VIP_THREAD,
    PULSE_GAME_PLUS,
    PULSE_STANDARD_PROMOTION,
    PULSE_SCENE_MORE_FPS
}

/** What an entry needs to know about the running game before it can be written. */
enum class Need { NONE, PACKAGE, PACKAGE_AND_PID }

/** Entries written once at activation, or repeated by the maintenance pulse. */
enum class Phase { ACTIVATION, PULSE }

/** How the value that existed before the session is put back. */
enum class Restore {
    /** Write the captured value back, or delete the key when it did not exist. */
    BASELINE,

    /** The key belongs to FrameX for the session (per-game registers); remove it. */
    DELETE
}

/** Values that are only known while a game runs or from the display profile. */
data class ResolveContext(
    val targetPackage: String?,
    val pid: Int,
    val refreshRateHz: Int
) {
    /** Package used when only the shape of a command is needed, for example in the ledger. */
    val packageOrDefault: String get() = targetPackage ?: DEFAULT_PACKAGE

    fun satisfies(need: Need): Boolean = when (need) {
        Need.NONE -> true
        Need.PACKAGE -> targetPackage != null
        Need.PACKAGE_AND_PID -> targetPackage != null && pid > 0
    }

    private companion object {
        const val DEFAULT_PACKAGE = "com.vivo.game"
    }
}

/**
 * One settings write and how it is undone. The catalog is the only place a vendor command is
 * described; activation, the maintenance pulse, rollback and the Execution Center all read it.
 */
data class CommandEntry(
    val table: SettingsTable,
    val key: String,
    val stage: Stage,
    val toggle: Toggle,
    val platforms: Set<Platform>,
    val priority: OpPriority,
    val phase: Phase = Phase.ACTIVATION,
    val need: Need = Need.NONE,
    val restore: Restore = Restore.BASELINE,
    val value: (ResolveContext) -> String
) {
    /** Stable identity, also the key of the persisted baseline. */
    val id: String get() = "${table.argument}:$key"
}
