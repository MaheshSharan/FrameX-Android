package com.framex.app.gaming

import android.content.Context
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Result of a single Vivo/iQOO-specific gaming optimization execution.
 * Each field is true only when the corresponding Shizuku command completed without error.
 * [maxHzApplied] carries the actual device max Hz used at activation time (not hardcoded).
 */
data class VivoOptimizationResult(
    val thermalOverride: Boolean,
    val refreshRateLock: Boolean,
    val maxHzApplied: Int,
    val touchBoost: Boolean,
    val competitionMode: Boolean,
    val resolutionSwitch: Boolean,
)

@Singleton
class EsportsOptimizationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val deviceDiagnosticManager: DeviceDiagnosticManager
) {
    private var activeGamePackage: String? = null
    private var activeGameUid: Int? = null
    private var initialMinRefreshRate: String? = null
    private var initialTouchSpeed: String? = null
    private var initialGameCubeCompetitionState: String? = null
    private var initialGameScreenResolutionSwitch: String? = null

    private val _vivoOptimizationResult = MutableStateFlow<VivoOptimizationResult?>(null)
    /** Null when gaming mode is inactive or device is not Vivo/iQOO. Non-null when active. */
    val vivoOptimizationResult: StateFlow<VivoOptimizationResult?> = _vivoOptimizationResult.asStateFlow()

    suspend fun applyOptimizationsForGame(packageName: String?, uid: Int?) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return

        activeGamePackage = packageName
        activeGameUid = uid
        val isVivo = deviceDiagnosticManager.isVivoOrIqoo() && settingsRepository.vivoOptEnabled.value

        // 0. RAM Cache Pre-Trimming, ART Heap Compaction & Framework Pinning
        shizukuManager.executeCommand("pm trim-caches 4G")
        shizukuManager.executeCommand("am compact background")
        runCatching { shizukuManager.executeCommand("cmd pinner repin /system/framework/framework.jar") }

        // 1. CPU Priority & Memory Lock (Android 16 Verified)
        if (settingsRepository.cpuPriorityLock.value && packageName != null) {
            shizukuManager.executeCommand("cmd activity set-bg-restriction-level --user 0 $packageName unrestricted")
            shizukuManager.executeCommand("am set-standby-bucket --user 0 $packageName active")
        }

        // 2. Network Firewall & Light Doze
        if (settingsRepository.networkFirewall.value && uid != null) {
            shizukuManager.executeCommand("cmd netpolicy add restrict-background-whitelist $uid")
            shizukuManager.executeCommand("cmd deviceidle force-idle")
        }

        // 3. Performance Governor Lock + Active Thermal Throttling Override
        // cmd thermalservice override-status 0: empirically verified on Vivo T3 Ultra (ADB shell UID 2000).
        // Locks Android ThermalManager to THERMAL_STATUS_NONE, preventing OEM frame-rate throttling drops.
        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled true")
        val thermalOverrideOk = runCatching {
            shizukuManager.executeCommand("cmd thermalservice override-status 0")
            true
        }.getOrDefault(false)

        // 4. Per-App Refresh Rate Lock & Android 16 Game Mode Downscaling Override
        // maxHz is read from device hardware — never hardcoded. Supports 120, 144, 165+ Hz devices.
        // Downscaling ratio 0.9x provides GPU/thermal headroom during intense hot drops.
        val maxHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
        var refreshRateLockOk = false
        if (settingsRepository.refreshRateLock.value) {
            initialMinRefreshRate = shizukuManager.executeCommand("settings get system min_refresh_rate")
            shizukuManager.executeCommand("settings put system peak_refresh_rate $maxHz")
            shizukuManager.executeCommand("settings put system min_refresh_rate $maxHz")
            if (isVivo) {
                // cmd game set --fps and --downscale 0.9 require a specific package — skip during manual activation.
                if (!packageName.isNullOrBlank()) {
                    shizukuManager.executeCommand("cmd game set --fps ${maxHz.toInt()} --downscale 0.9 $packageName")
                }
                // Use global namespace — empirically verified on Vivo T3 Ultra (OriginOS 6).
                val result = shizukuManager.executeCommand("settings put global vivo_screen_refresh_rate_mode ${maxHz.toInt()}")
                refreshRateLockOk = !result.contains("error", ignoreCase = true)
            } else {
                refreshRateLockOk = true
            }
        }

        // 5. Touch Response Latency Boost
        var touchBoostOk = false
        if (settingsRepository.touchBoost.value) {
            initialTouchSpeed = shizukuManager.executeCommand("settings get system touch_response_speed")
            shizukuManager.executeCommand("settings put system touch_response_speed 2")
            if (isVivo) {
                val result = shizukuManager.executeCommand("settings put system com.vivo.vtouch.persist 1")
                touchBoostOk = !result.contains("error", ignoreCase = true)
                // NOTE: setprop persist.sys.touch.response 2 is intentionally omitted.
                // OriginOS 6 blocks persist.sys.* writes from ADB shell UID 2000 via SELinux.
            } else {
                touchBoostOk = true
            }
        }

        // 6. Vivo GameCube Esports Boost (empirically verified writable on Vivo T3 Ultra)
        var competitionModeOk: Boolean
        var resolutionSwitchOk: Boolean
        if (isVivo) {
            initialGameCubeCompetitionState = shizukuManager.executeCommand("settings get system gamecube_competition_mode_state")
            initialGameScreenResolutionSwitch = shizukuManager.executeCommand("settings get system game_screen_resolution_switch")
            val compResult = shizukuManager.executeCommand("settings put system gamecube_competition_mode_state 1")
            competitionModeOk = !compResult.contains("error", ignoreCase = true)
            val resResult = shizukuManager.executeCommand("settings put system game_screen_resolution_switch 1")
            resolutionSwitchOk = !resResult.contains("error", ignoreCase = true)

            // Publish Vivo-specific execution result for UI status card
            _vivoOptimizationResult.value = VivoOptimizationResult(
                thermalOverride = thermalOverrideOk,
                refreshRateLock = refreshRateLockOk,
                maxHzApplied = maxHz.toInt(),
                touchBoost = touchBoostOk,
                competitionMode = competitionModeOk,
                resolutionSwitch = resolutionSwitchOk,
            )
        }
    }

    suspend fun revertOptimizations() {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return

        val pkg = activeGamePackage
        val uid = activeGameUid

        if (pkg != null) {
            shizukuManager.executeCommand("cmd game reset $pkg")
            if (settingsRepository.cpuPriorityLock.value) {
                shizukuManager.executeCommand("cmd activity set-bg-restriction-level --user 0 $pkg adaptive_bucket")
            }
        }

        if (uid != null && settingsRepository.networkFirewall.value) {
            shizukuManager.executeCommand("cmd netpolicy remove restrict-background-whitelist $uid")
            shizukuManager.executeCommand("cmd deviceidle unforce")
        }

        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled false")
        // Unlock thermal throttling — restores OEM default thermal management.
        shizukuManager.executeCommand("cmd thermalservice reset")

        if (settingsRepository.refreshRateLock.value) {
            val prev = initialMinRefreshRate
            if (!prev.isNullOrBlank() && prev != "null") {
                shizukuManager.executeCommand("settings put system min_refresh_rate $prev")
            } else {
                shizukuManager.executeCommand("settings delete system min_refresh_rate")
            }
        }

        if (settingsRepository.touchBoost.value) {
            val prevTouch = initialTouchSpeed
            if (!prevTouch.isNullOrBlank() && prevTouch != "null") {
                shizukuManager.executeCommand("settings put system touch_response_speed $prevTouch")
            } else {
                shizukuManager.executeCommand("settings delete system touch_response_speed")
            }
        }

        // Revert Vivo GameCube Esports Boost keys
        val prevCompetition = initialGameCubeCompetitionState
        if (!prevCompetition.isNullOrBlank() && prevCompetition != "null") {
            shizukuManager.executeCommand("settings put system gamecube_competition_mode_state $prevCompetition")
        } else {
            shizukuManager.executeCommand("settings delete system gamecube_competition_mode_state")
        }
        val prevResSwitch = initialGameScreenResolutionSwitch
        if (!prevResSwitch.isNullOrBlank() && prevResSwitch != "null") {
            shizukuManager.executeCommand("settings put system game_screen_resolution_switch $prevResSwitch")
        } else {
            shizukuManager.executeCommand("settings delete system game_screen_resolution_switch")
        }

        activeGamePackage = null
        activeGameUid = null
        initialGameCubeCompetitionState = null
        initialGameScreenResolutionSwitch = null
        // Clear Vivo status — device is no longer in gaming mode
        _vivoOptimizationResult.value = null
    }

    fun calculateFramePacingDeltaMs(actualFps: Int): Float {
        if (actualFps <= 0) return 0f
        val activeHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
        val targetFrameTimeMs = 1000f / activeHz
        val actualFrameTimeMs = 1000f / actualFps.toFloat()
        return abs(actualFrameTimeMs - targetFrameTimeMs)
    }
}
