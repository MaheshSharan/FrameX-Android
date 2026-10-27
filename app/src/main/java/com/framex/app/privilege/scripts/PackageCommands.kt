package com.framex.app.privilege.scripts

import com.framex.app.privilege.SuspendOutcome
import com.framex.app.utils.ShellSanitizer

/**
 * Command text and result handling for package suspension and app ops. Shared by the Shizuku
 * user service and the root backend so both issue byte-identical commands.
 */
object PackageCommands {

    private const val APP_OP_MARKER = "FXOP:"

    private val SKIPPABLE_FAILURES = listOf("unknown target package", "does not exist", "not found")

    /** Keeps only names that pass the package-name validator; the rest never reach a shell. */
    fun validPackages(packages: Collection<String>): List<String> =
        packages.mapNotNull { ShellSanitizer.sanitizePackageName(it) }

    fun suspendBatch(packages: List<String>, suspended: Boolean): String =
        "cmd package ${verb(suspended)} --user 0 ${packages.joinToString(" ")}"

    fun suspendOne(pkg: String, suspended: Boolean): String =
        "cmd package ${verb(suspended)} --user 0 $pkg"

    /** True when a per-package failure only means the package is not installed. */
    fun isUninstalledFailure(output: String): Boolean {
        val lower = output.lowercase()
        return SKIPPABLE_FAILURES.any { lower.contains(it) }
    }

    /**
     * Suspends or restores [packages] with one batched command and falls back to one command
     * per package only when the batch fails. [run] returns the exit code and output of a
     * command; it is inline so callers may use suspending executors.
     */
    inline fun applySuspend(
        packages: List<String>,
        suspended: Boolean,
        run: (String) -> Pair<Int, String>
    ): SuspendOutcome {
        val valid = validPackages(packages)
        if (valid.isEmpty()) return SuspendOutcome(emptyList(), 0)
        if (run(suspendBatch(valid, suspended)).first == 0) return SuspendOutcome(emptyList(), valid.size)

        val failed = mutableListOf<String>()
        var succeeded = 0
        for (pkg in valid) {
            val (exitCode, output) = run(suspendOne(pkg, suspended))
            when {
                exitCode == 0 -> succeeded++
                !isUninstalledFailure(output) -> failed.add(pkg)
            }
        }
        return SuspendOutcome(failed, succeeded)
    }

    /** One chained payload with a result marker per package, so failures stay attributable. */
    fun appOpBatch(packages: List<String>, opCode: Int, mode: Int): String {
        val modeName = when (mode) {
            1 -> "ignore"
            2 -> "deny"
            else -> "allow"
        }
        return validPackages(packages).joinToString("; ") { pkg ->
            "cmd appops set $pkg $opCode $modeName >/dev/null 2>&1; echo $APP_OP_MARKER\$?"
        }
    }

    fun countAppOpSuccesses(output: String): Int =
        output.lineSequence().count { it.trim() == "${APP_OP_MARKER}0" }

    private fun verb(suspended: Boolean) = if (suspended) "suspend" else "unsuspend"
}
