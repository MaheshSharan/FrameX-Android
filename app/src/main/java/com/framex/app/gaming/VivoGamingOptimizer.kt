package com.framex.app.gaming

import android.content.Context
import android.widget.Toast
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated optimization suite for Vivo & iQOO devices (FuntouchOS / OriginOS 14, 15, 16).
 *
 * Implements hardware-verified settings injections per vivo_final.md:
 * - 27 On-Activation commands (Power, Thermal, GameSpace, Gyro, Touch, Kernel)
 * - 3 Periodic maintenance commands (2-minute loop against GameWatch / GameCube resets)
 * - Manual performance utilities (AOT speed compilation, MEMC toggle, game whitelist)
 *
 * Adheres strictly to blacklist: never writes to resolution switches, peak_refresh_rate,
 * or memory-factor to prevent 60Hz LTPO/RMS lockouts.
 */
@Singleton
open class VivoGamingOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: com.framex.app.repository.SettingsRepository,
    private val ledgerExecutor: com.framex.app.gaming.ledger.LedgerExecutor,
    private val auditLogRepository: SystemAuditLogRepository,
    private val vivoSuiteGate: VivoSuiteGate,
    private val deviceDiagnosticManager: DeviceDiagnosticManager
) {

    private val maxHardwareRefreshRate: Int
        get() = deviceDiagnosticManager.getMaxHardwareRefreshRate().toInt().coerceAtLeast(60)

    private var activeGamePackage: String? = null
    private var activeGamePid: Int = 0

    val auditLogs: StateFlow<List<SystemAuditLog>> = auditLogRepository.logs

    private fun addLog(action: String, details: String, status: LogStatus) {
        auditLogRepository.addLog(action, details, status)
    }

    fun clearAuditLogs() {
        auditLogRepository.clear()
    }

    // Captured baseline snapshot to ensure safe rollback
    private var baselinePowerModeType: String? = null
    private var baselineBbbPerfMode: String? = null
    private var baselinePowerSaveTypeSys: String? = null
    private var baselinePowerSaveTypeSec: String? = null
    private var baselinePowerSaveAutoExit: String? = null
    private var baselinePowerSleepMode: String? = null
    private var baselineLowPowerMode: String? = null
    private var baselineVivoConsoleStatus: String? = null
    private var baselineGameOptimizeBrightness: String? = null
    private var baselineGameCubeTemperControl: String? = null
    private var baselineVtsGameParaAdjust: String? = null
    private var baselineTouchSmooth: String? = null
    private var baselineMemcTouchRate: String? = null
    private var baselineGameCubeVipThread: String? = null
    private var baselineMonitorPhantomProcs: String? = null

    // =========================================================================
    // Activation Sequence
    // =========================================================================

    suspend fun applyOptimizations(
        packageName: String?,
        pid: Int = 0,
        onProgress: (suspend (Float, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
            FrameXLog.w("Shizuku unavailable, skipping Vivo gaming optimizations", tag = TAG)
            addLog("Gaming Mode Activation", "Shizuku not ready or permission missing", LogStatus.FAILED)
            return@withContext false
        }

        activeGamePackage = packageName
        activeGamePid = pid

        FrameXLog.i("Applying Vivo & iQOO hardware optimizations (pkg=$packageName, pid=$pid)...", tag = TAG)

        captureBaselineSnapshot()

        val hasAnySpecificOptimization = settingsRepository.vivoMonsterMode.value ||
            settingsRepository.disableThermalThrottling.value ||
            (settingsRepository.vivoGameHandshake.value && packageName != null && pid > 0) ||
            settingsRepository.vivoGyroPromotion.value ||
            settingsRepository.vivoTouchOptimization.value ||
            settingsRepository.vivoVipThread.value

        if (!hasAnySpecificOptimization) {
            onProgress?.invoke(0.70f, "Applying Baseline Optimizations…")
        }

        if (settingsRepository.vivoMonsterMode.value) {
            onProgress?.invoke(0.60f, "Enforcing Monster Mode (5)…")
        }
        executePowerAndThermalPayload()

        if (settingsRepository.disableThermalThrottling.value) {
            onProgress?.invoke(0.68f, "Configuring Display & Thermals…")
        }
        executeDisplayAndGameSpacePayload()

        if (settingsRepository.vivoGameHandshake.value && packageName != null && pid > 0) {
            val targetFps = maxHardwareRefreshRate
            onProgress?.invoke(0.76f, "Initializing Game Handshake (${targetFps} FPS)…")
        }
        executeLiveHandshakePayload(packageName, pid)

        if (settingsRepository.vivoGyroPromotion.value) {
            onProgress?.invoke(0.84f, "Activating Hardware Gyroscope…")
        }
        executeHardwareGyroPayload(packageName)

        if (settingsRepository.vivoTouchOptimization.value) {
            onProgress?.invoke(0.90f, "Tuning Touch Response…")
        }
        executeTouchDigitizerPayload(packageName)

        if (settingsRepository.vivoVipThread.value) {
            onProgress?.invoke(0.96f, "Assigning VIP Scheduler…")
        }
        executeKernelSchedulerPayload(packageName)

        FrameXLog.i("Vivo & iQOO optimization sequence applied successfully", tag = TAG)
        addLog(
            "Gaming Mode Activation",
            "Hardware gaming optimizations applied" + if (packageName != null) " (Target: $packageName, PID: $pid)" else "",
            LogStatus.SUCCESS
        )
        true
    }

    private suspend fun captureBaselineSnapshot() {
        baselinePowerModeType = querySetting("secure", "system_property_power_mode_type")
        baselineBbbPerfMode = querySetting("global", "bbb_perf_mode")
        baselinePowerSaveTypeSys = querySetting("system", "power_save_type")
        baselinePowerSaveTypeSec = querySetting("secure", "power_save_type")
        baselinePowerSaveAutoExit = querySetting("system", "power_save_auto_exit")
        baselinePowerSleepMode = querySetting("system", "power_sleep_mode_enabled")
        baselineLowPowerMode = querySetting("global", "low_power_mode_opened")
        baselineVivoConsoleStatus = querySetting("system", "com.vivo.vivoconsole.icon.status")
        baselineGameOptimizeBrightness = querySetting("system", "game_optimize_brightness")
        baselineGameCubeTemperControl = querySetting("secure", "game_cube_temper_control")
        baselineVtsGameParaAdjust = querySetting("system", "vts_game_para_adjust")
        baselineTouchSmooth = querySetting("system", "touch_smooth")
        baselineMemcTouchRate = querySetting("global", "game_memc_request_touch_rate")
        baselineGameCubeVipThread = querySetting("global", "game_cube_vip_thread")
        baselineMonitorPhantomProcs = querySetting("global", "settings_enable_monitor_phantom_procs")
    }

    private suspend fun executePowerAndThermalPayload() {
        val specs = listOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/secure --bind name:s:system_property_power_mode_type --bind value:s:5", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/global --bind name:s:bbb_perf_mode --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:power_save_type --bind value:s:5", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/secure --bind name:s:power_save_type --bind value:s:5", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:power_save_auto_exit --bind value:s:0", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:power_sleep_mode_enabled --bind value:s:0", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/global --bind name:s:low_power_mode_opened --bind value:s:0", com.framex.app.gaming.ledger.OpPriority.DETAIL)
        )
        if (settingsRepository.vivoMonsterMode.value) {
            ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.POWER, specs)
        } else {
            ledgerExecutor.recordSkipped(com.framex.app.gaming.ledger.Stage.POWER, specs)
        }
    }

    private suspend fun executeDisplayAndGameSpacePayload() {
        val specs = mutableListOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:com.vivo.vivoconsole.icon.status --bind value:s:2", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:game_optimize_brightness --bind value:s:0", com.framex.app.gaming.ledger.OpPriority.DETAIL)
        )
        if (settingsRepository.disableThermalThrottling.value) {
            specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/secure --bind name:s:game_cube_temper_control --bind value:s:0", com.framex.app.gaming.ledger.OpPriority.PRIMARY))
        }
        ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.DISPLAY, specs)
    }

    private suspend fun executeLiveHandshakePayload(packageName: String?, pid: Int) {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName)
        val targetPkg = safePkg ?: "com.vivo.game"
        val specs = listOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:sdk_game_target_fps --bind value:s:\"${targetPkg}_${pid}_$maxHardwareRefreshRate\"", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:sdk_game_scene --bind value:s:\"${targetPkg}_${pid}_0\"", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/secure --bind name:s:sdk_game_scene --bind value:s:\"${targetPkg}_${pid}_0\"", com.framex.app.gaming.ledger.OpPriority.DETAIL)
        )
        if (settingsRepository.vivoGameHandshake.value && safePkg != null && pid > 0) {
            ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.HANDSHAKE, specs)
        } else {
            ledgerExecutor.recordSkipped(com.framex.app.gaming.ledger.Stage.HANDSHAKE, specs)
        }
    }

    private suspend fun executeHardwareGyroPayload(packageName: String?) {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName)
        val targetPkg = safePkg ?: "com.vivo.game"
        val specs = listOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_gyro_promotion --bind value:s:\"1@$targetPkg\"", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_gyro_dealy_promotion --bind value:s:\"$targetPkg#2\"", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_gyro_anti_shake_promotion --bind value:s:\"$targetPkg#1\"", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_gyro_data_prediction --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.DETAIL)
        )
        if (settingsRepository.vivoGyroPromotion.value && safePkg != null) {
            ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.GYRO, specs)
        } else {
            ledgerExecutor.recordSkipped(com.framex.app.gaming.ledger.Stage.GYRO, specs)
        }
    }

    private suspend fun executeTouchDigitizerPayload(packageName: String?) {
        val specs = mutableListOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vts_game_para_adjust --bind value:s:\"1,5,5,5\"", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:touch_smooth --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/global --bind name:s:game_memc_request_touch_rate --bind value:s:180", com.framex.app.gaming.ledger.OpPriority.PRIMARY)
        )
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName)
        if (safePkg != null) {
            specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_click_delay_promotion --bind value:s:\"$safePkg#2\"", com.framex.app.gaming.ledger.OpPriority.DETAIL))
            specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_touch_delay_promotion --bind value:s:\"$safePkg#2\"", com.framex.app.gaming.ledger.OpPriority.DETAIL))
        } else {
            val skippedPerGame = listOf(
                com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_click_delay_promotion --bind value:s:\"none#2\"", com.framex.app.gaming.ledger.OpPriority.DETAIL),
                com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_touch_delay_promotion --bind value:s:\"none#2\"", com.framex.app.gaming.ledger.OpPriority.DETAIL)
            )
            ledgerExecutor.recordSkipped(com.framex.app.gaming.ledger.Stage.TOUCH, skippedPerGame)
        }
        if (settingsRepository.vivoTouchOptimization.value) {
            ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.TOUCH, specs)
        } else {
            ledgerExecutor.recordSkipped(com.framex.app.gaming.ledger.Stage.TOUCH, specs)
        }
    }

    private suspend fun executeKernelSchedulerPayload(packageName: String?) {
        val specs = mutableListOf<com.framex.app.gaming.ledger.CommandSpec>()
        if (settingsRepository.vivoVipThread.value) {
            specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/global --bind name:s:game_cube_vip_thread --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.PRIMARY))
        }
        if (settingsRepository.disablePhantomProcKiller.value) {
            specs.add(com.framex.app.gaming.ledger.CommandSpec("cmd device_config put activity_manager max_phantom_processes 2147483647", com.framex.app.gaming.ledger.OpPriority.DETAIL))
            specs.add(com.framex.app.gaming.ledger.CommandSpec("settings put global settings_enable_monitor_phantom_procs false", com.framex.app.gaming.ledger.OpPriority.DETAIL))
        }
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName)
        if (safePkg != null) {
            if (settingsRepository.cpuPriorityLock.value) {
                specs.add(com.framex.app.gaming.ledger.CommandSpec("cmd activity set-bg-restriction-level --user 0 $safePkg unrestricted", com.framex.app.gaming.ledger.OpPriority.DETAIL))
            }
            if (settingsRepository.vivoVipThread.value) {
                specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/global --bind name:s:speed_mode_apps --bind value:s:$safePkg", com.framex.app.gaming.ledger.OpPriority.DETAIL))
            }
        }
        if (specs.isNotEmpty()) {
            ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.KERNEL, specs)
        }
    }

    // =========================================================================
    // Dynamic PID Promotion & Maintenance Loop
    // =========================================================================

    suspend fun promoteGamePid(packageName: String, pid: Int) = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext
        activeGamePackage = packageName
        activeGamePid = pid
        FrameXLog.i("Promoting live PID for Vivo game handshake: $packageName (PID=$pid)", tag = TAG)
        executeLiveHandshakePayload(packageName, pid)
    }

    suspend fun runPeriodicMaintenance() = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return@withContext
        if (!settingsRepository.vivoMaintenancePulse.value) {
            FrameXLog.d("Periodic maintenance pulse disabled by user setting", tag = TAG)
            return@withContext
        }
        FrameXLog.d("Executing 2-minute Vivo gaming maintenance pulse...", tag = TAG)

        val specs = mutableListOf<com.framex.app.gaming.ledger.CommandSpec>()
        if (settingsRepository.vivoPulseGamePlusMode.value) {
            specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:game_plus_mode_key --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.PRIMARY))
        }
        if (settingsRepository.vivoPulseStandardPromotion.value) {
            specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:game_standard_promotion_mode --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.DETAIL))
        }
        if (settingsRepository.vivoPulseSceneMoreFps.value) {
            specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:game_scene_more_fps --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.PRIMARY))
        }
        val allPassed = if (specs.isNotEmpty()) {
            ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.POWER, specs)
        } else {
            true
        }

        activeGamePackage?.let { pkg ->
            if (activeGamePid <= 0) {
                val resolvedPid = queryProcessPid(pkg)
                if (resolvedPid > 0) {
                    promoteGamePid(pkg, resolvedPid)
                }
            }
        }

        val pidStatus = if (activeGamePid > 0) "PID $activeGamePid ✓" else "Standby"
        addLog(
            "2-Min Maintenance Pulse",
            "Monster Flags (plus/mode/fps) re-asserted | Game: ${activeGamePackage ?: "None"} ($pidStatus)",
            if (allPassed) LogStatus.SUCCESS else LogStatus.FAILED
        )

        // Only show debug toast on screen if audit logging is enabled
        if (settingsRepository.auditLoggingEnabled.value) {
            withContext(Dispatchers.Main) {
                val msg = if (allPassed) {
                    "FrameX Pulse: Monster Flags (plus/mode/fps) Re-asserted ✓ | $pidStatus"
                } else {
                    "FrameX Pulse: Monster Flags Re-assert Failed"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =========================================================================
    // Teardown & Rollback
    // =========================================================================

    open suspend fun revertOptimizations(): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
            addLog("Gaming Mode Deactivation", "Shizuku not ready or permission missing", LogStatus.FAILED)
            return@withContext false
        }
        FrameXLog.i("Reverting Vivo & iQOO gaming optimizations to baseline...", tag = TAG)

        val revertCmds = mutableListOf<String>()

        // 1. Power & Monster Mode (Ensure power mode 0 and Monster icon reset)
        revertCmds.add("content insert --uri content://settings/secure --bind name:s:system_property_power_mode_type --bind value:s:${baselinePowerModeType ?: "0"}")
        revertCmds.add("content insert --uri content://settings/global --bind name:s:bbb_perf_mode --bind value:s:${baselineBbbPerfMode ?: "0"}")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:power_save_type --bind value:s:${baselinePowerSaveTypeSys ?: "0"}")
        revertCmds.add("content insert --uri content://settings/secure --bind name:s:power_save_type --bind value:s:${baselinePowerSaveTypeSec ?: "0"}")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:power_save_auto_exit --bind value:s:${baselinePowerSaveAutoExit ?: "1"}")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:power_sleep_mode_enabled --bind value:s:${baselinePowerSleepMode ?: "0"}")
        revertCmds.add("content insert --uri content://settings/global --bind name:s:low_power_mode_opened --bind value:s:${baselineLowPowerMode ?: "0"}")

        // 2. Display, Brightness & Monster Mode / VivoConsole Icon (Status 0 hides/resets monster mode indicator)
        revertCmds.add("content insert --uri content://settings/system --bind name:s:com.vivo.vivoconsole.icon.status --bind value:s:${baselineVivoConsoleStatus ?: "0"}")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:game_optimize_brightness --bind value:s:${baselineGameOptimizeBrightness ?: "1"}")
        revertCmds.add("content insert --uri content://settings/secure --bind name:s:game_cube_temper_control --bind value:s:${baselineGameCubeTemperControl ?: "1"}")

        // 3. Touch Digitizer & Delays
        baselineVtsGameParaAdjust?.let {
            revertCmds.add("content insert --uri content://settings/system --bind name:s:vts_game_para_adjust --bind value:s:\"$it\"")
        }
        revertCmds.add("content insert --uri content://settings/system --bind name:s:touch_smooth --bind value:s:${baselineTouchSmooth ?: "0"}")
        revertCmds.add("content insert --uri content://settings/global --bind name:s:game_memc_request_touch_rate --bind value:s:${baselineMemcTouchRate ?: "60"}")

        // 4. Hardware Gyroscope Suite
        revertCmds.add("content insert --uri content://settings/system --bind name:s:vivo_game_gyro_data_prediction --bind value:s:0")

        // 5. Kernel, VIP Threads & Phantoms
        revertCmds.add("content insert --uri content://settings/global --bind name:s:game_cube_vip_thread --bind value:s:${baselineGameCubeVipThread ?: "0"}")
        revertCmds.add("settings put global settings_enable_monitor_phantom_procs ${baselineMonitorPhantomProcs ?: "true"}")

        // 6. Periodic Maintenance Flags
        revertCmds.add("content insert --uri content://settings/system --bind name:s:game_plus_mode_key --bind value:s:0")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:game_standard_promotion_mode --bind value:s:0")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:game_scene_more_fps --bind value:s:0")

        // 7. Background Restriction
        activeGamePackage?.let { pkg ->
            revertCmds.add("cmd activity set-bg-restriction-level --user 0 $pkg adaptive")
        }

        // 8. O(1) Batched Deletes across System, Secure, and Global tables (replaces 10 individual JVM spawns with 3)
        revertCmds.add("content delete --uri content://settings/system --where \"name IN ('vivo_game_click_delay_promotion','vivo_game_touch_delay_promotion','vivo_game_gyro_promotion','vivo_game_gyro_dealy_promotion','vivo_game_gyro_anti_shake_promotion','sdk_game_target_fps','sdk_game_scene')\"")
        revertCmds.add("content delete --uri content://settings/secure --where \"name = 'sdk_game_scene'\"")
        revertCmds.add("content delete --uri content://settings/global --where \"name = 'speed_mode_apps'\"")

        val exitCode = shizukuManager.executeCommandWithExitCode(revertCmds.joinToString("; "))
        val success = (exitCode == 0)

        activeGamePackage = null
        activeGamePid = 0
        FrameXLog.i("Vivo & iQOO gaming optimizations reverted successfully", tag = TAG)
        addLog(
            "Gaming Mode Deactivation",
            "Monster mode reset, VivoConsole icon 0, touch/gyro cleared & settings restored to baseline",
            if (success) LogStatus.SUCCESS else LogStatus.FAILED
        )
        success
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private suspend fun querySetting(table: String, key: String): String? {
        val res = shizukuManager.executeCommandWithResult("settings get $table $key")
        val out = res?.output?.trim().orEmpty()
        return if (out.isBlank() || out == "null") null else out
    }

    private suspend fun queryProcessPid(packageName: String): Int {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName) ?: return 0
        val res = shizukuManager.executeCommandWithResult("pidof $safePkg")
        val out = res?.output?.trim().orEmpty()
        return out.split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: 0
    }

    companion object {
        private const val TAG = "VivoGamingOptimizer"
    }
}
