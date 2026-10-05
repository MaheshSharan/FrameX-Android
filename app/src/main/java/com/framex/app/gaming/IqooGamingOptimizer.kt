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

@Singleton
open class IqooGamingOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val ledgerExecutor: LedgerExecutor,
    private val auditLogRepository: SystemAuditLogRepository
) {
    companion object {
        private const val TAG = "IqooGamingOptimizer"
    }

    private var baselineGameCubeFrameInterpolation: String? = null

    suspend fun applyOptimizations(
        @Suppress("UNUSED_PARAMETER") packageName: String?,
        onProgress: (suspend (Float, String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
            auditLogRepository.addLog("Gaming Mode Activation", "Shizuku not ready or permission missing", LogStatus.FAILED)
            return@withContext false
        }

        baselineGameCubeFrameInterpolation = querySetting("system", "gamecube_frame_interpolation_for_sr")
        onProgress?.invoke(0.70f, "Applying iQOO 144Hz Optimization…")

        if (settingsRepository.vivo144FpsUnlock.value) {
            val displaySpecs = listOf(
                CommandSpec(
                    "content insert --uri content://settings/system --bind name:s:gamecube_frame_interpolation_for_sr --bind value:s:\"1:1::72:144\"",
                    OpPriority.PRIMARY
                )
            )
            ledgerExecutor.executeBatch(Stage.DISPLAY, displaySpecs)
        }

        auditLogRepository.addLog("Gaming Mode Activation", "iQOO optimizations applied (144Hz)", LogStatus.SUCCESS)
        FrameXLog.i("iQOO optimizations applied successfully", tag = TAG)
        true
    }

    suspend fun runPeriodicMaintenance() = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) return@withContext
        if (settingsRepository.vivo144FpsUnlock.value) {
            val specs = listOf(
                CommandSpec(
                    "content insert --uri content://settings/system --bind name:s:gamecube_frame_interpolation_for_sr --bind value:s:\"1:1::72:144\"",
                    OpPriority.PRIMARY
                )
            )
            ledgerExecutor.executeBatch(Stage.DISPLAY, specs)
            auditLogRepository.addLog("2-Min Maintenance Pulse", "iQOO 144Hz re-asserted", LogStatus.SUCCESS)
        }
    }

    suspend fun revertOptimizations(): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
            auditLogRepository.addLog("Gaming Mode Deactivation", "Shizuku not ready or permission missing", LogStatus.FAILED)
            return@withContext false
        }
        val targetVal = baselineGameCubeFrameInterpolation ?: "0:-1:0:0:0"
        val cmd = "content insert --uri content://settings/system --bind name:s:gamecube_frame_interpolation_for_sr --bind value:s:\"$targetVal\""
        val exitCode = shizukuManager.executeCommandWithExitCode(cmd)
        val success = (exitCode == 0)
        auditLogRepository.addLog("Gaming Mode Deactivation", "iQOO settings restored to baseline", if (success) LogStatus.SUCCESS else LogStatus.FAILED)
        success
    }

    private suspend fun querySetting(table: String, name: String): String? {
        val result = shizukuManager.executeCommandWithResult(
            "content query --uri content://settings/$table --projection value --where \"name='$name'\""
        )
        val line = result?.output?.lines()?.firstOrNull { it.contains("value=") } ?: return null
        return Regex("value=([^,}\\s]+)").find(line)?.groupValues?.get(1)?.trim()
    }
}
