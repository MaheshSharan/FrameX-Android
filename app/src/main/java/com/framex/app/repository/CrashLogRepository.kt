package com.framex.app.repository

import android.content.Context
import com.framex.app.utils.CrashHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashLogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasCrashLog(): Boolean = CrashHandler.hasCrashLog(context)
    fun clearCrashLog(): Boolean = CrashHandler.clearCrashLog(context)
    fun shareCrashLog() = CrashHandler.shareCrashLog(context)
}
