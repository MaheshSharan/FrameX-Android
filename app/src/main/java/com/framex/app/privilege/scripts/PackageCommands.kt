package com.framex.app.privilege.scripts

import com.framex.app.privilege.SuspendOutcome
import com.framex.app.utils.ShellSanitizer

/**
 * Command text and result handling for package suspension and app ops. Shared by the Shizuku
 * user service and the root backend so both issue byte-identical commands.
 */
object PackageCommands {

    private const val APP_OP_MARKER = "FXOP:"
    private const val SUSPEND_MARKER = "FXS|"
    private const val SUSPEND_PARALLELISM = 8
    private const val NOT_INSTALLED_PATTERN = "unknown target package|does not exist|not found"

    /** Keeps only names that pass the package-name validator; the rest never reach a shell. */
    fun validPackages(packages: Collection<String>): List<String> =
        packages.mapNotNull { ShellSanitizer.sanitizePackageName(it) }

    /**
     * One script that tries every package on its own, a few at a time. `cmd package suspend`
     * aborts the whole call at the first package it rejects (not installed, or protected on
     * this device), so a shared call would leave the rest untouched and force a slow retry
     * per package. Each line reports `FXS|pkg|exitCode|notInstalled` so nothing is guessed.
     */
    fun suspendScript(packages: List<String>, suspended: Boolean): String =
        packages.chunked(SUSPEND_PARALLELISM).joinToString("\n") { chunk ->
            chunk.joinToString("\n") { pkg ->
                "( out=$(cmd package ${verb(suspended)} --user 0 $pkg 2>&1); rc=\$?; " +
                    "if echo \"\$out\" | grep -qiE '$NOT_INSTALLED_PATTERN'; then gone=1; else gone=0; fi; " +
                    "echo \"$SUSPEND_MARKER$pkg|\$rc|\$gone\" ) &"
            } + "\nwait"
        }

    /**
     * Suspends or restores [packages] in one privileged call. A package that is not installed
     * is skipped; any other rejection is reported in the outcome. [run] returns the exit code
     * and output of a command; it is inline so callers may use suspending executors.
     */
    inline fun applySuspend(
        packages: List<String>,
        suspended: Boolean,
        run: (String) -> Pair<Int, String>
    ): SuspendOutcome {
        val valid = validPackages(packages)
        if (valid.isEmpty()) return SuspendOutcome(emptyList(), 0)
        val (_, output) = run(suspendScript(valid, suspended))
        return parseSuspendReport(valid, output)
    }

    /** Packages with no result line are reported failed, so a dropped call is never a success. */
    fun parseSuspendReport(packages: List<String>, output: String): SuspendOutcome {
        val results = output.lineSequence()
            .filter { it.startsWith(SUSPEND_MARKER) }
            .mapNotNull { line ->
                val parts = line.removePrefix(SUSPEND_MARKER).split('|')
                if (parts.size == 3) parts[0] to (parts[1] to parts[2]) else null
            }
            .toMap()
        val failed = mutableListOf<String>()
        var succeeded = 0
        for (pkg in packages) {
            val result = results[pkg]
            when {
                result == null -> failed.add(pkg)
                result.first == "0" -> succeeded++
                result.second != "1" -> failed.add(pkg)
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
