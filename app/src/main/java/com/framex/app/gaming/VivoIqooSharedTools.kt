package com.framex.app.gaming

import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import com.framex.app.utils.ShellSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared manual optimization tools for Vivo and iQOO devices.
 * Covers manual perf_game_list management, 144Game_mergelist, and AOT speed compilation.
 */
@Singleton
class VivoIqooSharedTools @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val vivoSuiteGate: VivoSuiteGate
) {
    companion object {
        private const val TAG = "VivoIqooSharedTools"
    }

    // =========================================================================
    // perf_game_list (system: namespace, colon-separated)
    // =========================================================================

    suspend fun getRawPerfGameList(): String = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext ""
        queryContentUri("content://settings/system/perf_game_list")
    }

    suspend fun getPerfGameList(): List<String> = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext emptyList()
        getRawPerfGameList().split(":").map { it.trim() }.filter { it.isNotBlank() }
    }

    suspend fun injectPerfGameList(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext false
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val existingRaw = queryContentUri("content://settings/system/perf_game_list")
        val listTokens = existingRaw.split(":").map { it.trim() }.filter { it.isNotBlank() }
        val updatedTokens = if (safePkg in listTokens) listTokens else listTokens + safePkg
        val updatedList = updatedTokens.joinToString(":", postfix = ":")
        val escapedValue = updatedList.replace(":", "\\\\:")

        val payload = listOf(
            "content delete --uri content://settings/system/perf_game_list",
            "content insert --uri content://settings/system --bind name:s:perf_game_list --bind value:s:$escapedValue",
            "content insert --uri content://settings/global --bind name:s:game_cube_apps --bind value:s:$safePkg"
        ).joinToString("; ")
        val exitCode = shizukuManager.executeCommandWithExitCode(payload)
        FrameXLog.d("injectPerfGameList($safePkg): exitCode=$exitCode", tag = TAG)
        exitCode == 0
    }

    suspend fun removePerfGame(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoOrIqooHardware) return@withContext false
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val existingRaw = queryContentUri("content://settings/system/perf_game_list")
        val listTokens = existingRaw.split(":").map { it.trim() }.filter { it.isNotBlank() && it != safePkg }

        val payload = if (listTokens.isNotEmpty()) {
            val updatedList = listTokens.joinToString(":", postfix = ":")
            val escapedValue = updatedList.replace(":", "\\\\:")
            listOf(
                "content delete --uri content://settings/system/perf_game_list",
                "content insert --uri content://settings/system --bind name:s:perf_game_list --bind value:s:$escapedValue"
            ).joinToString("; ")
        } else {
            "content delete --uri content://settings/system/perf_game_list"
        }
        val exitCode = shizukuManager.executeCommandWithExitCode(payload)
        FrameXLog.d("removePerfGame($safePkg): exitCode=$exitCode", tag = TAG)
        exitCode == 0
    }

    // =========================================================================
    // 144Game_mergelist (secure: namespace, comma-separated)
    // =========================================================================

    suspend fun getRaw144MergeList(): String = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext ""
        querySecureSetting("144Game_mergelist")
    }

    suspend fun get144MergeList(): List<String> = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext emptyList()
        getRaw144MergeList().split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    suspend fun addTo144MergeList(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext false
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val existingRaw = querySecureSetting("144Game_mergelist")
        val tokens = existingRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (safePkg in tokens) return@withContext true
        val updatedTokens = tokens + safePkg
        val updatedValue = updatedTokens.joinToString(",")

        val payload = listOf(
            "content delete --uri content://settings/secure/144Game_mergelist",
            "content insert --uri content://settings/secure --bind name:s:144Game_mergelist --bind value:s:$updatedValue"
        ).joinToString("; ")
        val exitCode = shizukuManager.executeCommandWithExitCode(payload)
        FrameXLog.d("addTo144MergeList($safePkg): exitCode=$exitCode", tag = TAG)
        exitCode == 0
    }

    suspend fun removeFrom144MergeList(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoOrIqooHardware) return@withContext false
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val existingRaw = querySecureSetting("144Game_mergelist")
        val tokens = existingRaw.split(",").map { it.trim() }.filter { it.isNotBlank() && it != safePkg }

        val payload = if (tokens.isNotEmpty()) {
            val updatedValue = tokens.joinToString(",")
            listOf(
                "content delete --uri content://settings/secure/144Game_mergelist",
                "content insert --uri content://settings/secure --bind name:s:144Game_mergelist --bind value:s:$updatedValue"
            ).joinToString("; ")
        } else {
            "content delete --uri content://settings/secure/144Game_mergelist"
        }
        val exitCode = shizukuManager.executeCommandWithExitCode(payload)
        FrameXLog.d("removeFrom144MergeList($safePkg): exitCode=$exitCode", tag = TAG)
        exitCode == 0
    }

    // =========================================================================
    // AOT Compilation & Package Filter Inspection
    // =========================================================================

    suspend fun getPackageCompileFilter(packageName: String): String = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext ""
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext "unknown"
        val dumpRes = shizukuManager.executeCommandWithResult("dumpsys package $safePkg | grep filter=")
        val line = dumpRes?.output?.lines()?.firstOrNull { it.contains("filter=") }?.trim().orEmpty()
        val match = Regex("filter=\\[?([a-zA-Z0-9_-]+)\\]?").find(line)
        match?.groupValues?.getOrNull(1) ?: if (line.isNotBlank()) line else "unknown"
    }

    suspend fun compileSpeedAot(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext false
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        FrameXLog.i("Starting AOT speed compilation for $safePkg...", tag = TAG)
        val compileRes = shizukuManager.executeCommandWithResult("pm compile -m speed -f $safePkg")
        val isCompiled = compileRes?.output?.contains("Success", ignoreCase = true) == true
        val dumpRes = shizukuManager.executeCommandWithResult("dumpsys package $safePkg | grep filter=")
        val filterSpeed = dumpRes?.output?.contains("filter=[speed]", ignoreCase = true) == true
        val success = isCompiled || filterSpeed
        FrameXLog.i("AOT speed compilation for $safePkg: success=$success (filterSpeed=$filterSpeed)", tag = TAG)
        success
    }

    suspend fun isSpeedCompiled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return@withContext false
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val dumpRes = shizukuManager.executeCommandWithResult("dumpsys package $safePkg | grep filter=")
        dumpRes?.output?.contains("filter=[speed]", ignoreCase = true) == true
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private suspend fun queryContentUri(uri: String): String {
        val result = shizukuManager.executeCommandWithResult("content query --uri $uri")
        val output = result?.output?.trim().orEmpty()
        val valueMatch = Regex("value=([^,\\n]*)").find(output)
        val raw = valueMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
        FrameXLog.d("queryContentUri($uri): raw=$raw", tag = TAG)
        return raw
    }

    private suspend fun querySecureSetting(key: String): String {
        val result = shizukuManager.executeCommandWithResult(
            "content query --uri content://settings/secure --projection value --where \"name='$key'\""
        )
        val line = result?.output?.lines()?.firstOrNull { it.contains("value=") } ?: return ""
        val raw = Regex("value=([^,}\\s]+)").find(line)?.groupValues?.get(1)?.trim().orEmpty()
        FrameXLog.d("querySecureSetting($key): raw=$raw", tag = TAG)
        return raw
    }
}
