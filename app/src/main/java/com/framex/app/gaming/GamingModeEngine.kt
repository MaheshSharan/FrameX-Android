package com.framex.app.gaming

import android.content.Context
import androidx.annotation.StringRes
import com.framex.app.R
import com.framex.app.device.DeviceDiagnosticManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
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
 * Core orchestrator for FrameX Gaming Mode (Plan 4.1, 4.2, 4.4, 6.2).
 *
 * Package resolution is delegated to [OemPackageResolver], crash-recovery notifications to
 * [GamingNotificationHelper], and legacy preferences cleanup to [SettingsRepository].
 */
@Singleton
class GamingModeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val privilegeManager: PrivilegeManager,
    private val genericGamingOptimizer: GenericGamingOptimizer,
    private val vivoGamingOptimizer: VivoGamingOptimizer,
    private val iqooGamingOptimizer: IqooGamingOptimizer,
    private val settingsRepository: SettingsRepository,
    private val oemPackageResolver: OemPackageResolver,
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val executionLedger: ExecutionLedger,
    private val ledgerExecutor: LedgerExecutor,
    private val vivoSuiteGate: VivoSuiteGate
) {

    internal val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recoveryCoordinator = GamingRecoveryCoordinator(context, privilegeManager, settingsRepository)
    private val engineMutex = Mutex()
    private var pulseJob: Job? = null

    private val _state = MutableStateFlow<GamingModeState>(GamingModeState.Idle)
    val state: StateFlow<GamingModeState> = _state.asStateFlow()

    private val _activeGamePackage = MutableStateFlow<String?>(null)
    val activeGamePackage: StateFlow<String?> = _activeGamePackage.asStateFlow()

    private val _suspendedPackagesCount = MutableStateFlow(settingsRepository.getGamingAffectedPackages().size)
    val suspendedPackagesCount: StateFlow<Int> = _suspendedPackagesCount.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val _isSessionActive = MutableStateFlow(false)

    val shouldPulseMaintenance: Flow<Boolean> = combine(
        _isSessionActive,
        settingsRepository.gamingPlatformPath,
        vivoSuiteGate.isVivoSuiteEnabledFlow
    ) { active, path, suiteEnabled ->
        active && suiteEnabled && (path == GamingPlatformPath.VIVO || path == GamingPlatformPath.IQOO)
    }.distinctUntilChanged()

    @Volatile
    var isPulseActive: Boolean = false
        private set

    init {
        settingsRepository.cleanupLegacyPreferences()
        observePulseState()
    }

    private fun observePulseState() {
        recoveryScope.launch {
            shouldPulseMaintenance.collect { shouldPulse ->
                if (shouldPulse) startMaintenancePulse() else stopMaintenancePulse()
            }
        }
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
        val path = settingsRepository.getGamingPlatformPath()
        isPulseActive = active && vivoSuiteGate.isVivoSuiteEnabled &&
            (path == GamingPlatformPath.VIVO || path == GamingPlatformPath.IQOO)
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
                legacyCleanup = { genericGamingOptimizer.performLegacyCleanupIfNeeded() }
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

    /** Activation steps in order. Each failure sets the error state and stops here. */
    private suspend fun runActivation(whitelist: Set<String>) {
        executionLedger.clear()
        enabling(0.10f, R.string.gaming_status_preparing, "Preparing…")

        val targetPath = vivoSuiteGate.resolveCurrentPlatformPath()
        if (!cleanupStaleVivoPath(targetPath)) {
            fail(R.string.gaming_err_revert_vivo_failed, "Failed to revert prior Vivo optimizations")
            return
        }
        persistRecoveryRecord(targetPath)

        enabling(0.25f, R.string.gaming_status_initializing, "Initializing…")
        if (!applyBaselineOptimizations()) return

        enabling(0.45f, R.string.gaming_status_suspending_bg, "Suspending Background Apps…")
        suspendBackgroundApps(whitelist)
        GamingDnd.enable(context)
        applyPlatformOptimizations(targetPath)

        _isSessionActive.value = true
        _isActive.value = true
        isPulseActive = vivoSuiteGate.isVivoSuiteEnabled &&
            (targetPath == GamingPlatformPath.VIVO || targetPath == GamingPlatformPath.IQOO)
        _state.value = GamingModeState.Active
        GamingNotificationHelper.dismissRecoveryNotification(context)
    }

    /** Durable recovery record, written BEFORE any mutation (Rule 2.8 / Plan 6.2). */
    private suspend fun persistRecoveryRecord(targetPath: GamingPlatformPath) {
        settingsRepository.setGamingPlatformPath(targetPath)
        settingsRepository.setGamingModeActive(true)
        settingsRepository.setGamingAppliedVia(privilegeManager.currentMode.value)
        settingsRepository.flushRecoveryWrites()
    }

    /** Captures the snapshot and applies the generic tweaks. Returns false after reporting failure. */
    private suspend fun applyBaselineOptimizations(): Boolean {
        val applied = genericGamingOptimizer.applyOptimizationsForGame(
            packageName = _activeGamePackage.value,
            uid = GamingProcessResolver.uidOf(context, _activeGamePackage.value)
        )
        if (!applied) {
            settingsRepository.setGamingModeActive(false)
            fail(R.string.gaming_err_snapshot_failed, "Failed to capture system settings snapshot")
        }
        return applied
    }

    /** Suspends background apps, persisting the candidate set first so a crash can undo it. */
    private suspend fun suspendBackgroundApps(whitelist: Set<String>) {
        val candidates = oemPackageResolver.resolvePackagesToSuspend(
            context = context,
            whitelist = whitelist,
            launcherGames = settingsRepository.launcherGames.value,
            deepFreezeEnabled = settingsRepository.deepFreezeEnabled.value
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
        settingsRepository.loadGamingOptimizationSnapshot()?.let { snap ->
            settingsRepository.saveGamingOptimizationSnapshot(snap.copy(affectedPackages = verified))
        }
        ledgerExecutor.recordManual(
            stage = Stage.APPS,
            key = "suspended_apps",
            displayValue = "${verified.size} suspended",
            status = if (verified.isNotEmpty()) OpStatus.APPLIED else OpStatus.FAILED,
            priority = OpPriority.PRIMARY
        )
    }

    private suspend fun applyPlatformOptimizations(path: GamingPlatformPath) {
        val onProgress: (Float, String) -> Unit = { progress, text ->
            _state.value = GamingModeState.Enabling(progress, text)
        }
        when (path) {
            GamingPlatformPath.VIVO ->
                vivoGamingOptimizer.applyOptimizations(_activeGamePackage.value, 0, onProgress)
            GamingPlatformPath.IQOO ->
                iqooGamingOptimizer.applyOptimizations(_activeGamePackage.value, 0, onProgress)
            GamingPlatformPath.GENERIC, GamingPlatformPath.NONE -> Unit
        }
    }

    suspend fun disableGamingMode() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            _state.value = GamingModeState.Disabling
            stopMaintenancePulse()
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
        val platformReverted = revertPlatformOptimizations()
        timer.log("platform revert ok=$platformReverted", t)
        t = System.nanoTime()
        val genericReverted = genericGamingOptimizer.revertOptimizations()
        timer.log("generic revert ok=$genericReverted", t)
        t = System.nanoTime()
        GamingDnd.restore(context)
        timer.log("dnd restore", t)

        if (remainingSuspended.isNotEmpty() || !platformReverted || !genericReverted) {
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
        clearSessionState()
        timer.log("deactivation complete", tStart)
    }

    private suspend fun unsuspendTrackedPackages(timer: DeactivationTimer): Set<String> {
        val snapshot = settingsRepository.loadGamingOptimizationSnapshot()
        val toUnsuspend = (snapshot?.affectedPackages ?: emptySet()) +
            settingsRepository.getGamingAffectedPackages()
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

    private suspend fun clearSessionState() {
        settingsRepository.setGamingPlatformPath(null)
        settingsRepository.setGamingModeActive(false)
        settingsRepository.setGamingAppliedVia(null)
        settingsRepository.clearGamingOptimizationSnapshot()
        _activeGamePackage.value = null
        _isSessionActive.value = false
        _isActive.value = false
        isPulseActive = false
        executionLedger.clear()
        _state.value = GamingModeState.Idle
        GamingNotificationHelper.dismissRecoveryNotification(context)
    }

    suspend fun revertPlatformOptimizations(): Boolean {
        val persistedPath = settingsRepository.getGamingPlatformPath()
        return when (persistedPath) {
            GamingPlatformPath.VIVO -> vivoGamingOptimizer.revertOptimizations()
            GamingPlatformPath.IQOO -> iqooGamingOptimizer.revertOptimizations()
            GamingPlatformPath.GENERIC, GamingPlatformPath.NONE -> true
            null -> {
                when {
                    vivoSuiteGate.isIqooHardware -> iqooGamingOptimizer.revertOptimizations()
                    vivoSuiteGate.isVivoHardware -> vivoGamingOptimizer.revertOptimizations()
                    else -> true
                }
            }
        }
    }

    suspend fun cleanupStaleVivoPath(newPath: GamingPlatformPath): Boolean {
        val priorPath = settingsRepository.getGamingPlatformPath()
        if ((priorPath == GamingPlatformPath.VIVO || priorPath == GamingPlatformPath.IQOO) && newPath != priorPath) {
            val reverted = runCatching { revertPlatformOptimizations() }.getOrDefault(false)
            if (reverted) {
                settingsRepository.clearGamingOptimizationSnapshot()
                executionLedger.removeStages(VIVO_PLATFORM_STAGES)
                return true
            }
            return false
        }
        return true
    }

    fun onVivoOptToggledOffMidSession(): Job? {
        val currentPath = settingsRepository.getGamingPlatformPath()
        if (currentPath != GamingPlatformPath.VIVO && currentPath != GamingPlatformPath.IQOO) {
            return null
        }
        stopMaintenancePulse()
        return recoveryScope.launch {
            val reverted = runCatching { revertPlatformOptimizations() }.getOrDefault(false)
            if (reverted) {
                settingsRepository.setGamingPlatformPath(GamingPlatformPath.NONE)
                executionLedger.removeStages(VIVO_PLATFORM_STAGES)
                _toastEvents.tryEmit(
                    safeString(
                        R.string.gaming_toast_hardware_rolled_back,
                        "Hardware optimizations rolled back"
                    )
                )
            }
        }
    }

    suspend fun resolveProcessPid(packageName: String): Int =
        withContext(Dispatchers.IO) { GamingProcessResolver.pidOf(privilegeManager, packageName) }

    suspend fun promoteGamePid(packageName: String, pid: Int) = withContext(Dispatchers.IO) {
        val safePkg = ShellSanitizer.sanitizePackageName(packageName) ?: return@withContext
        _activeGamePackage.value = safePkg
        val uid = GamingProcessResolver.uidOf(context, safePkg)
        genericGamingOptimizer.attachGame(safePkg, uid)

        when (settingsRepository.getGamingPlatformPath()) {
            GamingPlatformPath.VIVO -> {
                if (pid > 0) vivoGamingOptimizer.promoteGamePid(safePkg, pid)
            }
            GamingPlatformPath.IQOO -> {
                if (pid > 0) iqooGamingOptimizer.promoteGamePid(safePkg, pid)
            }
            else -> Unit
        }
    }

    private fun startMaintenancePulse() {
        stopMaintenancePulse()
        isPulseActive = true
        pulseJob = recoveryScope.launch {
            while (isActive && _isSessionActive.value) {
                delay(MAINTENANCE_PULSE_INTERVAL_MS)
                if (!isActive || !_isSessionActive.value) break
                when (settingsRepository.getGamingPlatformPath()) {
                    GamingPlatformPath.VIVO -> vivoGamingOptimizer.runPeriodicMaintenance()
                    GamingPlatformPath.IQOO -> iqooGamingOptimizer.runPeriodicMaintenance()
                    else -> break
                }
            }
        }
    }

    private fun stopMaintenancePulse() {
        isPulseActive = false
        pulseJob?.cancel()
        pulseJob = null
    }

    companion object {
        private const val TAG = "GamingModeEngine"
        private const val READY_WAIT_MS = 8_000L
        private const val MAINTENANCE_PULSE_INTERVAL_MS = 120_000L
        const val RECOVERY_NOTIFICATION_ID = GamingNotificationHelper.RECOVERY_NOTIFICATION_ID

        val GOOGLE_SAFE_TO_SUSPEND: Set<String> = OemPackageResolver.GOOGLE_SAFE_TO_SUSPEND

        private val VIVO_PLATFORM_STAGES = setOf(
            Stage.POWER,
            Stage.DISPLAY,
            Stage.HANDSHAKE,
            Stage.GYRO,
            Stage.TOUCH,
            Stage.KERNEL
        )

        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

        fun resetSessionStateForTesting() {
            _isActive.value = false
        }
    }
}