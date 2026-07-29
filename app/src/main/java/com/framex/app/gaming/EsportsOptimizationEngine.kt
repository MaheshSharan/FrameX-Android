package com.framex.app.gaming

import android.content.Context
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Result of a single Vivo T3 Ultra hardware optimization execution.
 * Each field is true only when the corresponding Shizuku command completed without error.
 * [maxHzApplied] carries the actual device max Hz used at activation time (not hardcoded).
 */
data class VivoOptimizationResult(
    val displayModeLock: Boolean,
    val maxHzApplied: Int,
    val touchBoost: Boolean,
    val whitelistApplied: Boolean,
)

@Singleton
class EsportsOptimizationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val deviceDiagnosticManager: DeviceDiagnosticManager
) {
    private val _vivoOptimizationResult = MutableStateFlow<VivoOptimizationResult?>(null)
    /** Null when gaming mode is inactive or device is not Vivo T3 Ultra. Non-null when active. */
    val vivoOptimizationResult: StateFlow<VivoOptimizationResult?> = _vivoOptimizationResult.asStateFlow()

    // Vivo OEM whitelist key names (settings global CSV)
    private val vivoWhitelistKeys = listOf(
        "game_cube_apps",
        "speed_mode_apps",
        "vivo_high_refresh_rate_apps",
        "vivo_screen_refresh_rate_apps_list"
    )

    /**
     * Captures a snapshot of all system settings FrameX is about to modify.
     * Returns null if any binder IPC command fails (indicating an un-restorable binder state).
     */
    private suspend fun captureSnapshot(packageName: String?, uid: Int?): GamingOptimizationSnapshot? {
        val isVivo = deviceDiagnosticManager.isVivoOrIqoo() && settingsRepository.vivoOptEnabled.value

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

        // Capture Vivo secure display mode (Group 1 mode 4)
        val displayModeResult = shizukuManager.executeCommandWithResult("settings get secure user_preferred_display_mode_id")
        if (displayModeResult == null) {
            FrameXLog.e("IPC failure capturing user_preferred_display_mode_id, aborting optimization", tag = TAG)
            return null
        }
        val userPreferredDisplayModeId = SettingValue.fromCommandOutput(displayModeResult.output)

        // Capture Vivo global settings
        var vivoRefreshMode: SettingValue? = null
        var gameCubeApps: SettingValue? = null
        var speedModeApps: SettingValue? = null
        var vivoHighRefreshApps: SettingValue? = null
        var vivoScreenRefreshAppsList: SettingValue? = null

        if (isVivo) {
            val vivoRrRes = shizukuManager.executeCommandWithResult("settings get global vivo_screen_refresh_rate_mode")
            val gcAppsRes = shizukuManager.executeCommandWithResult("settings get global game_cube_apps")
            val smAppsRes = shizukuManager.executeCommandWithResult("settings get global speed_mode_apps")
            val hrAppsRes = shizukuManager.executeCommandWithResult("settings get global vivo_high_refresh_rate_apps")
            val srAppsRes = shizukuManager.executeCommandWithResult("settings get global vivo_screen_refresh_rate_apps_list")

            if (vivoRrRes == null || gcAppsRes == null || smAppsRes == null || hrAppsRes == null || srAppsRes == null) {
                FrameXLog.e("IPC failure capturing Vivo OEM global settings, aborting optimization", tag = TAG)
                return null
            }

            vivoRefreshMode = SettingValue.fromCommandOutput(vivoRrRes.output)
            gameCubeApps = SettingValue.fromCommandOutput(gcAppsRes.output)
            speedModeApps = SettingValue.fromCommandOutput(smAppsRes.output)
            vivoHighRefreshApps = SettingValue.fromCommandOutput(hrAppsRes.output)
            vivoScreenRefreshAppsList = SettingValue.fromCommandOutput(srAppsRes.output)
        }

        return GamingOptimizationSnapshot(
            activeGamePackage = packageName,
            activeGameUid = uid,
            timestamp = System.currentTimeMillis(),
            minRefreshRate = minRefresh,
            peakRefreshRate = peakRefresh,
            touchResponseSpeed = touchSpeed,
            userPreferredDisplayModeId = userPreferredDisplayModeId,
            vivoRefreshRateMode = vivoRefreshMode,
            vivoTouchPersist = null,
            gameCubeApps = gameCubeApps,
            speedModeApps = speedModeApps,
            vivoHighRefreshApps = vivoHighRefreshApps,
            vivoScreenRefreshAppsList = vivoScreenRefreshAppsList,
            affectedPackages = emptySet()
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

        val isVivo = deviceDiagnosticManager.isVivoOrIqoo() && settingsRepository.vivoOptEnabled.value

        // 0. RAM Cache Pre-Trimming, ART Heap Compaction & Framework Pinning
        shizukuManager.executeCommand("pm trim-caches 4G")
        shizukuManager.executeCommand("am compact background")
        runCatching { shizukuManager.executeCommand("cmd pinner repin /system/framework/framework.jar") }

        // 1. CPU Priority & Memory Lock
        if (settingsRepository.cpuPriorityLock.value && packageName != null) {
            shizukuManager.executeCommand("cmd activity set-bg-restriction-level --user 0 $packageName unrestricted")
            shizukuManager.executeCommand("am set-standby-bucket --user 0 $packageName active")
        }

        // 2. Network Firewall & Deep Doze Exemption
        if (settingsRepository.networkFirewall.value && uid != null) {
            // Exempt socket restrictions in NetPolicy
            shizukuManager.executeCommand("cmd netpolicy add restrict-background-whitelist $uid")
            // Exempt CPU process throttling in DeviceIdleController
            if (!packageName.isNullOrBlank()) {
                shizukuManager.executeCommand("cmd deviceidle whitelist +$packageName")
            }
            // Force Light/Deep Doze for non-whitelisted background processes
            shizukuManager.executeCommand("cmd deviceidle force-idle")
        }

        // 3. Performance Governor Lock
        if (settingsRepository.fixedPerformanceMode.value) {
            shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled true")
        }

        // 4. Refresh Rate Lock & Display Mode Override
        val maxHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
        var displayModeLockOk = false

        if (settingsRepository.refreshRateLock.value) {
            shizukuManager.executeCommand("settings put system peak_refresh_rate $maxHz")
            shizukuManager.executeCommand("settings put system min_refresh_rate $maxHz")

            if (isVivo) {
                // Lock hardware display to Group 1 Mode 4 (1080x2400 @ 120Hz)
                val modeResult = shizukuManager.executeCommandWithResult("settings put secure user_preferred_display_mode_id 4")
                // Lock Vivo OEM display refresh rate mode
                shizukuManager.executeCommand("settings put global vivo_screen_refresh_rate_mode 1")

                if (!packageName.isNullOrBlank()) {
                    shizukuManager.executeCommand("cmd game set --fps ${maxHz.toInt()} --downscale 0.9 $packageName")
                }
                displayModeLockOk = modeResult?.exitCode == 0
            } else {
                displayModeLockOk = true
            }
        }

        // 5. Touch Response Latency Boost
        var touchBoostOk = false
        if (settingsRepository.touchBoost.value) {
            // Universal Android touch response speed knob
            val touchRes = shizukuManager.executeCommandWithResult("settings put system touch_response_speed 2")
            touchBoostOk = touchRes?.exitCode == 0
            // NOTE: Ghost key 'com.vivo.vtouch.persist' purged to eliminate redundant IPC overhead.
        }

        // 6. Vivo Whitelist Injection (Safe CSV Append)
        var whitelistAppliedOk = false
        if (isVivo) {
            if (!packageName.isNullOrBlank()) {
                var allSucceeded = true
                for (key in vivoWhitelistKeys) {
                    val currentValRes = shizukuManager.executeCommandWithResult("settings get global $key")
                    val rawOutput = currentValRes?.output?.trim().orEmpty()
                    val existingList = if (rawOutput == "null") "" else rawOutput

                    val pkgs = existingList.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                    if (packageName !in pkgs) {
                        pkgs.add(packageName)
                        val newList = pkgs.joinToString(",")
                        val writeRes = shizukuManager.executeCommandWithResult("settings put global $key \"$newList\"")
                        if (writeRes?.exitCode != 0) allSucceeded = false
                    }
                }
                whitelistAppliedOk = allSucceeded
            } else {
                whitelistAppliedOk = true
            }

            _vivoOptimizationResult.value = VivoOptimizationResult(
                displayModeLock = displayModeLockOk,
                maxHzApplied = maxHz.toInt(),
                touchBoost = touchBoostOk,
                whitelistApplied = whitelistAppliedOk
            )
        }

        return true
    }

    suspend fun revertOptimizations() {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return

        recoverThermalOverrideIfNeeded()

        val snapshot = settingsRepository.loadGamingOptimizationSnapshot()
        if (snapshot == null) {
            FrameXLog.w("No snapshot found, performing legacy revert", tag = TAG)
            revertLegacy()
            return
        }

        val pkg = snapshot.activeGamePackage
        val uid = snapshot.activeGameUid

        // Revert per-app overrides
        if (pkg != null) {
            shizukuManager.executeCommand("cmd game reset $pkg")
            if (settingsRepository.cpuPriorityLock.value) {
                shizukuManager.executeCommand("cmd activity set-bg-restriction-level --user 0 $pkg adaptive_bucket")
                shizukuManager.executeCommand("am set-standby-bucket --user 0 $pkg working_set")
            }
        }

        if (uid != null && settingsRepository.networkFirewall.value) {
            shizukuManager.executeCommand("cmd netpolicy remove restrict-background-whitelist $uid")
            if (!pkg.isNullOrBlank()) {
                shizukuManager.executeCommand("cmd deviceidle whitelist -$pkg")
            }
        }
        shizukuManager.executeCommand("cmd deviceidle unforce")
        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")

        // Restore system display & touch settings
        snapshot.minRefreshRate?.let { restoreSetting("system", "min_refresh_rate", it) }
        snapshot.peakRefreshRate?.let { restoreSetting("system", "peak_refresh_rate", it) }
        snapshot.touchResponseSpeed?.let { restoreSetting("system", "touch_response_speed", it) }

        // Restore secure display mode ID (restore to -1 if absent)
        snapshot.userPreferredDisplayModeId?.let { setting ->
            if (setting.existed && setting.value.isNotBlank()) {
                shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id ${setting.value}")
            } else {
                shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id -1")
            }
        }

        // Restore Vivo global settings
        snapshot.vivoRefreshRateMode?.let { restoreSetting("global", "vivo_screen_refresh_rate_mode", it) }

        // Strip pkg from Vivo CSV whitelists or restore captured baseline
        if (pkg != null) {
            stripPackageFromCsv("game_cube_apps", pkg, snapshot.gameCubeApps)
            stripPackageFromCsv("speed_mode_apps", pkg, snapshot.speedModeApps)
            stripPackageFromCsv("vivo_high_refresh_rate_apps", pkg, snapshot.vivoHighRefreshApps)
            stripPackageFromCsv("vivo_screen_refresh_rate_apps_list", pkg, snapshot.vivoScreenRefreshAppsList)
        } else {
            snapshot.gameCubeApps?.let { restoreSetting("global", "game_cube_apps", it) }
            snapshot.speedModeApps?.let { restoreSetting("global", "speed_mode_apps", it) }
            snapshot.vivoHighRefreshApps?.let { restoreSetting("global", "vivo_high_refresh_rate_apps", it) }
            snapshot.vivoScreenRefreshAppsList?.let { restoreSetting("global", "vivo_screen_refresh_rate_apps_list", it) }
        }

        // Clear snapshot only after successful restoration
        settingsRepository.clearGamingOptimizationSnapshot()
        _vivoOptimizationResult.value = null
    }

    private suspend fun restoreSetting(namespace: String, key: String, setting: SettingValue) {
        if (setting.existed && setting.value.isNotBlank()) {
            shizukuManager.executeCommand("settings put $namespace $key ${setting.value}")
        } else {
            shizukuManager.executeCommand("settings delete $namespace $key")
        }
    }

    private suspend fun stripPackageFromCsv(key: String, pkg: String, originalBackup: SettingValue?) {
        val currentValRes = shizukuManager.executeCommandWithResult("settings get global $key")
        val rawOutput = currentValRes?.output?.trim().orEmpty()
        if (rawOutput.isNotBlank() && rawOutput != "null") {
            val pkgs = rawOutput.split(",").map { it.trim() }.filter { it.isNotBlank() && it != pkg }
            if (pkgs.isNotEmpty()) {
                shizukuManager.executeCommand("settings put global $key \"${pkgs.joinToString(",")}\"")
            } else {
                if (originalBackup != null && originalBackup.existed && originalBackup.value.isNotBlank()) {
                    shizukuManager.executeCommand("settings put global $key \"${originalBackup.value}\"")
                } else {
                    shizukuManager.executeCommand("settings delete global $key")
                }
            }
        } else if (originalBackup != null) {
            restoreSetting("global", key, originalBackup)
        }
    }

    private suspend fun revertLegacy() {
        shizukuManager.executeCommand("settings delete system min_refresh_rate")
        shizukuManager.executeCommand("settings delete system peak_refresh_rate")
        shizukuManager.executeCommand("settings put secure user_preferred_display_mode_id -1")
        shizukuManager.executeCommand("settings delete global vivo_screen_refresh_rate_mode")
        shizukuManager.executeCommand("settings delete system touch_response_speed")
        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")
        shizukuManager.executeCommand("cmd deviceidle unforce")
        _vivoOptimizationResult.value = null
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
            "settings delete global vivo_screen_refresh_rate_mode",
            "settings delete system touch_response_speed",
            "settings delete global com.vivo.vtouch.persist",
            "settings delete system com.vivo.vtouch.persist",
            "settings delete system game_screen_resolution_switch",
            "settings delete system gamecube_competition_mode_state",
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

        val resetCommands = listOf(
            "settings delete system min_refresh_rate",
            "settings delete system peak_refresh_rate",
            "settings put secure user_preferred_display_mode_id -1",
            "settings delete global vivo_screen_refresh_rate_mode",
            "settings delete system touch_response_speed",
            "settings delete global game_cube_apps",
            "settings delete global speed_mode_apps",
            "settings delete global vivo_high_refresh_rate_apps",
            "settings delete global vivo_screen_refresh_rate_apps_list",
            "cmd power set-fixed-performance-mode-enabled false",
            "cmd thermalservice reset",
            "cmd deviceidle unforce"
        )

        var successCount = 0
        for (cmd in resetCommands) {
            val result = shizukuManager.executeCommandWithResult(cmd)
            if (result != null && result.exitCode == 0) {
                successCount++
            }
        }

        settingsRepository.markLegacySettingsCleanupComplete()
        settingsRepository.clearGamingOptimizationSnapshot()

        FrameXLog.i("Device defaults reset: $successCount/${resetCommands.size} commands succeeded", tag = TAG)
        return successCount == resetCommands.size
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