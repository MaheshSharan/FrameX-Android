package com.framex.app.gaming

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.framex.app.MainActivity
import com.framex.app.R
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import com.framex.app.gaming.ledger.CommandSpec
import com.framex.app.gaming.ledger.ExecutionLedger
import com.framex.app.gaming.ledger.LedgerExecutor
import com.framex.app.gaming.ledger.OpPriority
import com.framex.app.gaming.ledger.OpStatus
import com.framex.app.gaming.ledger.Stage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// State Models
// ---------------------------------------------------------------------------

sealed class GamingModeState {
    object Idle : GamingModeState()
    data class Enabling(val progress: Float = 0f, val statusText: String = "Preparing…") : GamingModeState()
    object Active : GamingModeState()
    object Disabling : GamingModeState()
    data class Error(val message: String) : GamingModeState()
}

data class AppInfo(
    val packageName: String,
    val label: String
)

// ---------------------------------------------------------------------------
// Engine Implementation
// ---------------------------------------------------------------------------

@Singleton
class GamingModeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val esportsOptimizationEngine: EsportsOptimizationEngine,
    private val vivoGamingOptimizer: VivoGamingOptimizer,
    private val oemPackageResolver: OemPackageResolver,
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val executionLedger: ExecutionLedger,
    private val ledgerExecutor: LedgerExecutor
) {

    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var thermalRecoveryJob: Job? = null

    // ---- Public State -------------------------------------------------------

    private val _state = MutableStateFlow<GamingModeState>(GamingModeState.Idle)
    val state: StateFlow<GamingModeState> = _state.asStateFlow()

    private val _activeGamePackage = MutableStateFlow<String?>(null)
    val activeGamePackage: StateFlow<String?> = _activeGamePackage.asStateFlow()

    private val _suspendedPackagesCount = MutableStateFlow(0)
    val suspendedPackagesCount: StateFlow<Int> = _suspendedPackagesCount.asStateFlow()

    val safeToSuspendPackages: List<String>
        get() = oemPackageResolver.getOemPackagesToSuspend()

    // ---- Public API ---------------------------------------------------------

    /**
     * Enumerate all installed non-system user apps eligible for freezing.
     */
    fun getInstalledUserApps(): List<AppInfo> {
        val pm = context.packageManager
        val selfPkg = context.packageName
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { ai ->
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    ai.packageName != selfPkg &&
                    ai.packageName !in SYSTEM_CRITICAL &&
                    ai.packageName !in GAMING_DAEMONS &&
                    ai.packageName !in HARD_WHITELIST &&
                    ai.packageName !in GOOGLE_SAFE_TO_SUSPEND
            }
            .map { ai ->
                AppInfo(
                    packageName = ai.packageName,
                    label = pm.getApplicationLabel(ai).toString()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Returns the Google apps from GOOGLE_SAFE_TO_SUSPEND that are actually installed.
     */
    fun getGoogleAppsForWhitelist(): List<AppInfo> {
        val pm = context.packageManager
        return GOOGLE_SAFE_TO_SUSPEND.mapNotNull { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                AppInfo(
                    packageName = ai.packageName,
                    label = pm.getApplicationLabel(ai).toString()
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { it.label.lowercase() }
    }

    /**
     * Full Gaming Mode activation sequence.
     */
    suspend fun enableGamingMode(userWhitelist: Set<String>, activeGamePkg: String? = null) {
        if (!isShizukuReady()) {
            _state.value = GamingModeState.Error("Shizuku not available or permission not granted")
            return
        }

        _state.value = GamingModeState.Enabling(0f, "Initializing…")
        FrameXLog.i("Starting Gaming Mode activation (activeGamePkg=$activeGamePkg)...", tag = TAG)

        val isAlreadyActive = _isActive.value
        val shouldBoostRam = activeGamePkg?.let { settingsRepository.getGameConfigBoostRam(it) } ?: true
        val resolvedWhitelist = buildFinalWhitelist(userWhitelist, activeGamePkg)

        // Clear previous session execution records on new activation
        executionLedger.clear()

        if (shouldBoostRam) {
            executeRamCachePurge()
        }

        if (!isAlreadyActive) {
            restartNotificationListener()
        }

        try {
            val installedSafeToSuspend = safeToSuspendPackages.filter { isPackageInstalled(it) }
            val newlySuspendedPkgs = mutableSetOf<String>()

            if (shouldBoostRam && !isAlreadyActive) {
                val suspended = suspendBackgroundBloat(resolvedWhitelist, installedSafeToSuspend)
                newlySuspendedPkgs.addAll(suspended)
            } else if (!shouldBoostRam) {
                ledgerExecutor.recordManual(
                    stage = Stage.APPS,
                    key = "suspended_apps",
                    displayValue = "Skipped (RAM Boost Disabled)",
                    status = OpStatus.SKIPPED,
                    priority = OpPriority.PRIMARY
                )
            }

            if (!isAlreadyActive) {
                settingsRepository.setGamingAffectedPackages(newlySuspendedPkgs)
                executeBackgroundProcessPurge(shouldBoostRam)
                applyNotificationSuppression()
            }

            val isVivo = deviceDiagnosticManager.isVivoOrIqoo()
            val optimizationsApplied = applyPlatformOptimizations(isVivo, activeGamePkg)

            if (!optimizationsApplied) {
                handleActivationFailure(isAlreadyActive, installedSafeToSuspend, newlySuspendedPkgs)
                return
            }

            updateSnapshotIfNeeded(isVivo, isAlreadyActive, newlySuspendedPkgs)
            finalizeActivation(activeGamePkg, newlySuspendedPkgs.size)

        } catch (e: Exception) {
            FrameXLog.e("Unexpected error during Gaming Mode activation", e, tag = TAG)
            _state.value = GamingModeState.Error(e.message ?: "Unexpected error during activation")
            settingsRepository.setGamingModeActive(false)
            _isActive.value = false
        }
    }

    /**
     * Full Gaming Mode deactivation sequence.
     */
    suspend fun disableGamingMode() {
        _state.value = GamingModeState.Disabling
        FrameXLog.i("Starting Gaming Mode deactivation...", tag = TAG)

        try {
            val snapshot = settingsRepository.loadGamingOptimizationSnapshot()
            val targetsToUnsuspend = snapshot?.affectedPackages?.toList()
                ?: settingsRepository.getGamingAffectedPackages().toList()

            val unsuspendedSuccessfully = revertPackageSuspensions(targetsToUnsuspend, snapshot)
            if (!unsuspendedSuccessfully) return

            purgeProcessesPostUnsuspension()
            revertNotificationSuppression()
            cleanupLegacyPreferences()
            revertPlatformOptimizations()

            _activeGamePackage.value = null
            _suspendedPackagesCount.value = 0
            settingsRepository.setGamingModeActive(false)
            _isActive.value = false
            _state.value = GamingModeState.Idle
            executionLedger.clear()
            FrameXLog.i("Gaming Mode deactivation complete!", tag = TAG)

        } catch (e: Exception) {
            FrameXLog.w("Error during Gaming Mode deactivation", e, tag = TAG)
            _state.value = GamingModeState.Error(e.message ?: "Unexpected error during deactivation")
        }
    }

    /**
     * Recovers persisted state on application startup.
     */
    fun recoverPersistedState() {
        recoverThermalOverrideIfNeeded()
        recoverLegacySettingsIfNeeded()

        val snapshot = settingsRepository.loadGamingOptimizationSnapshot()
        if (snapshot != null) {
            _activeGamePackage.value = snapshot.activeGamePackage
            _suspendedPackagesCount.value = snapshot.affectedPackages.size
            FrameXLog.w("Detected orphaned gaming optimization snapshot, scheduling recovery", tag = TAG)
            _isActive.value = true
            _state.value = GamingModeState.Active

            if (isShizukuReady()) {
                recoveryScope.launch { disableGamingMode() }
            } else {
                showRecoveryNotification()
            }
        } else if (settingsRepository.isGamingModeActive()) {
            _suspendedPackagesCount.value = settingsRepository.getGamingAffectedPackages().size
            _isActive.value = true
            _state.value = GamingModeState.Active
            if (!isShizukuReady()) {
                showRecoveryNotification()
            }
        }
    }

    // =========================================================================
    // Granular Activation Steps (IDE / GitHub Symbol Navigation)
    // =========================================================================

    private fun buildFinalWhitelist(userWhitelist: Set<String>, activeGamePkg: String?): Set<String> {
        val list = (userWhitelist + settingsRepository.launcherGames.value + HARD_WHITELIST).toMutableSet()
        list.add(context.packageName)
        if (activeGamePkg != null) list.add(activeGamePkg)
        return list
    }

    private suspend fun executeRamCachePurge() {
        ledgerExecutor.executeBatch(
            Stage.MEMORY,
            listOf(CommandSpec("pm trim-caches 4G", OpPriority.PRIMARY))
        )
        FrameXLog.i("Deep RAM cache purge (pm trim-caches 4G) executed", tag = TAG)
    }

    private suspend fun restartNotificationListener() {
        try {
            val component = ComponentName(context, GamingNotificationListener::class.java)
            context.packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            delay(100)
            context.packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            FrameXLog.w("Notification listener reset failed", e, tag = TAG)
        }
    }

    private suspend fun suspendBackgroundBloat(
        finalWhitelist: Set<String>,
        installedSafeToSuspend: List<String>
    ): Set<String> {
        val googleTargets = GOOGLE_SAFE_TO_SUSPEND.filter { it !in finalWhitelist && isPackageInstalled(it) }
        val userApps = withContext(Dispatchers.IO) { getInstalledUserApps() }
            .filter { it.packageName !in finalWhitelist }
            .map { it.packageName }

        val allTargets = (installedSafeToSuspend + googleTargets + userApps).distinct()
        val preSuspended = shizukuManager.getSuspendedPackages(allTargets)
        val selfPkg = context.packageName
        val targetsToFreeze = allTargets
            .map { it.trim() }
            .filter { it.matches(PACKAGE_NAME_REGEX) }
            .filterNot { it in preSuspended }
            .filterNot { it == selfPkg || it in HARD_WHITELIST || it in finalWhitelist }

        FrameXLog.i("Suspending ${targetsToFreeze.size} background apps (${preSuspended.size} externally pre-suspended ignored)...", tag = TAG)
        _state.value = GamingModeState.Enabling(0.5f, "Suspending ${targetsToFreeze.size} background apps…")

        val suspendResult = if (targetsToFreeze.isNotEmpty()) shizukuManager.suspendPackages(targetsToFreeze, true) else null
        val failedPkgs = suspendResult?.failedPackages?.toSet().orEmpty()

        val successful = targetsToFreeze.filter { it !in failedPkgs }.toSet()
        FrameXLog.i("Package suspension finished: ${successful.size} apps suspended, ${failedPkgs.size} failed", tag = TAG)

        if (targetsToFreeze.isEmpty()) {
            ledgerExecutor.recordManual(
                stage = Stage.APPS,
                key = "suspended_apps",
                displayValue = "0 apps",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY,
                rawCommand = "No background apps to suspend"
            )
        } else if (successful.isEmpty()) {
            ledgerExecutor.recordManual(
                stage = Stage.APPS,
                key = "suspended_apps",
                displayValue = "0 apps (${failedPkgs.size} failed)",
                status = OpStatus.FAILED,
                priority = OpPriority.PRIMARY,
                rawCommand = "Failed to suspend: ${failedPkgs.joinToString(", ")}"
            )
        } else {
            ledgerExecutor.recordManual(
                stage = Stage.APPS,
                key = "suspended_apps",
                displayValue = "${successful.size} apps",
                status = OpStatus.APPLIED,
                priority = OpPriority.PRIMARY,
                rawCommand = "pm suspend --user 0 (${successful.size} packages)"
            )
            if (failedPkgs.isNotEmpty()) {
                ledgerExecutor.recordManual(
                    stage = Stage.APPS,
                    key = "suspension_failures",
                    displayValue = "${failedPkgs.size} failed",
                    status = OpStatus.FAILED,
                    priority = OpPriority.DETAIL,
                    rawCommand = "Failed to suspend: ${failedPkgs.joinToString(", ")}"
                )
            }
        }

        return successful
    }

    private suspend fun executeBackgroundProcessPurge(boostRam: Boolean) {
        if (!boostRam) return
        _state.value = GamingModeState.Enabling(0.96f, "Purging background cache…")
        ledgerExecutor.executeBatch(
            Stage.MEMORY,
            listOf(CommandSpec("am kill-all", OpPriority.DETAIL))
        )
        FrameXLog.i("Background process purge (am kill-all) executed", tag = TAG)
    }

    private suspend fun applyNotificationSuppression() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            FrameXLog.i("DND filter set to INTERRUPTION_FILTER_NONE, verifying application...", tag = TAG)

            var filterApplied = false
            for (attempt in 1..5) {
                if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                    filterApplied = true
                    break
                }
                delay(100)
            }

            if (filterApplied) {
                ledgerExecutor.recordManual(
                    stage = Stage.DND,
                    key = "notification_filter",
                    displayValue = "Total Silence",
                    status = OpStatus.APPLIED,
                    priority = OpPriority.PRIMARY,
                    rawCommand = "NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_NONE)"
                )
            } else {
                FrameXLog.w("DND filter did not settle to INTERRUPTION_FILTER_NONE after 500ms (current: ${nm.currentInterruptionFilter})", tag = TAG)
                ledgerExecutor.recordManual(
                    stage = Stage.DND,
                    key = "notification_filter",
                    displayValue = "Filter Failed (${nm.currentInterruptionFilter})",
                    status = OpStatus.FAILED,
                    priority = OpPriority.PRIMARY,
                    rawCommand = "currentInterruptionFilter != INTERRUPTION_FILTER_NONE"
                )
            }
        } else {
            FrameXLog.w("DND policy access not granted", tag = TAG)
            ledgerExecutor.recordManual(
                stage = Stage.DND,
                key = "notification_filter",
                displayValue = "Access Missing",
                status = OpStatus.SKIPPED,
                priority = OpPriority.PRIMARY,
                rawCommand = "isNotificationPolicyAccessGranted == false"
            )
        }
    }

    private suspend fun applyPlatformOptimizations(isVivo: Boolean, activeGamePkg: String?): Boolean {
        if (isVivo) {
            FrameXLog.i("Vivo/iQOO device detected: Applying hardware-verified Vivo gaming suite", tag = TAG)
            val pid = activeGamePkg?.let { resolveProcessPid(it) } ?: 0
            return vivoGamingOptimizer.applyOptimizations(activeGamePkg, pid) { progress, statusText ->
                _state.value = GamingModeState.Enabling(progress, statusText)
            }
        }
        return try {
            val uid = activeGamePkg?.let {
                runCatching { context.packageManager.getPackageUid(it, 0) }.getOrNull()
            }
            esportsOptimizationEngine.applyOptimizationsForGame(activeGamePkg, uid)
        } catch (e: Exception) {
            FrameXLog.w("Esports optimization failed", e, tag = TAG)
            false
        }
    }

    private suspend fun handleActivationFailure(
        isAlreadyActive: Boolean,
        installedSafeToSuspend: List<String>,
        affectedPkgs: Set<String>
    ) {
        _state.value = GamingModeState.Error("Failed to capture system settings snapshot")
        FrameXLog.e("Esports optimizations failed to apply, aborting activation", tag = TAG)

        if (!isAlreadyActive) {
            val allToUnsuspend = (installedSafeToSuspend + affectedPkgs).distinct()
            shizukuManager.suspendPackages(allToUnsuspend, false)
        }
        settingsRepository.setGamingModeActive(false)
        _isActive.value = false
    }

    private fun updateSnapshotIfNeeded(isVivo: Boolean, isAlreadyActive: Boolean, affectedPkgs: Set<String>) {
        if (!isVivo && (!isAlreadyActive || affectedPkgs.isNotEmpty())) {
            val currentSnapshot = settingsRepository.loadGamingOptimizationSnapshot()
            currentSnapshot?.let {
                val updatedSnapshot = it.copy(affectedPackages = affectedPkgs)
                settingsRepository.saveGamingOptimizationSnapshot(updatedSnapshot)
            }
        }
    }

    private fun finalizeActivation(activeGamePkg: String?, suspendedCount: Int = 0) {
        _activeGamePackage.value = activeGamePkg
        _suspendedPackagesCount.value = suspendedCount
        settingsRepository.setGamingModeActive(true)
        _isActive.value = true
        _state.value = GamingModeState.Active
        FrameXLog.i("Gaming Mode activation complete! Active game: $activeGamePkg, suspended: $suspendedCount", tag = TAG)
    }

    // =========================================================================
    // Granular Deactivation Steps (IDE / GitHub Symbol Navigation)
    // =========================================================================

    private suspend fun revertPackageSuspensions(
        targetsToUnsuspend: List<String>,
        snapshot: GamingOptimizationSnapshot?
    ): Boolean {
        if (targetsToUnsuspend.isEmpty()) return true

        FrameXLog.i("Attempting to unsuspend ${targetsToUnsuspend.size} FrameX-managed packages...", tag = TAG)
        val suspendResult = shizukuManager.suspendPackages(targetsToUnsuspend, false)
        if (suspendResult == null) {
            FrameXLog.e("Deactivation failed: Shizuku IPC binder unavailable", tag = TAG)
            _state.value = GamingModeState.Error("Deactivation incomplete: Shizuku service unavailable. Tap to retry.")
            return false
        }

        val failedPkgs = suspendResult.failedPackages?.toSet().orEmpty()
        if (failedPkgs.isNotEmpty()) {
            FrameXLog.w("Deactivation partial failure: ${failedPkgs.size}/${targetsToUnsuspend.size} failed: $failedPkgs", tag = TAG)
            settingsRepository.setGamingAffectedPackages(failedPkgs)
            snapshot?.let {
                settingsRepository.saveGamingOptimizationSnapshot(it.copy(affectedPackages = failedPkgs))
            }
            _state.value = GamingModeState.Error("Deactivation incomplete: ${failedPkgs.size} apps still suspended. Tap to retry.")
            return false
        }

        FrameXLog.i("Package unsuspension completed successfully (${targetsToUnsuspend.size} apps)", tag = TAG)
        settingsRepository.setGamingAffectedPackages(emptySet())
        return true
    }

    private suspend fun purgeProcessesPostUnsuspension() {
        shizukuManager.executeCommand("am kill-all")
        FrameXLog.i("Background process purge (am kill-all) executed", tag = TAG)
    }

    private fun revertNotificationSuppression() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            FrameXLog.i("DND filter restored to INTERRUPTION_FILTER_ALL", tag = TAG)
        }
    }

    private fun cleanupLegacyPreferences() {
        val prefs = context.getSharedPreferences("framex_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("orig_ringtone_val")
            .remove("orig_brightness_mode")
            .remove("orig_rotation_mode")
            .apply()
    }

    private suspend fun revertPlatformOptimizations() {
        if (!deviceDiagnosticManager.isVivoOrIqoo()) {
            val revertSuccess = runCatching { esportsOptimizationEngine.revertOptimizations() }.getOrDefault(false)
            if (revertSuccess) {
                FrameXLog.i("Esports optimizations reverted successfully", tag = TAG)
            } else {
                FrameXLog.w("Esports revert incomplete during deactivation", tag = TAG)
            }
        } else {
            FrameXLog.i("Vivo/iQOO device detected: Reverting Vivo gaming suite...", tag = TAG)
            vivoGamingOptimizer.revertOptimizations()
            settingsRepository.clearGamingOptimizationSnapshot()
        }
    }

    suspend fun runPeriodicMaintenance() {
        if (deviceDiagnosticManager.isVivoOrIqoo()) {
            vivoGamingOptimizer.runPeriodicMaintenance()
        }
    }

    suspend fun resolveProcessPid(packageName: String): Int {
        val res = shizukuManager.executeCommandWithResult("pidof $packageName")
        val out = res?.output?.trim().orEmpty()
        return out.split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: 0
    }

    suspend fun promoteGamePid(packageName: String, pid: Int) {
        if (deviceDiagnosticManager.isVivoOrIqoo()) {
            vivoGamingOptimizer.promoteGamePid(packageName, pid)
        }
    }

    // =========================================================================
    // Startup & Thermal Recovery Helpers
    // =========================================================================

    private fun recoverThermalOverrideIfNeeded() {
        if (deviceDiagnosticManager.isVivoOrIqoo()) return
        if (!settingsRepository.needsThermalOverrideRecovery() || thermalRecoveryJob?.isActive == true) return

        thermalRecoveryJob = recoveryScope.launch {
            combine(shizukuManager.isShizukuAvailable, shizukuManager.hasPermission) { available, granted ->
                available && granted
            }
                .distinctUntilChanged()
                .filter { it }
                .first { esportsOptimizationEngine.recoverThermalOverrideIfNeeded() }
        }
    }

    private fun recoverLegacySettingsIfNeeded() {
        if (deviceDiagnosticManager.isVivoOrIqoo()) return
        if (!settingsRepository.needsLegacySettingsCleanup()) return

        recoveryScope.launch {
            combine(shizukuManager.isShizukuAvailable, shizukuManager.hasPermission) { available, granted ->
                available && granted
            }
                .distinctUntilChanged()
                .filter { it }
                .first { esportsOptimizationEngine.performLegacyCleanupIfNeeded() }
        }
    }

    private fun showRecoveryNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GamingModeService.CHANNEL_ID)
            .setContentTitle("Gaming Mode Interrupted")
            .setContentText("Tap to connect Shizuku and restore your apps.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        nm.notify(RECOVERY_NOTIFICATION_ID, notification)
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isShizukuReady(): Boolean =
        shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value

    // =========================================================================
    // Static Declarations & Whitelists
    // =========================================================================

    companion object {
        private const val TAG = "GamingMode"

        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

        internal const val RECOVERY_NOTIFICATION_ID = 3

        val GOOGLE_SAFE_TO_SUSPEND = listOf(
            "com.google.android.youtube",
            "com.google.android.apps.photos",
            "com.google.android.apps.maps",
            "com.google.android.gm",
            "com.google.android.apps.messaging",
            "com.google.android.calendar",
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.bard",
            "com.google.android.apps.nbu.files",
            "com.google.android.projection.gearhead",
            "com.google.android.apps.authenticator2",
            "com.android.chrome"
        )

        val SYSTEM_CRITICAL = listOf(
            "com.vivo.pem",
            "com.vivo.abe",
            "com.vivo.daemonService",
            "com.vivo.sps",
            "com.vivo.pie",
            "com.vivo.fingerprintui",
            "com.vivo.fingerprint",
            "com.vivo.fingerprintvit",
            "com.vivo.faceui",
            "com.vivo.faceunlock",
            "com.vivo.systemuiplugin",
            "com.vivo.networkstate",
            "com.vivo.connbase",
            "com.android.systemui",
            "com.android.phone",
            "com.mediatek.ims"
        )

        val GAMING_DAEMONS = listOf(
            "com.vivo.gamecube",
            "com.vivo.gamewatch",
            "com.vivo.game",
            "com.iqoo.powersaving",
            "com.microsoft.deviceintegrationservice"
        )

        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z0-9_.]+$")

        val HARD_WHITELIST = setOf(
            "com.framex.app",
            "moe.shizuku.privileged.api",
            "com.adguard.android",
            "com.adguard.vpn"
        )
    }
}