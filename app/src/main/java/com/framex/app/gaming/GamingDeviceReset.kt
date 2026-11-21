package com.framex.app.gaming

import com.framex.app.gaming.catalog.GamingCommandRestorer
import com.framex.app.gaming.catalog.ResolveContext
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a "Restore to Default" attempt. */
data class ResetOutcome(val success: Boolean, val usedLegacyFallback: Boolean)

/**
 * Session/crash-recovery bookkeeping left over after [GamingCommandExecutor]/[GamingCommandRestorer]
 * absorbed the former `GenericGamingOptimizer`'s command execution: thermal-override recovery,
 * one-time legacy cleanup, and the "Restore to Default" orchestration. None of this runs catalog
 * commands directly — it calls through [GamingCommandRestorer] for that and only falls back to a
 * hardcoded command list when no baseline was ever recorded for the session.
 */
@Singleton
class GamingDeviceReset @Inject constructor(
    private val privilegeManager: PrivilegeManager,
    private val settingsRepository: SettingsRepository,
    private val restorer: GamingCommandRestorer
) {

    suspend fun recoverThermalOverrideIfNeeded(): Boolean {
        if (!settingsRepository.needsThermalOverrideRecovery()) return true
        if (!privilegeManager.isReady.value) return false

        val result = privilegeManager.exec("cmd thermalservice reset")
        if (!result.isSuccess) {
            FrameXLog.w("Thermal override recovery failed", tag = TAG)
            return false
        }
        settingsRepository.markThermalOverrideRecoveryComplete()
        FrameXLog.i("Thermal override recovery completed", tag = TAG)
        return true
    }

    suspend fun performLegacyCleanupIfNeeded(): Boolean {
        if (!settingsRepository.needsLegacySettingsCleanup()) return true
        if (!privilegeManager.isReady.value) return false

        FrameXLog.i("Performing one-time legacy settings cleanup", tag = TAG)
        val allSucceeded = executeCommandList(LEGACY_CLEANUP_COMMANDS)
        if (allSucceeded) {
            settingsRepository.markLegacySettingsCleanupComplete()
            FrameXLog.i("Legacy settings cleanup completed successfully", tag = TAG)
        }
        return allSucceeded
    }

    /**
     * Restores every baseline recorded for the current/last session. Falls back to a fixed
     * reset-command list, which cannot know the device's real original values, only when no
     * baseline exists at all (crash before any capture, or data cleared). [ResetOutcome
     * .usedLegacyFallback] tells the caller to surface that this happened, per AGENTS.md §2.8:
     * a silent generic guess must not be presented as a precise restore.
     */
    suspend fun resetToDeviceDefaults(forceReset: Boolean = false): ResetOutcome {
        if (!forceReset && !settingsRepository.needsLegacySettingsCleanup()) {
            return ResetOutcome(success = true, usedLegacyFallback = false)
        }
        if (!privilegeManager.isReady.value) return ResetOutcome(success = false, usedLegacyFallback = false)

        FrameXLog.i("User-triggered device defaults reset", tag = TAG)

        unsuspendAllTrackedPackages()

        val hasBaseline = settingsRepository.getGamingBaselines().isNotEmpty()
        val context = ResolveContext(
            targetPackage = settingsRepository.getGamingActiveGamePackage(),
            uid = settingsRepository.getGamingActiveGameUid()
        )
        val success = if (hasBaseline) {
            restorer.restore(context).allReverted
        } else {
            executeCommandList(LEGACY_RESET_COMMANDS)
        }

        settingsRepository.markLegacySettingsCleanupComplete()
        settingsRepository.setGamingActiveGame(null, null)

        FrameXLog.i(
            "Device defaults reset complete (success=$success, legacyFallback=${!hasBaseline})",
            tag = TAG
        )
        return ResetOutcome(success = success, usedLegacyFallback = !hasBaseline)
    }

    private suspend fun unsuspendAllTrackedPackages() {
        val packages = settingsRepository.getGamingAffectedPackages()
        if (packages.isNotEmpty()) {
            FrameXLog.i("Resetting suspended packages: ${packages.size} apps", tag = TAG)
            privilegeManager.suspendPackages(packages.toList(), false)
            settingsRepository.setGamingAffectedPackages(emptySet())
        }
    }

    private suspend fun executeCommandList(commands: List<String>): Boolean {
        if (commands.isEmpty()) return true
        val exitCode = privilegeManager.execExit(commands.joinToString("; "))
        return exitCode == 0
    }

    private companion object {
        const val TAG = "GamingDeviceReset"

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
