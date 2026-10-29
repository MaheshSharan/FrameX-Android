package com.framex.app.gaming

import android.content.Context
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog

/**
 * Reconciles a Gaming Mode session that was interrupted by a crash or kill. It undoes the
 * changes through the backend that applied them, or raises a recovery alert when that
 * backend cannot be reached.
 */
internal class GamingRecoveryCoordinator(
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
    private val settingsRepository: SettingsRepository
) {
    suspend fun watch(
        isSessionActive: () -> Boolean,
        disable: suspend () -> Unit,
        legacyCleanup: suspend () -> Unit
    ) {
        settingsRepository.awaitSettingsLoaded()
        var triedRecordedBackend = false
        privilegeManager.isReady.collect { ready ->
            val wasActive = settingsRepository.isGamingModeActive()
            val hasSnapshot = settingsRepository.hasActiveGamingSnapshot()
            val affected = settingsRepository.getGamingAffectedPackages()

            if (!wasActive && !hasSnapshot && affected.isEmpty()) {
                return@collect
            }

            if (isSessionActive()) {
                return@collect
            }

            // Undo through the backend that applied the changes, not the one selected now.
            val recorded = settingsRepository.getGamingAppliedVia()
            val current = privilegeManager.currentMode.value
            if (recorded != PrivilegeMode.NONE && recorded != current) {
                if (triedRecordedBackend) return@collect
                triedRecordedBackend = true
                FrameXLog.w("Recovering Gaming Mode through recorded backend ${recorded.key}", tag = TAG)
                val recovered = privilegeManager.runWithBackend(recorded) {
                    disable()
                    legacyCleanup()
                    true
                }
                if (recovered != true || settingsRepository.isGamingModeActive()) {
                    GamingNotificationHelper.showRecoveryNotification(context)
                }
                return@collect
            }

            if (ready) {
                FrameXLog.w("Recovering interrupted Gaming Mode session via the selected privilege backend", tag = TAG)
                disable()
                legacyCleanup()
            } else {
                GamingNotificationHelper.showRecoveryNotification(context)
            }
        }
    }

    private companion object {
        const val TAG = "GamingRecovery"
    }
}
