package com.framex.app.gaming

import android.content.Context
import android.widget.Toast
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
class VivoGamingOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: com.framex.app.repository.SettingsRepository,
    private val ledgerExecutor: com.framex.app.gaming.ledger.LedgerExecutor,
    private val auditLogRepository: SystemAuditLogRepository
) {

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

        onProgress?.invoke(0.60f, "Enforcing Monster Mode (5) & Governor…")
        captureBaselineSnapshot()
        executePowerAndThermalPayload()

        onProgress?.invoke(0.68f, "Disabling Thermal Auto-Exit & Brightness Dimming…")
        executeDisplayAndGameSpacePayload()

        onProgress?.invoke(0.76f, "Initializing Game Handshake & 120 FPS Target…")
        executeLiveHandshakePayload(packageName, pid)

        onProgress?.invoke(0.84f, "Activating Hardware Gyroscope & Anti-Shake…")
        executeHardwareGyroPayload(packageName)

        onProgress?.invoke(0.90f, "Tuning Touch Response (1,5,5,5) & Trajectory Smooth…")
        executeTouchDigitizerPayload(packageName)

        onProgress?.invoke(0.96f, "Assigning Unrestricted Top-App Cgroup & VIP Sched…")
        executeKernelSchedulerPayload(packageName)

        FrameXLog.i("Vivo & iQOO optimization sequence applied successfully", tag = TAG)
        addLog(
            "Gaming Mode Activation",
            "Monster Mode (5), Display/Thermal lock, VIP Cgroup & 27 settings applied" + if (packageName != null) " (Target: $packageName, PID: $pid)" else "",
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
        ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.POWER, specs)
    }

    private suspend fun executeDisplayAndGameSpacePayload() {
        val specs = listOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:com.vivo.vivoconsole.icon.status --bind value:s:2", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:game_optimize_brightness --bind value:s:0", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/secure --bind name:s:game_cube_temper_control --bind value:s:0", com.framex.app.gaming.ledger.OpPriority.PRIMARY)
        )
        ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.DISPLAY, specs)
    }

    private suspend fun executeLiveHandshakePayload(packageName: String?, pid: Int) {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName)
        val targetPkg = safePkg ?: "com.vivo.game"
        val specs = listOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:sdk_game_target_fps --bind value:s:\"${targetPkg}_${pid}_120\"", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:sdk_game_scene --bind value:s:\"${targetPkg}_${pid}_0\"", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/secure --bind name:s:sdk_game_scene --bind value:s:\"${targetPkg}_${pid}_0\"", com.framex.app.gaming.ledger.OpPriority.DETAIL)
        )
        if (safePkg != null && pid > 0) {
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
        if (safePkg != null) {
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
        ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.TOUCH, specs)
    }

    private suspend fun executeKernelSchedulerPayload(packageName: String?) {
        val specs = mutableListOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/global --bind name:s:game_cube_vip_thread --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("cmd device_config put activity_manager max_phantom_processes 2147483647", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("settings put global settings_enable_monitor_phantom_procs false", com.framex.app.gaming.ledger.OpPriority.DETAIL)
        )
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName)
        if (safePkg != null) {
            specs.add(com.framex.app.gaming.ledger.CommandSpec("cmd activity set-bg-restriction-level --user 0 $safePkg unrestricted", com.framex.app.gaming.ledger.OpPriority.DETAIL))
            specs.add(com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/global --bind name:s:speed_mode_apps --bind value:s:$safePkg", com.framex.app.gaming.ledger.OpPriority.DETAIL))
        }
        ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.KERNEL, specs)
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
        FrameXLog.d("Executing 2-minute Vivo gaming maintenance pulse...", tag = TAG)

        val specs = listOf(
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:game_plus_mode_key --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.PRIMARY),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:game_standard_promotion_mode --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.DETAIL),
            com.framex.app.gaming.ledger.CommandSpec("content insert --uri content://settings/system --bind name:s:game_scene_more_fps --bind value:s:1", com.framex.app.gaming.ledger.OpPriority.PRIMARY)
        )
        val allPassed = ledgerExecutor.executeBatch(com.framex.app.gaming.ledger.Stage.POWER, specs)

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

    suspend fun revertOptimizations(): Boolean = withContext(Dispatchers.IO) {
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
        revertCmds.add("content delete --uri content://settings/system/vivo_game_click_delay_promotion")
        revertCmds.add("content delete --uri content://settings/system/vivo_game_touch_delay_promotion")

        // 4. Hardware Gyroscope Suite
        revertCmds.add("content delete --uri content://settings/system/vivo_game_gyro_promotion")
        revertCmds.add("content delete --uri content://settings/system/vivo_game_gyro_dealy_promotion")
        revertCmds.add("content delete --uri content://settings/system/vivo_game_gyro_anti_shake_promotion")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:vivo_game_gyro_data_prediction --bind value:s:0")

        // 5. Live Handshake (Target FPS & Scene)
        revertCmds.add("content delete --uri content://settings/system/sdk_game_target_fps")
        revertCmds.add("content delete --uri content://settings/system/sdk_game_scene")
        revertCmds.add("content delete --uri content://settings/secure/sdk_game_scene")

        // 6. Kernel, VIP Threads & Phantoms
        revertCmds.add("content insert --uri content://settings/global --bind name:s:game_cube_vip_thread --bind value:s:${baselineGameCubeVipThread ?: "0"}")
        revertCmds.add("settings put global settings_enable_monitor_phantom_procs ${baselineMonitorPhantomProcs ?: "true"}")
        revertCmds.add("content delete --uri content://settings/global/speed_mode_apps")

        // 7. Periodic Maintenance Flags
        revertCmds.add("content insert --uri content://settings/system --bind name:s:game_plus_mode_key --bind value:s:0")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:game_standard_promotion_mode --bind value:s:0")
        revertCmds.add("content insert --uri content://settings/system --bind name:s:game_scene_more_fps --bind value:s:0")

        // 8. Background Restriction
        activeGamePackage?.let { pkg ->
            revertCmds.add("cmd activity set-bg-restriction-level --user 0 $pkg adaptive")
        }

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
    // Manual Performance Utilities (vivo_final.md Section 4)
    // =========================================================================

    suspend fun injectPerfGameList(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val existingRaw = queryPerfGameListDirect()
        val listTokens = existingRaw.split(":").map { it.trim() }.filter { it.isNotBlank() }
        val updatedTokens = if (safePkg in listTokens) listTokens else listTokens + safePkg
        val updatedList = updatedTokens.joinToString(":", postfix = ":")
        // \\: in the runtime string → sh sees \: → content CLI receives : as literal separator
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

    suspend fun getPerfGameList(): List<String> = withContext(Dispatchers.IO) {
        queryPerfGameListDirect().split(":").map { it.trim() }.filter { it.isNotBlank() }
    }

    suspend fun getRawPerfGameList(): String = withContext(Dispatchers.IO) {
        queryPerfGameListDirect()
    }

    suspend fun removePerfGame(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val existingRaw = queryPerfGameListDirect()
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

    /**
     * Reads perf_game_list directly from the SQLite settings database via content query.
     * Unlike `settings get system perf_game_list`, this bypasses OriginOS/FuntouchOS API
     * filtering and returns the actual stored value.
     */
    private suspend fun queryPerfGameListDirect(): String {
        val result = shizukuManager.executeCommandWithResult(
            "content query --uri content://settings/system/perf_game_list"
        )
        val output = result?.output?.trim().orEmpty()
        // Output format: "Row: 0 _id=90034, name=perf_game_list, value=pkg1:pkg2:, ..."
        val valueMatch = Regex("value=([^,\\n]*)").find(output)
        val raw = valueMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
        FrameXLog.d("queryPerfGameListDirect: raw=$raw", tag = TAG)
        return raw
    }

    suspend fun getPackageCompileFilter(packageName: String): String = withContext(Dispatchers.IO) {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext "unknown"
        val dumpRes = shizukuManager.executeCommandWithResult("dumpsys package $safePkg | grep filter=")
        val line = dumpRes?.output?.lines()?.firstOrNull { it.contains("filter=") }?.trim().orEmpty()
        val match = Regex("filter=\\[?([a-zA-Z0-9_-]+)\\]?").find(line)
        match?.groupValues?.getOrNull(1) ?: if (line.isNotBlank()) line else "unknown"
    }

    suspend fun compileSpeedAot(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
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
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val dumpRes = shizukuManager.executeCommandWithResult("dumpsys package $safePkg | grep filter=")
        dumpRes?.output?.contains("filter=[speed]", ignoreCase = true) == true
    }

    suspend fun setMemcTargetFps(packageName: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val safePkg = com.framex.app.utils.ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext false
        val value = if (enabled) "\"${safePkg}_0_120\"" else "\"\""
        val payload = listOf(
            "content insert --uri content://settings/global --bind name:s:cached_memc_sdk_game_target_fps --bind value:s:$value",
            "content insert --uri content://settings/system --bind name:s:cached_memc_sdk_game_target_fps --bind value:s:$value"
        ).joinToString("; ")
        shizukuManager.executeCommandWithExitCode(payload) == 0
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
