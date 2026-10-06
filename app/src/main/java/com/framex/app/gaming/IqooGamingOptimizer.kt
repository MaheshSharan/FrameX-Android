package com.framex.app.gaming

import android.content.Context
import com.framex.app.gaming.ledger.CommandSpec
import com.framex.app.gaming.ledger.LedgerExecutor
import com.framex.app.gaming.ledger.OpPriority
import com.framex.app.gaming.ledger.Stage
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.utils.ShellSanitizer

@Singleton
open class IqooGamingOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val ledgerExecutor: LedgerExecutor,
    private val auditLogRepository: SystemAuditLogRepository,
    private val deviceDiagnosticManager: DeviceDiagnosticManager
) {
    companion object {
        private const val TAG = "IqooGamingOptimizer"
    }

    private val maxHardwareRefreshRate: Int
        get() = deviceDiagnosticManager.getMaxHardwareRefreshRate().toInt().coerceAtLeast(60)

    private var activeGamePackage: String? = null
    private var activeGamePid: Int = 0

    private var baselineGameCubeFrameInterpolation: String? = null
    private var baselineGameOptimizeBrightness: String? = null
    private var baselineGameCubeTemperControl: String? = null
    private var baselineVtsGameParaAdjust: String? = null
    private var baselineMemcTouchRate: String? = null
    private var baselineGyroDataPrediction: String? = null
    private var baselineGameCubeVipThread: String? = null
    private var baselineGameSceneMoreFps: String? = null

    suspend fun applyOptimizations(
        packageName: String?,
        pid: Int = 0,
        onProgress: (suspend (Float, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
            auditLogRepository.addLog("Gaming Mode Activation", "Shizuku not ready or permission missing", LogStatus.FAILED)
            return@withContext false
        }

        activeGamePackage = packageName
        activeGamePid = pid

        captureBaselines()
        onProgress?.invoke(0.70f, "Applying iQOO Hardware Suite…")

        // 1. Display & Thermals
        val displaySpecs = mutableListOf<CommandSpec>()
        if (settingsRepository.disableThermalThrottling.value) {
            displaySpecs.add(CommandSpec("content insert --uri content://settings/system --bind name:s:game_optimize_brightness --bind value:s:0", OpPriority.PRIMARY))
            displaySpecs.add(CommandSpec("content insert --uri content://settings/secure --bind name:s:game_cube_temper_control --bind value:s:0", OpPriority.PRIMARY))
        }
        if (settingsRepository.vivo144FpsUnlock.value) {
            displaySpecs.add(CommandSpec("content insert --uri content://settings/system --bind name:s:gamecube_frame_interpolation_for_sr --bind value:s:\"1:1::72:144\"", OpPriority.PRIMARY))
        }
        if (displaySpecs.isNotEmpty()) {
            ledgerExecutor.executeBatch(Stage.DISPLAY, displaySpecs)
        }

        // 2. Live Target FPS Handshake
        executeLiveHandshakePayload(packageName, pid)

        // 3. Touch Digitizer
        if (settingsRepository.vivoTouchOptimization.value) {
            val touchSpecs = listOf(
                CommandSpec("content insert --uri content://settings/system --bind name:s:vts_game_para_adjust --bind value:s:\"1,5,5,5\"", OpPriority.PRIMARY),
                CommandSpec("content insert --uri content://settings/global --bind name:s:game_memc_request_touch_rate --bind value:s:180", OpPriority.PRIMARY)
            )
            ledgerExecutor.executeBatch(Stage.TOUCH, touchSpecs)
        }

        // 4. Hardware Gyroscope
        if (settingsRepository.vivoGyroPromotion.value) {
            val gyroSpecs = listOf(
                CommandSpec("content insert --uri content://settings/system --bind name:s:vivo_game_gyro_data_prediction --bind value:s:1", OpPriority.PRIMARY)
            )
            ledgerExecutor.executeBatch(Stage.GYRO, gyroSpecs)
        }

        // 5. Kernel VIP Scheduler
        if (settingsRepository.vivoVipThread.value) {
            val vipSpecs = listOf(
                CommandSpec("content insert --uri content://settings/global --bind name:s:game_cube_vip_thread --bind value:s:1", OpPriority.PRIMARY)
            )
            ledgerExecutor.executeBatch(Stage.KERNEL, vipSpecs)
        }

        auditLogRepository.addLog("Gaming Mode Activation", "iQOO optimizations applied" + if (packageName != null) " (Target: $packageName, PID: $pid)" else "", LogStatus.SUCCESS)
        FrameXLog.i("iQOO optimizations applied successfully", tag = TAG)
        true
    }

    private suspend fun executeLiveHandshakePayload(packageName: String?, pid: Int) {
        val safePkg = ShellSanitizer.sanitizePackageName(packageName)
        val targetPkg = safePkg ?: "com.vivo.game"
        val specs = listOf(
            CommandSpec("content insert --uri content://settings/system --bind name:s:sdk_game_target_fps --bind value:s:\"${targetPkg}_${pid}_$maxHardwareRefreshRate\"", OpPriority.PRIMARY),
            CommandSpec("content insert --uri content://settings/system --bind name:s:sdk_game_scene --bind value:s:\"${targetPkg}_${pid}_0\"", OpPriority.PRIMARY),
            CommandSpec("content insert --uri content://settings/secure --bind name:s:sdk_game_scene --bind value:s:\"${targetPkg}_${pid}_0\"", OpPriority.DETAIL)
        )
        if (settingsRepository.vivoGameHandshake.value && safePkg != null && pid > 0) {
            ledgerExecutor.executeBatch(Stage.HANDSHAKE, specs)
        } else {
            ledgerExecutor.recordSkipped(Stage.HANDSHAKE, specs)
        }
    }

    suspend fun promoteGamePid(packageName: String, pid: Int) = withContext(Dispatchers.IO) {
        if (pid <= 0) return@withContext
        activeGamePackage = packageName
        activeGamePid = pid
        FrameXLog.i("Promoting live PID for iQOO game handshake: $packageName (PID=$pid)", tag = TAG)
        executeLiveHandshakePayload(packageName, pid)
    }

    suspend fun runPeriodicMaintenance() = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return@withContext
        if (!settingsRepository.vivoMaintenancePulse.value) return@withContext

        val specs = mutableListOf<CommandSpec>()
        if (settingsRepository.vivoPulseSceneMoreFps.value) {
            specs.add(CommandSpec("content insert --uri content://settings/system --bind name:s:game_scene_more_fps --bind value:s:1", OpPriority.PRIMARY))
        }

        val allPassed = if (specs.isNotEmpty()) {
            ledgerExecutor.executeBatch(Stage.DISPLAY, specs)
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
        auditLogRepository.addLog(
            "2-Min Maintenance Pulse",
            "iQOO pulse applied (game_scene_more_fps) | Game: ${activeGamePackage ?: "None"} ($pidStatus)",
            if (allPassed) LogStatus.SUCCESS else LogStatus.FAILED
        )
    }

    open suspend fun revertOptimizations(): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
            auditLogRepository.addLog("Gaming Mode Deactivation", "Shizuku not ready or permission missing", LogStatus.FAILED)
            return@withContext false
        }

        val commands = mutableListOf<String>()

        baselineGameCubeFrameInterpolation?.let {
            commands.add("content insert --uri content://settings/system --bind name:s:gamecube_frame_interpolation_for_sr --bind value:s:\"$it\"")
        } ?: commands.add("content insert --uri content://settings/system --bind name:s:gamecube_frame_interpolation_for_sr --bind value:s:\"0:-1:0:0:0\"")

        baselineGameOptimizeBrightness?.let {
            commands.add("content insert --uri content://settings/system --bind name:s:game_optimize_brightness --bind value:s:\"$it\"")
        }
        baselineGameCubeTemperControl?.let {
            commands.add("content insert --uri content://settings/secure --bind name:s:game_cube_temper_control --bind value:s:\"$it\"")
        }
        baselineVtsGameParaAdjust?.let {
            commands.add("content insert --uri content://settings/system --bind name:s:vts_game_para_adjust --bind value:s:\"$it\"")
        }
        baselineMemcTouchRate?.let {
            commands.add("content insert --uri content://settings/global --bind name:s:game_memc_request_touch_rate --bind value:s:\"$it\"")
        }
        baselineGyroDataPrediction?.let {
            commands.add("content insert --uri content://settings/system --bind name:s:vivo_game_gyro_data_prediction --bind value:s:\"$it\"")
        }
        baselineGameCubeVipThread?.let {
            commands.add("content insert --uri content://settings/global --bind name:s:game_cube_vip_thread --bind value:s:\"$it\"")
        }
        baselineGameSceneMoreFps?.let {
            commands.add("content insert --uri content://settings/system --bind name:s:game_scene_more_fps --bind value:s:\"$it\"")
        }

        // Clean up transient Live Handshake registers
        commands.add("content delete --uri content://settings/system --where \"name IN ('sdk_game_target_fps','sdk_game_scene')\"")
        commands.add("content delete --uri content://settings/secure --where \"name = 'sdk_game_scene'\"")

        val batchedCmd = commands.joinToString(" ; ")
        val exitCode = shizukuManager.executeCommandWithExitCode(batchedCmd)
        val success = (exitCode == 0)

        activeGamePackage = null
        activeGamePid = 0

        auditLogRepository.addLog("Gaming Mode Deactivation", "iQOO settings restored to baseline", if (success) LogStatus.SUCCESS else LogStatus.FAILED)
        success
    }

    private suspend fun captureBaselines() {
        baselineGameCubeFrameInterpolation = querySetting("system", "gamecube_frame_interpolation_for_sr")
        baselineGameOptimizeBrightness = querySetting("system", "game_optimize_brightness")
        baselineGameCubeTemperControl = querySetting("secure", "game_cube_temper_control")
        baselineVtsGameParaAdjust = querySetting("system", "vts_game_para_adjust")
        baselineMemcTouchRate = querySetting("global", "game_memc_request_touch_rate")
        baselineGyroDataPrediction = querySetting("system", "vivo_game_gyro_data_prediction")
        baselineGameCubeVipThread = querySetting("global", "game_cube_vip_thread")
        baselineGameSceneMoreFps = querySetting("system", "game_scene_more_fps")
    }

    private suspend fun querySetting(table: String, name: String): String? {
        val result = shizukuManager.executeCommandWithResult(
            "content query --uri content://settings/$table --projection value --where \"name='$name'\""
        )
        val line = result?.output?.lines()?.firstOrNull { it.contains("value=") } ?: return null
        return Regex("value=([^,}\\s]+)").find(line)?.groupValues?.get(1)?.trim()
    }

    private suspend fun queryProcessPid(packageName: String): Int {
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return 0
        val res = shizukuManager.executeCommandWithResult("pidof $safePkg")
        val out = res?.output?.trim().orEmpty()
        return out.split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: 0
    }
}
