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
        return runCatching { context.getString(resId, *formatArgs) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: fallback
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
    init {
        // Switching privilege backend first turns Gaming Mode off through the backend that applied it.
        privilegeManager.registerSwitchGuard {
            disableGamingMode()
            !settingsRepository.isGamingModeActive()
        }
    }

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
            _state.value = GamingModeState.Error(
                safeString(
                    R.string.gaming_err_shizuku_unavailable,
                    "Shizuku not available or permission not granted"
                )
            )
            return@withContext
        }

        engineMutex.withLock {
            try {
                executionLedger.clear()
                _state.value = GamingModeState.Enabling(
                    0.10f,
                    safeString(R.string.gaming_status_preparing, "Preparing…")
                )

                val targetPath = vivoSuiteGate.resolveCurrentPlatformPath()
                if (!cleanupStaleVivoPath(targetPath)) {
                    _state.value = GamingModeState.Error(
                        safeString(
                            R.string.gaming_err_revert_vivo_failed,
                            "Failed to revert prior Vivo optimizations"
                        )
                    )
                    return@withLock
                }

                // Write durable recovery record BEFORE applying mutations (Rule 2.8 / Plan 6.2).
                settingsRepository.setGamingPlatformPath(targetPath)
                settingsRepository.setGamingModeActive(true)
                settingsRepository.setGamingAppliedVia(privilegeManager.currentMode.value)
                settingsRepository.flushRecoveryWrites()

                _state.value = GamingModeState.Enabling(
                    0.25f,
                    safeString(R.string.gaming_status_initializing, "Initializing…")
                )

                val baselineApplied = genericGamingOptimizer.applyOptimizationsForGame(
                    packageName = _activeGamePackage.value,
                    uid = GamingProcessResolver.uidOf(context, _activeGamePackage.value)
                )
                if (!baselineApplied) {
                    settingsRepository.setGamingModeActive(false)
                    _state.value = GamingModeState.Error(
                        safeString(
                            R.string.gaming_err_snapshot_failed,
                            "Failed to capture system settings snapshot"
                        )
                    )
                    return@withLock
                }

                // Suspend background apps and record in snapshot & preferences.
                _state.value = GamingModeState.Enabling(
                    0.45f,
                    safeString(R.string.gaming_status_suspending_bg, "Suspending Background Apps…")
                )
                val candidates = oemPackageResolver.resolvePackagesToSuspend(
                    context = context,
                    whitelist = whitelist,
                    launcherGames = settingsRepository.launcherGames.value,
                    deepFreezeEnabled = settingsRepository.deepFreezeEnabled.value
                )
                if (candidates.isNotEmpty()) {
                    _state.value = GamingModeState.Enabling(
                        0.50f,
                        safeString(
                            R.string.gaming_status_suspending_count,
                            "Suspending ${candidates.size} background apps…",
                            candidates.size
                        )
                    )
                    val persistedAffected = settingsRepository.getGamingAffectedPackages().toMutableSet()
                    persistedAffected.addAll(candidates)
                    settingsRepository.setGamingAffectedPackages(persistedAffected)
                    settingsRepository.flushRecoveryWrites()

                    val suspendRes = privilegeManager.suspendPackages(candidates, suspended = true)
                    val failedSet = suspendRes?.failedPackages?.toSet() ?: emptySet()
                    val verifiedSuspended = if (suspendRes != null) {
                        candidates.filter { it !in failedSet }.toSet()
                    } else {
                        emptySet()
                    }
                    settingsRepository.setGamingAffectedPackages(verifiedSuspended)
                    _suspendedPackagesCount.value = verifiedSuspended.size

                    settingsRepository.loadGamingOptimizationSnapshot()?.let { snap ->
                        settingsRepository.saveGamingOptimizationSnapshot(
                            snap.copy(affectedPackages = verifiedSuspended)
                        )
                    }

                    ledgerExecutor.recordManual(
                        stage = Stage.APPS,
                        key = "suspended_apps",
                        displayValue = "${verifiedSuspended.size} suspended",
                        status = if (verifiedSuspended.isNotEmpty() || candidates.isEmpty()) OpStatus.APPLIED else OpStatus.FAILED,
                        priority = OpPriority.PRIMARY
                    )
                } else {
                    _suspendedPackagesCount.value = 0
                }

                // Apply DND if granted.
                GamingDnd.enable(context)

                // Apply platform-specific hardware optimizations.
                when (targetPath) {
                    GamingPlatformPath.VIVO -> {
                        vivoGamingOptimizer.applyOptimizations(
                            packageName = _activeGamePackage.value,
                            pid = 0
                        ) { progress, text ->
                            _state.value = GamingModeState.Enabling(progress, text)
                        }
                    }
                    GamingPlatformPath.IQOO -> {
                        iqooGamingOptimizer.applyOptimizations(
                            packageName = _activeGamePackage.value,
                            pid = 0
                        ) { progress, text ->
                            _state.value = GamingModeState.Enabling(progress, text)
                        }
                    }
                    GamingPlatformPath.GENERIC, GamingPlatformPath.NONE -> Unit
                }

                _isSessionActive.value = true
                _isActive.value = true
                isPulseActive = vivoSuiteGate.isVivoSuiteEnabled &&
                    (targetPath == GamingPlatformPath.VIVO || targetPath == GamingPlatformPath.IQOO)
                _state.value = GamingModeState.Active
                GamingNotificationHelper.dismissRecoveryNotification(context)
            } catch (e: Exception) {
                FrameXLog.e("enableGamingMode failed", e, tag = TAG)
                _state.value = GamingModeState.Error(
                    e.localizedMessage ?: safeString(R.string.gaming_err_snapshot_failed, "Activation failed")
                )
            }
        }
    }

    suspend fun disableGamingMode() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            _state.value = GamingModeState.Disabling
            stopMaintenancePulse()

            val isShizukuReady = privilegeManager.isReady.value
            if (!isShizukuReady) {
                val msg = safeString(
                    R.string.gaming_err_deactivate_shizuku,
                    "Deactivation incomplete: Shizuku service unavailable. Tap to retry."
                )
                _state.value = GamingModeState.Error(msg)
                GamingNotificationHelper.showRecoveryNotification(context)
                return@withLock
            }

            try {
                val snapshot = settingsRepository.loadGamingOptimizationSnapshot()
                val toUnsuspend = (snapshot?.affectedPackages ?: emptySet()) +
                    settingsRepository.getGamingAffectedPackages()

                var remainingSuspended = emptySet<String>()
                if (toUnsuspend.isNotEmpty()) {
                    remainingSuspended = GamingSuspendPipeline.unsuspend(privilegeManager, toUnsuspend)
                    settingsRepository.setGamingAffectedPackages(remainingSuspended)
                }
                _suspendedPackagesCount.value = remainingSuspended.size

                val platformReverted = revertPlatformOptimizations()
                val esportsReverted = genericGamingOptimizer.revertOptimizations()
                GamingDnd.restore(context)

                if (remainingSuspended.isNotEmpty() || !platformReverted || !esportsReverted) {
                    val msg = safeString(
                        R.string.gaming_err_deactivate_partial,
                        "Deactivation incomplete: ${remainingSuspended.size} apps still suspended. Tap to retry.",
                        remainingSuspended.size
                    )
                    _state.value = GamingModeState.Error(msg)
                    GamingNotificationHelper.showRecoveryNotification(context)
                    return@withLock
                }

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
            } catch (e: Exception) {
                FrameXLog.e("disableGamingMode failed", e, tag = TAG)
                _state.value = GamingModeState.Error(
                    safeString(
                        R.string.gaming_err_deactivate_shizuku,
                        "Deactivation incomplete: Shizuku service unavailable. Tap to retry."
                    )
                )
            }
        }
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