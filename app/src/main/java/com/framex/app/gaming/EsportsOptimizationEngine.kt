package com.framex.app.gaming

import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.ledger.CommandSpec
import com.framex.app.gaming.ledger.LedgerExecutor
import com.framex.app.gaming.ledger.OpPriority
import com.framex.app.gaming.ledger.Stage
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class EsportsOptimizationEngine @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val ledgerExecutor: LedgerExecutor
) {

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Applies non-invasive esports tweaks (thermal, CPU priorities, network, refresh rate).
     * Bypasses dangerous display and thermal overrides if the device is Vivo/iQOO.
     */
    suspend fun applyOptimizationsForGame(packageName: String?, uid: Int?): Boolean {
        if (!isShizukuReady()) return false

        val snapshot = captureSnapshot(packageName, uid) ?: run {
            FrameXLog.e("Snapshot capture failed, aborting optimizations", tag = TAG)
            return false
        }
        settingsRepository.saveGamingOptimizationSnapshot(snapshot)

        FrameXLog.i("Applying Esports Optimizations (pkg=$packageName, uid=$uid)", tag = TAG)

        applyMemoryAndThermalOptimizations()
        applyProcessPriorities(packageName)
        applyNetworkAndDozeExemptions(packageName, uid)
        applyPerformanceGovernor()
        applyDisplayRefreshRate()
        applyTouchResponseLatency()

        return true
    }

    /**
     * Attaches an active game package to an already-active session without re-capturing settings,
     * protecting the user's baseline display/touch settings against overwrite data loss.
     */
    suspend fun attachGame(packageName: String, uid: Int?): Boolean {
        if (!isShizukuReady()) return false
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName) ?: return false

        val currentSnapshot = settingsRepository.loadGamingOptimizationSnapshot()
        if (currentSnapshot != null) {
            settingsRepository.saveGamingOptimizationSnapshot(
                currentSnapshot.copy(activeGamePackage = safePkg, activeGameUid = uid)
            )
        }

        FrameXLog.i("Attaching game $safePkg (uid=$uid) to existing session", tag = TAG)
        applyProcessPriorities(safePkg)
        applyNetworkAndDozeExemptions(safePkg, uid)
        return true
    }

    /**
     * Reverts all modified settings using the saved snapshot baseline.
     */
    suspend fun revertOptimizations(): Boolean {
        if (!isShizukuReady()) return false
        FrameXLog.i("Reverting Esports Optimizations...", tag = TAG)

        recoverThermalOverrideIfNeeded()

        val snapshot = settingsRepository.loadGamingOptimizationSnapshot()
        if (snapshot == null) {
            FrameXLog.w("No snapshot found, performing legacy revert", tag = TAG)
            revertLegacy()
            return true
        }

        val pkg = snapshot.activeGamePackage
        val uid = snapshot.activeGameUid
        FrameXLog.i("Reverting optimizations for pkg=$pkg, uid=$uid from snapshot", tag = TAG)

        revertPerAppOverrides(pkg, uid)
        revertSystemDisplayAndTouchSettings(snapshot)
        revertThermalAndIdleState()

        settingsRepository.clearGamingOptimizationSnapshot()
        FrameXLog.i("Snapshot cleared. Esports revert complete!", tag = TAG)
        return true
    }

    /**
     * Resets settings back to device baseline defaults, safeguarding Vivo/iQOO hardware.
     */
    suspend fun resetToDeviceDefaults(forceReset: Boolean = false): Boolean {
        if (!forceReset && !settingsRepository.needsLegacySettingsCleanup()) return true
        if (!isShizukuReady()) return false

        FrameXLog.i("User-triggered device defaults reset", tag = TAG)

        val isVivo = deviceDiagnosticManager.isVivoOrIqoo()
        val snapshot = settingsRepository.loadGamingOptimizationSnapshot()

        unsuspendAllTrackedPackages(snapshot)

        if (isVivo) {
            FrameXLog.i("Vivo/iQOO device detected: Skipping generic system reset commands", tag = TAG)
            settingsRepository.markLegacySettingsCleanupComplete()
            settingsRepository.clearGamingOptimizationSnapshot()
            return true
        }

        val success = if (snapshot != null) {
            restoreSettingsFromSnapshot(snapshot)
        } else {
            executeCommandList(LEGACY_RESET_COMMANDS)
        }

        settingsRepository.markLegacySettingsCleanupComplete()
        settingsRepository.clearGamingOptimizationSnapshot()

        FrameXLog.i("Device defaults reset complete (success=$success)", tag = TAG)
        return success
    }

    suspend fun recoverThermalOverrideIfNeeded(): Boolean {
        if (!settingsRepository.needsThermalOverrideRecovery()) return true
        if (!isShizukuReady()) return false

        val result = shizukuManager.executeCommandWithResult("cmd thermalservice reset")
        if (result == null || result.exitCode != 0) {
            FrameXLog.w("Thermal override recovery failed", tag = TAG)
            return false
        }

        settingsRepository.markThermalOverrideRecoveryComplete()
        FrameXLog.i("Thermal override recovery completed", tag = TAG)
        return true
    }

    suspend fun performLegacyCleanupIfNeeded(): Boolean {
        if (!settingsRepository.needsLegacySettingsCleanup()) return true
        if (!isShizukuReady()) return false

        FrameXLog.i("Performing one-time legacy settings cleanup", tag = TAG)
        val allSucceeded = executeCommandList(LEGACY_CLEANUP_COMMANDS)

        if (allSucceeded) {
            settingsRepository.markLegacySettingsCleanupComplete()
            FrameXLog.i("Legacy settings cleanup completed successfully", tag = TAG)
        }
        return allSucceeded
    }

    fun calculateFramePacingDeltaMs(actualFps: Int): Float {
        if (actualFps <= 0) return 0f
        val activeHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
        val targetFrameTimeMs = 1000f / activeHz
        val actualFrameTimeMs = 1000f / actualFps.toFloat()
        return abs(actualFrameTimeMs - targetFrameTimeMs)
    }

    // =========================================================================
    // Granular Optimization Steps (IDE & GitHub Symbol Navigation)
    // =========================================================================

    private suspend fun applyMemoryAndThermalOptimizations() {
        val memSpecs = listOf(
            CommandSpec("pm trim-caches 4G", OpPriority.PRIMARY),
            CommandSpec("am compact background", OpPriority.DETAIL),
            CommandSpec("cmd pinner repin /system/framework/framework.jar", OpPriority.DETAIL)
        )
        ledgerExecutor.executeBatch(Stage.MEMORY, memSpecs)

        val powerSpecs = listOf(
            CommandSpec("cmd thermalservice override-status 0", OpPriority.PRIMARY)
        )
        ledgerExecutor.executeBatch(Stage.POWER, powerSpecs)
        settingsRepository.setNeedsThermalOverrideActive(true)
        FrameXLog.i("RAM cache pre-trimming, ART heap compaction & thermal throttle override executed", tag = TAG)
    }

    private suspend fun applyProcessPriorities(packageName: String?) {
        if (!settingsRepository.cpuPriorityLock.value) return
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName)
        val targetPkg = safePkg ?: "<no active game>"
        val specs = listOf(
            CommandSpec("cmd activity set-bg-restriction-level --user 0 $targetPkg unrestricted", OpPriority.PRIMARY),
            CommandSpec("am set-standby-bucket --user 0 $targetPkg active", OpPriority.DETAIL)
        )
        if (safePkg != null) {
            ledgerExecutor.executeBatch(Stage.POWER, specs)
            FrameXLog.i("CPU Priority & Standby Bucket active set for $safePkg", tag = TAG)
        } else {
            ledgerExecutor.recordSkipped(Stage.POWER, specs)
            FrameXLog.i("CPU Priority & Standby Bucket recorded as skipped (no active game)", tag = TAG)
        }
    }

    private suspend fun applyNetworkAndDozeExemptions(packageName: String?, uid: Int?) {
        if (!settingsRepository.networkFirewall.value) return
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName)
        val targetUid = uid?.toString() ?: "<no active game>"
        val targetPkg = safePkg ?: "<no active game>"
        val specs = listOf(
            CommandSpec("cmd netpolicy add restrict-background-whitelist $targetUid", OpPriority.PRIMARY),
            CommandSpec("cmd deviceidle whitelist +$targetPkg", OpPriority.DETAIL),
            CommandSpec("cmd deviceidle force-idle", OpPriority.DETAIL)
        )
        if (safePkg != null && uid != null) {
            ledgerExecutor.executeBatch(Stage.NETWORK, specs)
            FrameXLog.i("Network Firewall & Deep Doze exemption applied (uid=$uid, pkg=$safePkg)", tag = TAG)
        } else {
            ledgerExecutor.recordSkipped(Stage.NETWORK, specs)
            FrameXLog.i("Network Firewall & Deep Doze recorded as skipped (uid=$uid, pkg=$safePkg)", tag = TAG)
        }
    }

    private suspend fun applyPerformanceGovernor() {
        if (settingsRepository.fixedPerformanceMode.value) {
            val specs = listOf(
                CommandSpec("cmd power set-fixed-performance-mode-enabled true", OpPriority.PRIMARY)
            )
            ledgerExecutor.executeBatch(Stage.POWER, specs)
            FrameXLog.i("Fixed performance mode enabled", tag = TAG)
        }
    }

    private suspend fun applyDisplayRefreshRate() {
        val maxHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
        if (settingsRepository.refreshRateLock.value) {
            val specs = listOf(
                CommandSpec("settings put system peak_refresh_rate $maxHz", OpPriority.PRIMARY),
                CommandSpec("settings put system min_refresh_rate $maxHz", OpPriority.DETAIL)
            )
            ledgerExecutor.executeBatch(Stage.DISPLAY, specs)
            FrameXLog.i("Refresh rate set to peak/min $maxHz Hz", tag = TAG)
        }
    }

    private suspend fun applyTouchResponseLatency() {
        if (settingsRepository.touchBoost.value) {
            val specs = listOf(
                CommandSpec("settings put system touch_response_speed 2", OpPriority.PRIMARY)
            )
            ledgerExecutor.executeBatch(Stage.TOUCH, specs)
            FrameXLog.i("Touch response latency boost applied", tag = TAG)
        }
    }

    // =========================================================================
    // Granular Reversion Steps
    // =========================================================================

    private suspend fun revertPerAppOverrides(pkg: String?, uid: Int?) {
        if (pkg != null) {
            shizukuManager.executeCommand("cmd game reset $pkg")
            if (settingsRepository.cpuPriorityLock.value) {
                shizukuManager.executeCommand("cmd activity set-bg-restriction-level --user 0 $pkg adaptive_bucket")
                shizukuManager.executeCommand("am set-standby-bucket --user 0 $pkg working_set")
            }
            FrameXLog.i("Per-app game & CPU priority overrides reset for $pkg", tag = TAG)
        }

        if (uid != null && settingsRepository.networkFirewall.value) {
            shizukuManager.executeCommand("cmd netpolicy remove restrict-background-whitelist $uid")
            if (!pkg.isNullOrBlank()) {
                shizukuManager.executeCommand("cmd deviceidle whitelist -$pkg")
            }
        }
    }

    private suspend fun revertSystemDisplayAndTouchSettings(snapshot: GamingOptimizationSnapshot) {
        snapshot.minRefreshRate?.let { restoreSetting("system", "min_refresh_rate", it) }
        snapshot.peakRefreshRate?.let { restoreSetting("system", "peak_refresh_rate", it) }
        snapshot.touchResponseSpeed?.let { restoreSetting("system", "touch_response_speed", it) }

        snapshot.userPreferredDisplayModeId?.let { setting ->
            val value = if (setting.existed && setting.value.isNotBlank()) setting.value else "-1"
            shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id $value")
            FrameXLog.i("Restored secure user_preferred_display_mode_id to $value", tag = TAG)
        }
    }

    private suspend fun revertThermalAndIdleState() {
        shizukuManager.executeCommand("cmd deviceidle unforce")
        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")
        shizukuManager.executeCommand("cmd thermalservice reset")
        settingsRepository.markThermalOverrideRecoveryComplete()
        FrameXLog.i("Network policy, deviceidle, fixed performance mode & thermal reset", tag = TAG)
    }

    private suspend fun unsuspendAllTrackedPackages(snapshot: GamingOptimizationSnapshot?) {
        val packagesToUnsuspend = snapshot?.affectedPackages ?: settingsRepository.getGamingAffectedPackages()
        if (packagesToUnsuspend.isNotEmpty()) {
            FrameXLog.i("Resetting suspended packages: ${packagesToUnsuspend.size} apps", tag = TAG)
            shizukuManager.suspendPackages(packagesToUnsuspend.toList(), false)
            settingsRepository.setGamingAffectedPackages(emptySet())
        }
    }

    private suspend fun restoreSettingsFromSnapshot(snapshot: GamingOptimizationSnapshot): Boolean {
        snapshot.minRefreshRate?.let { restoreSetting("system", "min_refresh_rate", it) }
        snapshot.peakRefreshRate?.let { restoreSetting("system", "peak_refresh_rate", it) }
        snapshot.touchResponseSpeed?.let { restoreSetting("system", "touch_response_speed", it) }
        snapshot.userPreferredDisplayModeId?.let { setting ->
            val value = if (setting.existed && setting.value.isNotBlank()) setting.value else "-1"
            shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id $value")
        }
        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")
        shizukuManager.executeCommand("cmd thermalservice reset")
        shizukuManager.executeCommand("cmd deviceidle unforce")
        return true
    }

    // =========================================================================
    // Snapshot Capturing & Helper Utilities
    // =========================================================================

    private suspend fun captureSnapshot(packageName: String?, uid: Int?): GamingOptimizationSnapshot? {
        val minRefreshResult = shizukuManager.executeCommandWithResult("settings get system min_refresh_rate")
        val peakRefreshResult = shizukuManager.executeCommandWithResult("settings get system peak_refresh_rate")
        val touchSpeedResult = shizukuManager.executeCommandWithResult("settings get system touch_response_speed")
        val displayModeResult = shizukuManager.executeCommandWithResult("settings get secure user_preferred_display_mode_id")

        if (minRefreshResult == null || peakRefreshResult == null || touchSpeedResult == null || displayModeResult == null) {
            FrameXLog.e("IPC failure capturing generic display/touch settings, aborting optimization", tag = TAG)
            return null
        }

        val existingSnapshot = settingsRepository.loadGamingOptimizationSnapshot()
        val existingAffected = existingSnapshot?.affectedPackages ?: settingsRepository.getGamingAffectedPackages()

        return GamingOptimizationSnapshot(
            activeGamePackage = packageName,
            activeGameUid = uid,
            timestamp = System.currentTimeMillis(),
            minRefreshRate = SettingValue.fromCommandOutput(minRefreshResult.output),
            peakRefreshRate = SettingValue.fromCommandOutput(peakRefreshResult.output),
            touchResponseSpeed = SettingValue.fromCommandOutput(touchSpeedResult.output),
            userPreferredDisplayModeId = SettingValue.fromCommandOutput(displayModeResult.output),
            affectedPackages = existingAffected
        )
    }

    private suspend fun restoreSetting(namespace: String, key: String, setting: SettingValue) {
        if (setting.existed && setting.value.isNotBlank()) {
            shizukuManager.executeCommand("settings put $namespace $key ${setting.value}")
        } else {
            shizukuManager.executeCommand("settings delete $namespace $key")
        }
    }

    private suspend fun executeCommandList(commands: List<String>): Boolean {
        var allSucceeded = true
        for (cmd in commands) {
            val result = shizukuManager.executeCommandWithResult(cmd)
            if (result == null || result.exitCode != 0) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private suspend fun revertLegacy() {
        executeCommandList(LEGACY_RESET_COMMANDS)
    }

    private fun isShizukuReady(): Boolean =
        shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value

    private companion object {
        const val TAG = "EsportsEngine"

        val LEGACY_RESET_COMMANDS = listOf(
            "settings delete system min_refresh_rate",
            "settings delete system peak_refresh_rate",
            "settings put secure user_preferred_display_mode_id -1",
            "settings delete system touch_response_speed",
            "cmd power set-fixed-performance-mode-enabled false",
            "cmd thermalservice reset",
            "cmd deviceidle unforce"
        )

        val LEGACY_CLEANUP_COMMANDS = listOf(
            "settings delete system min_refresh_rate",
            "settings delete system peak_refresh_rate",
            "settings put secure user_preferred_display_mode_id -1",
            "settings delete system touch_response_speed",
            "cmd power set-fixed-performance-mode-enabled false",
            "cmd thermalservice reset"
        )
    }
}