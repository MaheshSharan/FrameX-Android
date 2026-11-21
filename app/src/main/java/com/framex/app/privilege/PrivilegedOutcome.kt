package com.framex.app.privilege

/**
 * Result of one privileged shell operation. The cases mirror the failure model required for
 * every shell and IPC call: a caller can always tell a command that ran and failed apart from
 * one that never ran.
 */
sealed interface PrivilegedOutcome {
    val output: String
    val exitCode: Int
    val isSuccess: Boolean get() = this is Success

    data class Success(override val output: String) : PrivilegedOutcome {
        override val exitCode: Int get() = 0
    }

    data class NonZeroExit(override val exitCode: Int, override val output: String) : PrivilegedOutcome

    data class Timeout(val afterMs: Long) : PrivilegedOutcome {
        override val output: String get() = ""
        override val exitCode: Int get() = EXIT_TIMEOUT
    }

    data class PermissionDenied(val detail: String) : PrivilegedOutcome {
        override val output: String get() = detail
        override val exitCode: Int get() = EXIT_UNAVAILABLE
    }

    /** The Shizuku binder or the root shell is gone, or the backend is not connected. */
    data object BinderDisconnected : PrivilegedOutcome {
        override val output: String get() = ""
        override val exitCode: Int get() = EXIT_UNAVAILABLE
    }

    companion object {
        const val EXIT_UNAVAILABLE = -1
        const val EXIT_TIMEOUT = -2

        /** Classifies a finished command. Permission errors are recognised from the shell text. */
        fun fromExit(exitCode: Int, output: String, timeoutMs: Long = 0L): PrivilegedOutcome = when {
            exitCode == 0 -> Success(output)
            exitCode == EXIT_TIMEOUT -> Timeout(timeoutMs)
            looksLikePermissionDenial(output) -> PermissionDenied(output)
            else -> NonZeroExit(exitCode, output)
        }

        private fun looksLikePermissionDenial(output: String): Boolean =
            output.contains("Permission denied", ignoreCase = true) ||
                output.contains("Operation not permitted", ignoreCase = true) ||
                output.contains("SecurityException")
    }
}

/** Packages that failed to change state and how many succeeded. */
data class SuspendOutcome(val failedPackages: List<String>, val successCount: Int)
