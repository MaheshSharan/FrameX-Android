package com.framex.app.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.framex.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val CRASH_DIR_NAME = "crashes"
    private const val CRASH_FILE_NAME = "last_crash.txt"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashLog(throwable)
        } catch (e: Exception) {
            FrameXLog.e("Failed to write local crash log", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashLog(throwable: Throwable) {
        val ctx = appContext ?: return
        val crashDir = File(ctx.filesDir, CRASH_DIR_NAME).apply { if (!exists()) mkdirs() }
        val crashFile = File(crashDir, CRASH_FILE_NAME)

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timestamp = dateFormat.format(Date())

        val logContent = buildString {
            appendLine("=== FrameX Local Crash Report ===")
            appendLine("Timestamp: $timestamp")
            appendLine("App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Thread: ${Thread.currentThread().name}")
            appendLine("=================================")
            appendLine()
            appendLine(stackTrace)
        }

        crashFile.writeText(logContent)
    }

    fun hasCrashLog(context: Context): Boolean {
        val file = File(File(context.filesDir, CRASH_DIR_NAME), CRASH_FILE_NAME)
        return file.exists() && file.length() > 0
    }

    fun readCrashLog(context: Context): String? {
        val file = File(File(context.filesDir, CRASH_DIR_NAME), CRASH_FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clearCrashLog(context: Context): Boolean {
        val file = File(File(context.filesDir, CRASH_DIR_NAME), CRASH_FILE_NAME)
        return if (file.exists()) file.delete() else false
    }

    fun shareCrashLog(context: Context) {
        val crashText = readCrashLog(context) ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FrameX Crash Log (v${BuildConfig.VERSION_NAME})")
            putExtra(Intent.EXTRA_TEXT, crashText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(shareIntent, "Share FrameX Crash Log").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }
}
