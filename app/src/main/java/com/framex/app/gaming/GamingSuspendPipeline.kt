package com.framex.app.gaming

import com.framex.app.privilege.PrivilegeManager

internal object GamingSuspendPipeline {

    /**
     * Unsuspends in one batch, then retries each package that failed on its own.
     * Returns the packages that are still suspended.
     */
    suspend fun unsuspend(privilegeManager: PrivilegeManager, packages: Set<String>): Set<String> {
        val batch = privilegeManager.suspendPackages(packages.toList(), suspended = false)
        val failed = batch?.failedPackages?.toSet() ?: packages
        if (failed.isEmpty()) return emptySet()
        val stillSuspended = mutableSetOf<String>()
        for (pkg in failed) {
            if (privilegeManager.execExit("cmd package unsuspend --user 0 $pkg") != 0) stillSuspended.add(pkg)
        }
        return stillSuspended
    }
}
