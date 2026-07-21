package com.framex.app.gaming

import android.content.Context
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
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
    private var activeGamePackage: String? = null
    private var activeGameUid: Int? = null
    private var initialMinRefreshRate: String? = null
    private var initialTouchSpeed: String? = null

    suspend fun applyOptimizationsForGame(packageName: String, uid: Int) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return

        activeGamePackage = packageName
        activeGameUid = uid
        val isVivo = deviceDiagnosticManager.isVivoOrIqoo() && settingsRepository.vivoOptEnabled.value

        // 0. RAM Cache Pre-Trimming
        shizukuManager.executeCommand("pm trim-caches 4G")

        // 1. CPU Priority & Memory Lock (Android 16 Verified)
        if (settingsRepository.cpuPriorityLock.value) {
            shizukuManager.executeCommand("cmd activity set-bg-restriction-level --user 0 $packageName unrestricted")
            shizukuManager.executeCommand("am set-standby-bucket --user 0 $packageName active")
        }

        // 2. Network Firewall & Light Doze
        if (settingsRepository.networkFirewall.value) {
            shizukuManager.executeCommand("cmd netpolicy add restrict-background-whitelist $uid")
            shizukuManager.executeCommand("cmd deviceidle force-idle")
        }

        // 3. Performance Governor Lock
        shizukuManager.executeCommand("cmd power set-fixed-performance-mode-enabled true")
        if (isVivo) {
            shizukuManager.executeCommand("setprop persist.sys.performance.mode 1")
        }

        // 4. Per-App Refresh Rate Lock & Android 16 Game Mode Override
        if (settingsRepository.refreshRateLock.value) {
            val maxHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
            initialMinRefreshRate = shizukuManager.executeCommand("settings get system min_refresh_rate")
            shizukuManager.executeCommand("settings put system peak_refresh_rate $maxHz")
            shizukuManager.executeCommand("settings put system min_refresh_rate $maxHz")
            if (isVivo) {
                shizukuManager.executeCommand("cmd game set --fps ${maxHz.toInt()} $packageName")
                shizukuManager.executeCommand("settings put system vivo_screen_refresh_rate_mode ${maxHz.toInt()}")
            }
        }

        // 5. Touch Response Latency Boost
        if (settingsRepository.touchBoost.value) {
            initialTouchSpeed = shizukuManager.executeCommand("settings get system touch_response_speed")
            shizukuManager.executeCommand("settings put system touch_response_speed 2")
            if (isVivo) {
                shizukuManager.executeCommand("settings put system com.vivo.vtouch.persist 1")
                shizukuManager.executeCommand("setprop persist.sys.touch.response 2")
            }
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

        activeGamePackage = null
        activeGameUid = null
    }

    fun calculateFramePacingDeltaMs(actualFps: Int): Float {
        if (actualFps <= 0) return 0f
        val activeHz = deviceDiagnosticManager.getMaxHardwareRefreshRate()
        val targetFrameTimeMs = 1000f / activeHz
        val actualFrameTimeMs = 1000f / actualFps.toFloat()
        return abs(actualFrameTimeMs - targetFrameTimeMs)
    }
}
