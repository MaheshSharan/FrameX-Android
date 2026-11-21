package com.framex.app.gaming

import android.content.Context

/** Looks up the uid of a game package. */
internal object GamingProcessResolver {

    fun uidOf(context: Context, packageName: String?): Int? {
        if (packageName.isNullOrBlank()) return null
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0).uid
        }.getOrNull()
    }
}
