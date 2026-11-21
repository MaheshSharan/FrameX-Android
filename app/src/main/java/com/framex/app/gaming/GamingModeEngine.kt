package com.framex.app.gaming

import android.content.Context
import androidx.annotation.StringRes
import com.framex.app.R
import com.framex.app.gaming.catalog.GamingCommandExecutor
import com.framex.app.gaming.catalog.GamingCommandRestorer
import com.framex.app.gaming.catalog.ResolveContext
import com.framex.app.gaming.ledger.ExecutionLedger
import com.framex.app.gaming.ledger.LedgerExecutor
import com.framex.app.gaming.ledger.OpPriority
import com.framex.app.gaming.ledger.OpStatus
import com.framex.app.gaming.ledger.Stage
import com.framex.app.repository.SettingsRepository
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.utils.FrameXLog
import com.framex.app.utils.ShellSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class GamingModeState {
    data object Idle : GamingModeState()
    data class Enabling(
        val progress: Float = 0f,
        val statusText: String = "Activating Gaming Mode…"
    ) : GamingModeState()
    data object Active : GamingModeState()
    data object Disabling : GamingModeState()
    data class Error(val message: String) : GamingModeState()
}

/**
 * Core orchestrator for FrameX Gaming Mode. Command execution/rollback is delegated to the
 * unified catalog engine ([GamingCommandExecutor]/[GamingCommandRestorer]), package resolution
 * to [OemPackageResolver], crash-recovery notifications to [GamingNotificationHelper], and
 * session/crash-recovery bookkeeping (thermal recovery, legacy cleanup, device-defaults reset)
 * to [GamingDeviceReset].
 */
