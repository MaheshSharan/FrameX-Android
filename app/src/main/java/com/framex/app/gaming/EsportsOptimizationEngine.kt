package com.framex.app.gaming

import android.content.Context
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class EsportsOptimizationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val deviceDiagnosticManager: DeviceDiagnosticManager
) {

    /**
     * Captures a snapshot of all system settings FrameX is about to modify.
     * Returns null if any binder IPC command fails (indicating an un-restorable binder state).
     */
    private suspend fun captureSnapshot(packageName: String?, uid: Int?): GamingOptimizationSnapshot? {
        // Capture system settings (all devices)
        val minRefreshResult = shizukuManager.executeCommandWithResult("settings get system min_refresh_rate")
        val peakRefreshResult = shizukuManager.executeCommandWithResult("settings get system peak_refresh_rate")
        val touchSpeedResult = shizukuManager.executeCommandWithResult("settings get system touch_response_speed")

        if (minRefreshResult == null || peakRefreshResult == null || touchSpeedResult == null) {
            FrameXLog.e("IPC failure capturing generic display/touch settings, aborting optimization", tag = TAG)
            return null
        }

        val minRefresh = SettingValue.fromCommandOutput(minRefreshResult.output)
        val peakRefresh = SettingValue.fromCommandOutput(peakRefreshResult.output)
        val touchSpeed = SettingValue.fromCommandOutput(touchSpeedResult.output)

        // Capture secure display mode
        val displayModeResult = shizukuManager.executeCommandWithResult("settings get secure user_preferred_display_mode_id")
        if (displayModeResult == null) {
            FrameXLog.e("IPC failure capturing user_preferred_display_mode_id, aborting optimization", tag = TAG)
            return null
        }
        val userPreferredDisplayModeId = SettingValue.fromCommandOutput(displayModeResult.output)

        val existingSnapshot = settingsRepository.loadGamingOptimizationSnapshot()
        val existingAffected = existingSnapshot?.affectedPackages ?: settingsRepository.getGamingAffectedPackages()

        return GamingOptimizationSnapshot(
            activeGamePackage = packageName,
            activeGameUid = uid,
            timestamp = System.currentTimeMillis(),
            minRefreshRate = minRefresh,
            peakRefreshRate = peakRefresh,
            touchResponseSpeed = touchSpeed,
            userPreferredDisplayModeId = userPreferredDisplayModeId,
            affectedPackages = existingAffected
        )
    }

    suspend fun applyOptimizationsForGame(packageName: String?, uid: Int?): Boolean {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return false

        // Capture snapshot BEFORE making any changes
        val snapshot = captureSnapshot(packageName, uid)
        if (snapshot == null) {
            FrameXLog.e("Snapshot capture failed, aborting optimizations", tag = TAG)
            return false
        }

        // Save snapshot immediately
        settingsRepository.saveGamingOptimizationSnapshot(snapshot)

        FrameXLog.i("Applying Esports Optimizations (pkg=$packageName, uid=$uid)", tag = TAG)

        // 0. RAM Cache Pre-Trimming, ART Heap Compaction, Framework Pinning & Thermal Override
        shizukuManager.executeCommand("pm trim-caches 4G")
        shizukuManager.executeCommand("am compact background")
        runCatching { shizukuManager.executeCommand("cmd pinner repin /system/framework/framework.jar") }
        shizukuManager.executeCommand("cmd thermalservice override-status 0")
        settingsRepository.setNeedsThermalOverrideActive(true)
        FrameXLog.i("RAM cache pre-trimming, ART heap compaction & thermal throttle override executed", tag = TAG)

        // 1. CPU Priority & Memory Lock
        if (settingsRepository.cpuPriorityLock.value && packageName != null) {
            shizukuManager.executeCommand("cmd activity set-bg-restriction-level --user 0 $packageName unrestricted")
            shizukuManager.executeCommand("am set-standby-bucket --user 0 $packageName active")
            FrameXLog.i("CPU Priority & Standby Bucket active set for $packageName", tag = TAG)
        }

        // 2. Network Firewall & Deep Doze Exemption
        if (settingsRepository.networkFirewall.value && uid != null) {
            shizukuManager.executeCommand("cmd netpolicy add restrict-background-whitelist $uid")
            if (!packageName.isNullOrBlank()) {
                shizukuManager.executeCommand("cmd deviceidle whitelist +$packageName")
            }
            shizukuManager.executeCommand("cmd deviceidle force-idle")
            FrameXLog.i("Network Firewall & Deep Doze exemption applied (uid=$uid, pkg=$packageName)", tag = TAG)
        }

        // 3. Performance Governor Lock
        if (settingsRepository.fixedPerformanceMode.value) {
            shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled true")
            FrameXLog.i("Fixed performance mode enabled", tag = TAG)
        }

        // 4. Refresh Rate Lock & Display Mode Override
        val maxHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
        if (settingsRepository.refreshRateLock.value) {
            shizukuManager.executeCommand("settings put system peak_refresh_rate $maxHz")
            shizukuManager.executeCommand("settings put system min_refresh_rate $maxHz")
            FrameXLog.i("Refresh rate set to peak/min $maxHz Hz", tag = TAG)
        }

        // 5. Touch Response Latency Boost
        if (settingsRepository.touchBoost.value) {
            shizukuManager.executeCommand("settings put system touch_response_speed 2")
            FrameXLog.i("Touch response latency boost applied", tag = TAG)
        }

        return true
    }

    suspend fun revertOptimizations(): Boolean {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return false
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

        // Revert per-app overrides
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
        shizukuManager.executeCommand("cmd deviceidle unforce")
        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")
        FrameXLog.i("Network policy, deviceidle & fixed performance mode reset", tag = TAG)

        // Restore system display & touch settings
        snapshot.minRefreshRate?.let { restoreSetting("system", "min_refresh_rate", it) }
        snapshot.peakRefreshRate?.let { restoreSetting("system", "peak_refresh_rate", it) }
        snapshot.touchResponseSpeed?.let { restoreSetting("system", "touch_response_speed", it) }

        // Restore secure display mode ID (restore to -1 if absent)
        snapshot.userPreferredDisplayModeId?.let { setting ->
            if (setting.existed && setting.value.isNotBlank()) {
                shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id ${setting.value}")
                FrameXLog.i("Restored secure user_preferred_display_mode_id to ${setting.value}", tag = TAG)
            } else {
                shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id -1")
                FrameXLog.i("Restored secure user_preferred_display_mode_id to -1", tag = TAG)
            }
        }

        shizukuManager.executeCommand("cmd thermalservice reset")
        settingsRepository.markThermalOverrideRecoveryComplete()

        // Clear snapshot only after successful restoration
        settingsRepository.clearGamingOptimizationSnapshot()
        FrameXLog.i("Snapshot cleared. Esports revert complete!", tag = TAG)
        return true
    }

    private suspend fun restoreSetting(namespace: String, key: String, setting: SettingValue) {
        if (setting.existed && setting.value.isNotBlank()) {
            shizukuManager.executeCommand("settings put $namespace $key ${setting.value}")
        } else {
            shizukuManager.executeCommand("settings delete $namespace $key")
        }
    }

    private suspend fun revertLegacy() {
        shizukuManager.executeCommand("settings delete system min_refresh_rate")
        shizukuManager.executeCommand("settings delete system peak_refresh_rate")
        shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id -1")
        shizukuManager.executeCommand("settings delete system touch_response_speed")
        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")
        shizukuManager.executeCommand("cmd thermalservice reset")
        shizukuManager.executeCommand("cmd deviceidle unforce")
    }

    suspend fun recoverThermalOverrideIfNeeded(): Boolean {
        if (!settingsRepository.needsThermalOverrideRecovery()) return true
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return false

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
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return false

        FrameXLog.i("Performing one-time legacy settings cleanup", tag = TAG)

        val cleanupCommands = listOf(
            "settings delete system min_refresh_rate",
            "settings delete system peak_refresh_rate",
            "settings put secure user_preferred_display_mode_id -1",
            "settings delete system touch_response_speed",
            "cmd power set-fixed-performance-mode-enabled false",
            "cmd thermalservice reset"
        )

        var allSucceeded = true
        for (cmd in cleanupCommands) {
            val result = shizukuManager.executeCommandWithResult(cmd)
            if (result == null || result.exitCode != 0) {
                allSucceeded = false
            }
        }

        if (allSucceeded) {
            settingsRepository.markLegacySettingsCleanupComplete()
            FrameXLog.i("Legacy settings cleanup completed successfully", tag = TAG)
        }

        return allSucceeded
    }

    suspend fun resetToDeviceDefaults(forceReset: Boolean = false): Boolean {
        if (!forceReset && !settingsRepository.needsLegacySettingsCleanup()) return true
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return false

        FrameXLog.i("User-triggered device defaults reset", tag = TAG)

        val isVivo = deviceDiagnosticManager.isVivoOrIqoo()
        val snapshot = settingsRepository.loadGamingOptimizationSnapshot()

        // 1. Unsuspend any packages recorded in snapshot or settings
        val packagesToUnsuspend = snapshot?.affectedPackages ?: settingsRepository.getGamingAffectedPackages()
        if (packagesToUnsuspend.isNotEmpty()) {
            FrameXLog.i("Resetting suspended packages: ${packagesToUnsuspend.size} apps", tag = TAG)
            shizukuManager.suspendPackages(packagesToUnsuspend.toList(), false)
            settingsRepository.setGamingAffectedPackages(emptySet())
        }

        // 2. On Vivo/iQOO devices, do NOT execute generic display/power/thermal reset commands
        if (isVivo) {
            FrameXLog.i("Vivo/iQOO device detected: Skipping generic system reset commands", tag = TAG)
            settingsRepository.markLegacySettingsCleanupComplete()
            settingsRepository.clearGamingOptimizationSnapshot()
            return true
        }

        // 3. For non-Vivo devices: restore only what was actually snapshotted if available
        var success = true
        if (snapshot != null) {
            snapshot.minRefreshRate?.let { restoreSetting("system", "min_refresh_rate", it) }
            snapshot.peakRefreshRate?.let { restoreSetting("system", "peak_refresh_rate", it) }
            snapshot.touchResponseSpeed?.let { restoreSetting("system", "touch_response_speed", it) }
            snapshot.userPreferredDisplayModeId?.let { setting ->
                if (setting.existed && setting.value.isNotBlank()) {
                    shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id ${setting.value}")
                } else {
                    shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id -1")
                }
            }
            shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")
            shizukuManager.executeCommand("cmd thermalservice reset")
            shizukuManager.executeCommand("cmd deviceidle unforce")
        } else {
            // Fallback for non-Vivo devices if forced reset without snapshot
            val resetCommands = listOf(
                "settings delete system min_refresh_rate",
                "settings delete system peak_refresh_rate",
                "settings put secure user_preferred_display_mode_id -1",
                "settings delete system touch_response_speed",
                "cmd power set-fixed-performance-mode-enabled false",
                "cmd thermalservice reset",
                "cmd deviceidle unforce"
            )
            for (cmd in resetCommands) {
                val result = shizukuManager.executeCommandWithResult(cmd)
                if (result == null || result.exitCode != 0) {
                    success = false
                }
            }
        }

        settingsRepository.markLegacySettingsCleanupComplete()
        settingsRepository.clearGamingOptimizationSnapshot()

        FrameXLog.i("Device defaults reset complete (success=$success)", tag = TAG)
        return success
    }

    fun calculateFramePacingDeltaMs(actualFps: Int): Float {
        if (actualFps <= 0) return 0f
        val activeHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
        val targetFrameTimeMs = 1000f / activeHz
        val actualFrameTimeMs = 1000f / actualFps.toFloat()
        return abs(actualFrameTimeMs - targetFrameTimeMs)
    }

    private companion object {
        const val TAG = "EsportsEngine"
    }
}