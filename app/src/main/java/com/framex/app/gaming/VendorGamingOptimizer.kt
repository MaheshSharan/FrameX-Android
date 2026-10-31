package com.framex.app.gaming

import android.content.Context
import android.widget.Toast
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.catalog.Platform
import com.framex.app.gaming.catalog.ResolveContext
import com.framex.app.gaming.catalog.VendorCommandExecutor
import com.framex.app.gaming.catalog.VendorCommandRestorer
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog
import com.framex.app.utils.ShellSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Session logic shared by the Vivo and iQOO suites. The commands themselves live in the
 * catalog; this class only knows which game is running and reports what happened.
 */
abstract class VendorGamingOptimizer(
    private val platform: Platform,
    private val displayName: String,
    private val showPulseToast: Boolean,
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
    private val settingsRepository: SettingsRepository,
    private val executor: VendorCommandExecutor,
    private val restorer: VendorCommandRestorer,
    private val auditLogRepository: SystemAuditLogRepository,
    private val deviceDiagnosticManager: DeviceDiagnosticManager
) {
    private var activeGamePackage: String? = null
    private var activeGamePid: Int = 0

    private fun resolveContext() = ResolveContext(
        targetPackage = ShellSanitizer.sanitizePackageName(activeGamePackage),
        pid = activeGamePid,
        refreshRateHz = deviceDiagnosticManager.getMaxHardwareRefreshRate().toInt().coerceAtLeast(MIN_REFRESH_HZ)
    )

    suspend fun applyOptimizations(
        packageName: String?,
        pid: Int = 0,
        onProgress: (suspend (Float, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!privilegeManager.isReady.value) {
            auditLogRepository.addLog("Gaming Mode Activation", NOT_READY, LogStatus.FAILED)
            return@withContext false
        }
        activeGamePackage = packageName
        activeGamePid = pid
        onProgress?.invoke(PROGRESS_APPLY, "Applying $displayName hardware suite…")
        val applied = executor.activate(platform, resolveContext())
        val target = if (packageName != null) " (Target: $packageName, PID: $pid)" else ""
        auditLogRepository.addLog(
            "Gaming Mode Activation",
            if (applied) "Hardware gaming optimizations applied$target" else "Some hardware optimizations failed$target",
            if (applied) LogStatus.SUCCESS else LogStatus.FAILED
        )
        applied
    }

    suspend fun promoteGamePid(packageName: String, pid: Int) = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext
        activeGamePackage = packageName
        activeGamePid = pid
        FrameXLog.i("Promoting live PID for $displayName game handshake: $packageName (PID=$pid)", tag = TAG)
        executor.promote(platform, resolveContext())
    }

    suspend fun runPeriodicMaintenance() = withContext(Dispatchers.IO) {
        if (!privilegeManager.isReady.value) return@withContext
        if (!settingsRepository.vivoMaintenancePulse.value) return@withContext
        val allPassed = executor.pulse(platform, resolveContext())
        resolvePidIfMissing()
        val pidStatus = if (activeGamePid > 0) "PID $activeGamePid ✓" else "Standby"
        auditLogRepository.addLog(
            "2-Min Maintenance Pulse",
            "$displayName pulse re-asserted | Game: ${activeGamePackage ?: "None"} ($pidStatus)",
            if (allPassed) LogStatus.SUCCESS else LogStatus.FAILED
        )
        if (showPulseToast && settingsRepository.auditLoggingEnabled.value) showToast(allPassed, pidStatus)
    }

    /** Puts every value changed this session back. True only when all of them were restored. */
    open suspend fun revertOptimizations(): Boolean = withContext(Dispatchers.IO) {
        if (!privilegeManager.isReady.value) {
            auditLogRepository.addLog("Gaming Mode Deactivation", NOT_READY, LogStatus.FAILED)
            return@withContext false
        }
        val restored = restorer.restore(platform)
        activeGamePackage = null
        activeGamePid = 0
        auditLogRepository.addLog(
            "Gaming Mode Deactivation",
            if (restored) "$displayName settings restored to their values before the session"
            else "$displayName settings could not all be restored; they are kept for a retry",
            if (restored) LogStatus.SUCCESS else LogStatus.FAILED
        )
        restored
    }

    private suspend fun resolvePidIfMissing() {
        val pkg = activeGamePackage ?: return
        if (activeGamePid > 0) return
        val resolved = queryProcessPid(pkg)
        if (resolved > 0) promoteGamePid(pkg, resolved)
    }

    private suspend fun queryProcessPid(packageName: String): Int {
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return 0
        val output = privilegeManager.exec("pidof $safePkg").output.trim()
        return output.split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: 0
    }

    private suspend fun showToast(allPassed: Boolean, pidStatus: String) {
        val message = if (allPassed) "FrameX Pulse: Monster Flags Re-asserted ✓ | $pidStatus"
        else "FrameX Pulse: Monster Flags Re-assert Failed"
        withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val TAG = "VendorGamingOptimizer"
        const val NOT_READY = "Privileged access not ready or permission missing"
        const val MIN_REFRESH_HZ = 60
        const val PROGRESS_APPLY = 0.70f
    }
}