@Singleton
class GamingModeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privilegeManager: PrivilegeManager,
    private val gamingCommandExecutor: GamingCommandExecutor,
    private val gamingCommandRestorer: GamingCommandRestorer,
    private val gamingDeviceReset: GamingDeviceReset,
    private val settingsRepository: SettingsRepository,
    private val oemPackageResolver: OemPackageResolver,
    private val executionLedger: ExecutionLedger,
    private val ledgerExecutor: LedgerExecutor
) {

    internal val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recoveryCoordinator = GamingRecoveryCoordinator(context, privilegeManager, settingsRepository)
    private val engineMutex = Mutex()

    private val _state = MutableStateFlow<GamingModeState>(GamingModeState.Idle)
    val state: StateFlow<GamingModeState> = _state.asStateFlow()

    private val _activeGamePackage = MutableStateFlow<String?>(null)
    val activeGamePackage: StateFlow<String?> = _activeGamePackage.asStateFlow()

    private val _suspendedPackagesCount = MutableStateFlow(settingsRepository.getGamingAffectedPackages().size)
    val suspendedPackagesCount: StateFlow<Int> = _suspendedPackagesCount.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _isSessionActive = MutableStateFlow(false)

    init {
        settingsRepository.cleanupLegacyPreferences()
    }

    private fun safeString(@StringRes resId: Int, fallback: String, vararg formatArgs: Any): String {
        return runCatching {
            if (formatArgs.isEmpty()) context.getString(resId) else context.getString(resId, *formatArgs)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: fallback
    }

    fun getInstalledUserApps(): List<AppInfo> =
        oemPackageResolver.getInstalledUserApps(context)

    fun getGoogleAppsForWhitelist(): List<AppInfo> =
        oemPackageResolver.getGoogleAppsForWhitelist(context)

    fun setSessionActiveForTesting(active: Boolean) {
        _isSessionActive.value = active
        _isActive.value = active
    }

    /**
     * Triggered by [GamingModeService.onTaskRemoved] on [recoveryScope] so swipe-from-recents
     * restores all suspended apps and system settings before stopping the service (Plan 6.2).
     */
    suspend fun emergencyTeardownOnSwipeKill() {
        FrameXLog.w("Executing emergency teardown on swipe-kill", tag = TAG)
        disableGamingMode()
    }

    /**
     * Reconciles persisted crash-recovery records on process startup (Plan 6.2).
     */
    fun recoverPersistedState() {
        recoveryScope.launch {
            recoveryCoordinator.watch(
                isSessionActive = { _isSessionActive.value },
                disable = { disableGamingMode() },
                legacyCleanup = { gamingDeviceReset.performLegacyCleanupIfNeeded() }
            )
        }
    }

    suspend fun enableGamingMode(
        whitelist: Set<String> = settingsRepository.gamingModeWhitelist.value
    ) = withContext(Dispatchers.IO) {
        if (!privilegeManager.isReady.value) {
            fail(R.string.gaming_err_shizuku_unavailable, "Privileged access is not available")
            return@withContext
        }
        engineMutex.withLock {
            try {
                runActivation(whitelist)
            } catch (e: Exception) {
                FrameXLog.e("enableGamingMode failed", e, tag = TAG)
                _state.value = GamingModeState.Error(
                    e.localizedMessage ?: safeString(R.string.gaming_err_snapshot_failed, "Activation failed")
                )
            }
        }
    }

    private fun fail(@StringRes resId: Int, fallback: String) {
        _state.value = GamingModeState.Error(safeString(resId, fallback))
    }

    private fun enabling(progress: Float, @StringRes resId: Int, fallback: String) {
        _state.value = GamingModeState.Enabling(progress, safeString(resId, fallback))
    }

    /**
     * Activation steps in order. Each failure sets the error state and stops here. The 5-second
     * budget (Decision 3) is a soft target: a slow batch is logged, never aborted mid-way, since
     * rolling back a possibly-successful apply is worse than finishing late.
     */
    private suspend fun runActivation(whitelist: Set<String>) {
        val tStart = System.nanoTime()
        executionLedger.clear()
        enabling(0.10f, R.string.gaming_status_preparing, "Preparing…")

        persistRecoveryRecord()

        enabling(0.25f, R.string.gaming_status_initializing, "Initializing…")
        if (!applyBaselineOptimizations()) return

        enabling(0.45f, R.string.gaming_status_suspending_bg, "Suspending Background Apps…")
        suspendBackgroundApps(whitelist)
        GamingDnd.enable(context)

        _isSessionActive.value = true
        _isActive.value = true
        _state.value = GamingModeState.Active
        GamingNotificationHelper.dismissRecoveryNotification(context)
        warnIfOverBudget("activation", tStart)
    }

    private fun warnIfOverBudget(phase: String, tStart: Long) {
        val elapsedMs = (System.nanoTime() - tStart) / 1_000_000
        if (elapsedMs > ACTIVATION_BUDGET_MS) {
            FrameXLog.w("$phase took ${elapsedMs}ms, over the ${ACTIVATION_BUDGET_MS}ms soft budget", tag = TAG)
        }
    }

    /** Durable recovery record, written BEFORE any mutation (Rule 2.8 / Plan 6.2). */
    private suspend fun persistRecoveryRecord() {
        settingsRepository.setGamingModeActive(true)
        settingsRepository.setGamingAppliedVia(privilegeManager.currentMode.value)
        settingsRepository.flushRecoveryWrites()
    }

    /**
     * Captures baselines and applies the catalog's activation entries. Only an infrastructure
     * failure (baselines could not be read, nothing was changed) blocks the session — a single
     * command failing to apply is surfaced as a toast and the session still goes Active, since one
     * broken optimization (e.g. RAM pre-trim) must never prevent every other one, or background
     * app suspension and DND, from taking effect.
     */
    private suspend fun applyBaselineOptimizations(): Boolean {
        val context = ResolveContext(targetPackage = _activeGamePackage.value, uid = null)
        val outcome = gamingCommandExecutor.activate(context)
        if (!outcome.baselinesCaptured) {
            settingsRepository.setGamingModeActive(false)
            fail(R.string.gaming_err_snapshot_failed, "Failed to capture system settings snapshot")
            return false
        }
        if (!outcome.allCommandsApplied) {
            FrameXLog.w("Some optimizations failed to apply: ${outcome.failedIds}; continuing anyway", tag = TAG)
            _toastEvents.tryEmit(
                safeString(
                    R.string.gaming_toast_partial_optimizations,
                    "Gaming Mode is active, but some optimizations couldn't be applied — check Execution Center"
                )
            )
        }
        return true
    }

    /** Suspends background apps, persisting the candidate set first so a crash can undo it. */
    private suspend fun suspendBackgroundApps(whitelist: Set<String>) {
        val candidates = oemPackageResolver.resolvePackagesToSuspend(
            context = context,
            whitelist = whitelist,
            launcherGames = settingsRepository.launcherGames.value,
            deepFreezeEnabled = settingsRepository.deepFreezeEnabled.value,
            frameXSuspended = settingsRepository.getGamingAffectedPackages()
        )
        if (candidates.isEmpty()) {
            _suspendedPackagesCount.value = 0
            return
        }
        _state.value = GamingModeState.Enabling(
            0.50f,
            safeString(
                R.string.gaming_status_suspending_count,
                "Suspending ${candidates.size} background apps…",
                candidates.size
            )
        )
        settingsRepository.setGamingAffectedPackages(
            settingsRepository.getGamingAffectedPackages() + candidates
        )
        settingsRepository.flushRecoveryWrites()

        val result = privilegeManager.suspendPackages(candidates, suspended = true)
        val failed = result?.failedPackages?.toSet() ?: emptySet()
        val verified = if (result != null) candidates.filter { it !in failed }.toSet() else emptySet()
        settingsRepository.setGamingAffectedPackages(verified)
        _suspendedPackagesCount.value = verified.size
        ledgerExecutor.recordManual(
            stage = Stage.APPS,
            key = "suspended_apps",
            displayValue = "${verified.size} suspended",
            status = if (verified.isNotEmpty()) OpStatus.APPLIED else OpStatus.FAILED,
            priority = OpPriority.PRIMARY
        )
    }

    suspend fun disableGamingMode() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            _state.value = GamingModeState.Disabling
            if (!privilegeManager.awaitReady(READY_WAIT_MS)) {
                reportDeactivationUnavailable()
                return@withLock
            }
            try {
                runDeactivation()
            } catch (e: Exception) {
                FrameXLog.e("disableGamingMode failed", e, tag = TAG)
                _state.value = GamingModeState.Error(deactivationUnavailableMessage())
            }
        }
    }

    private fun deactivationUnavailableMessage(): String = safeString(
        R.string.gaming_err_deactivate_shizuku,
        "Deactivation incomplete: Privileged service unavailable. Tap to retry."
    )

    private fun reportDeactivationUnavailable() {
        _state.value = GamingModeState.Error(deactivationUnavailableMessage())
        GamingNotificationHelper.showRecoveryNotification(context)
    }

    /** Undoes every change through the selected backend, timing each phase. */
    private suspend fun runDeactivation() {
        val tStart = System.nanoTime()
        val timer = DeactivationTimer(tStart, privilegeManager.currentMode.value)
        val remainingSuspended = unsuspendTrackedPackages(timer)

        var t = System.nanoTime()
        gamingDeviceReset.recoverThermalOverrideIfNeeded()
        timer.log("thermal recovery", t)

        t = System.nanoTime()
        val pkg = _activeGamePackage.value
        val uid = GamingProcessResolver.uidOf(context, pkg)
        val restoreOutcome = gamingCommandRestorer.restore(ResolveContext(pkg, uid))
        timer.log("catalog restore ranAtAll=${restoreOutcome.ranAtAll} allReverted=${restoreOutcome.allReverted}", t)

        t = System.nanoTime()
        GamingDnd.restore(context)
        timer.log("dnd restore", t)

        // Only a true infrastructure failure (the restore batch could not run at all) or apps
        // still suspended blocks completion and asks for a retry. A handful of catalog entries
        // that individually failed to revert (kept on disk for the next attempt by
        // GamingCommandRestorer) must not prevent the rest of deactivation — suspended apps are
        // already back, DND is already restored — from completing.
        if (remainingSuspended.isNotEmpty() || !restoreOutcome.ranAtAll) {
            _state.value = GamingModeState.Error(
                safeString(
                    R.string.gaming_err_deactivate_partial,
                    "Deactivation incomplete: ${remainingSuspended.size} apps still suspended. Tap to retry.",
                    remainingSuspended.size
                )
            )
            GamingNotificationHelper.showRecoveryNotification(context)
            return
        }
        if (!restoreOutcome.allReverted) {
            FrameXLog.w("Some settings could not be reverted, kept for retry: ${restoreOutcome.failedIds}", tag = TAG)
            _toastEvents.tryEmit(
                safeString(
                    R.string.gaming_toast_partial_restore,
                    "Gaming Mode deactivated, but some settings couldn't be fully reverted"
                )
            )
        }
        clearSessionState()
        timer.log("deactivation complete", tStart)
        warnIfOverBudget("deactivation", tStart)
    }

    private suspend fun unsuspendTrackedPackages(timer: DeactivationTimer): Set<String> {
        val toUnsuspend = settingsRepository.getGamingAffectedPackages()
        var remaining = emptySet<String>()
        if (toUnsuspend.isNotEmpty()) {
            val t = System.nanoTime()
            remaining = GamingSuspendPipeline.unsuspend(privilegeManager, toUnsuspend)
            timer.log("unsuspend ${toUnsuspend.size} pkgs", t)
            settingsRepository.setGamingAffectedPackages(remaining)
        }
        _suspendedPackagesCount.value = remaining.size
        return remaining
    }

    private fun clearSessionState() {
        settingsRepository.setGamingModeActive(false)
        settingsRepository.setGamingAppliedVia(null)
        settingsRepository.setGamingActiveGame(null, null)
        _activeGamePackage.value = null
        _isSessionActive.value = false
        _isActive.value = false
        executionLedger.clear()
        _state.value = GamingModeState.Idle
        GamingNotificationHelper.dismissRecoveryNotification(context)
    }

    /** Re-applies the per-game catalog entries now that the real package/uid are known. */
    suspend fun promoteGamePid(packageName: String) = withContext(Dispatchers.IO) {
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext
        _activeGamePackage.value = safePkg
        val uid = GamingProcessResolver.uidOf(context, safePkg)
        settingsRepository.setGamingActiveGame(safePkg, uid)
        gamingCommandExecutor.promote(ResolveContext(safePkg, uid))
    }

    companion object {
        private const val TAG = "GamingModeEngine"
        private const val READY_WAIT_MS = 8_000L
        private const val ACTIVATION_BUDGET_MS = 5_000L
        const val RECOVERY_NOTIFICATION_ID = GamingNotificationHelper.RECOVERY_NOTIFICATION_ID

        val GOOGLE_SAFE_TO_SUSPEND: Set<String> = OemPackageResolver.GOOGLE_SAFE_TO_SUSPEND

        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

        fun resetSessionStateForTesting() {
            _isActive.value = false
        }
    }
}
