package com.framex.app.gaming

import android.content.Context
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.utils.ShellSanitizer

/** Looks up the process id and uid of a game package. */
internal object GamingProcessResolver {

    suspend fun pidOf(privilegeManager: PrivilegeManager, packageName: String): Int {
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return 0
        val out = privilegeManager.exec("pidof $safePkg").output.trim()
        return out.split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: 0
    }

    fun uidOf(context: Context, packageName: String?): Int? {
        if (packageName.isNullOrBlank()) return null
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrNull()
    }
}
