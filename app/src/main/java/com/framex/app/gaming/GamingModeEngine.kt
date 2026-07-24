package com.framex.app.gaming

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// State model
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
// Engine
// ---------------------------------------------------------------------------

@Singleton
class GamingModeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val esportsOptimizationEngine: EsportsOptimizationEngine,
    private val oemPackageResolver: OemPackageResolver
) {

    // ---- Public state -------------------------------------------------------

    private val _state = MutableStateFlow<GamingModeState>(GamingModeState.Idle)
    val state: StateFlow<GamingModeState> = _state.asStateFlow()

    // Companion-level flag so GamingNotificationListener can read it without DI.
    companion object {
        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

        internal const val RECOVERY_NOTIFICATION_ID = 3
    }

    val SAFE_TO_SUSPEND: List<String>
        get() = oemPackageResolver.getOemPackagesToSuspend()

    val GOOGLE_SAFE_TO_SUSPEND = listOf(
        // Google user-facing apps — safe to freeze during gaming
        "com.google.android.youtube",
        "com.google.android.apps.photos",
        "com.google.android.apps.maps",
        "com.google.android.gm",                   // Gmail
        "com.google.android.apps.messaging",        // Google Messages
        "com.google.android.calendar",
        "com.google.android.googlequicksearchbox",  // Google Search / Assistant
        "com.google.android.apps.bard",             // Gemini
        "com.google.android.apps.nbu.files",        // Files by Google
        "com.google.android.apps.wellbeing",        // Digital Wellbeing
        "com.google.android.projection.gearhead",   // Android Auto
        "com.google.android.apps.authenticator2",   // Authenticator
        "com.google.android.apps.restore",          // Google Restore
        "com.android.chrome"                        // Chrome browser
    )

    val SYSTEM_CRITICAL = listOf(
        // Core Daemons — suspending these causes soft-reboot on OriginOS
        "com.vivo.pem",                // Power Event Manager — restarts force-stopped apps
        "com.vivo.abe",                // App Behavior Engine
        "com.vivo.daemonService",      // Hardware daemon
        "com.vivo.sps",                // System Power Service
        "com.vivo.pie",                // Framework extension

        // Hardware & UI Modules
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
        "com.mediatek.ims"             // VoLTE — kills calls if suspended
    )

    val GAMING_DAEMONS = listOf(
        "com.vivo.gamecube",
        "com.vivo.gamewatch",
        "com.vivo.game",
        "com.iqoo.powersaving",        // Prevents thermal throttling
        "com.microsoft.deviceintegrationservice"  // ThermalInfoService bridge
    )

    // Always protected — losing Shizuku = losing the ADB bridge.
    private val HARD_WHITELIST = setOf(
        "moe.shizuku.privileged.api",  // Shizuku itself
        context.packageName,           // FrameX itself
        "com.adguard.android",
        "com.adguard.vpn"
    )

    // ---- Public API ---------------------------------------------------------

    /**
     * Enumerate all installed non-system user apps that are candidates for
     * the AppOps / force-stop treatment.  Returns them sorted by label.
     */
    fun getInstalledUserApps(): List<AppInfo> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { ai ->
                // Keep only user-installed apps (no FLAG_SYSTEM)
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
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
     * Returns the Google apps from GOOGLE_SAFE_TO_SUSPEND that are actually
     * installed on this device, so the whitelist UI can show them as toggleable.
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
                null  // Not installed on this device
            }
        }.sortedBy { it.label.lowercase() }
    }

    /**
     * Full Gaming Mode activation sequence.
     *
     * 1. pm suspend --user 0 on SAFE_TO_SUSPEND
     * 2. AppOps ignore + am force-stop on non-whitelisted user apps
     * 3. am kill-all
     * 4. Enable DND (if policy access is granted)
     */
    suspend fun enableGamingMode(userWhitelist: Set<String>, activeGamePkg: String? = null) {
        if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
            _state.value = GamingModeState.Error("Shizuku not available or permission not granted")
            return
        }

        _state.value = GamingModeState.Enabling(0f, "Initializing…")
        
        val prefs = context.getSharedPreferences("framex_settings", Context.MODE_PRIVATE)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        var finalWhitelist = userWhitelist + settingsRepository.launcherGames.value
        var boostRam = true

        if (activeGamePkg != null) {
            finalWhitelist = finalWhitelist + activeGamePkg
            boostRam = settingsRepository.getGameConfigBoostRam(activeGamePkg)

            // Save original ringtone volume
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            prefs.edit().putInt("orig_ringtone_val", currentVol).apply()

            // Change Ringtone volume
            val targetVolPct = settingsRepository.getGameConfigRingtoneVol(activeGamePkg)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            val targetVol = (targetVolPct / 100f * maxVol).toInt().coerceIn(0, maxVol)
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, targetVol, 0)
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.w("Failed to set ringtone volume", e)
            }

            // Settings Overrides (auto-brightness, auto-rotate)
            val canWrite = Settings.System.canWrite(context)
            if (canWrite) {
                // Brightness override
                if (settingsRepository.getGameConfigDisableBrightness(activeGamePkg)) {
                    val origBrightnessMode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    prefs.edit().putInt("orig_brightness_mode", origBrightnessMode).apply()
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                }

                // Rotation override
                if (settingsRepository.getGameConfigDisableRotate(activeGamePkg)) {
                    val origRotation = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
                    prefs.edit().putInt("orig_rotation_mode", origRotation).apply()
                    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) // Lock orientation
                }
            }
        }

        val isAlreadyActive = _isActive.value

        if (boostRam) {
            // Phase 0 — Deep Cache Purge (Instantly clear system caches to free RAM block)
            try {
                shizukuManager.executeCommand("pm trim-caches 4G")
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.w("Deep cache purge failed", e)
            }
        }
        
        // OriginOS 6 "Final Boss" Fix: Force re-bind the Notification Listener.
        // On Vivo/Oppo, the listener can fall into a 'coma' if unused. 
        // Disabling and re-enabling it right before use wakes it up 100% of the time.
        if (!isAlreadyActive) {
            try {
                val component = ComponentName(context, GamingNotificationListener::class.java)
                context.packageManager.setComponentEnabledSetting(
                    component, 
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 
                    PackageManager.DONT_KILL_APP
                )
                // Small delay to allow the system to process the unbind before re-binding
                kotlinx.coroutines.delay(100)
                context.packageManager.setComponentEnabledSetting(
                    component, 
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.w("Notification listener reset failed", e)
            }
        }

        try {
            val affectedPkgs = mutableSetOf<String>()

            if (boostRam && !isAlreadyActive) {
                // ----------------------------------------------------------------
                // Phase 1 & 2 — Batch Suspend OEM, Google, and User Apps
                // ----------------------------------------------------------------
                val googleTargets = GOOGLE_SAFE_TO_SUSPEND.filter { it !in finalWhitelist }
                val userApps = withContext(Dispatchers.IO) { getInstalledUserApps() }
                    .filter { it.packageName !in finalWhitelist }
                val userTargets = userApps.map { it.packageName }

                val allTargets = (SAFE_TO_SUSPEND + googleTargets + userTargets).distinct()

                _state.value = GamingModeState.Enabling(0.5f, "Suspending ${allTargets.size} background apps…")
                shizukuManager.suspendPackages(allTargets, true)
                shizukuManager.setAppOpMode(allTargets, 70, 1)

                affectedPkgs.addAll(googleTargets)
                affectedPkgs.addAll(userTargets)
            }

            // Persist the affected list so disableGamingMode restores only what we changed.
            if (!isAlreadyActive) {
                settingsRepository.setGamingAffectedPackages(affectedPkgs)
            }

            if (boostRam) {
                // ----------------------------------------------------------------
                // Phase 3 — Kill cached background processes
                // ----------------------------------------------------------------
                _state.value = GamingModeState.Enabling(0.96f, "Purging background cache…")
                shizukuManager.executeCommand("am kill-all")
            }

            // ----------------------------------------------------------------
            // Phase 4 — Enable DND via NotificationManager policy
            // ----------------------------------------------------------------
            if (!isAlreadyActive) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
            }

            // Apply Esports optimizations for all activations.
            // When activeGamePkg is null (manual activation), package-specific commands like
            // cmd game set --fps are skipped, but all system-wide Vivo/thermal boosts still run.
            try {
                val uid = activeGamePkg?.let {
                    runCatching { context.packageManager.getPackageUid(it, 0) }.getOrNull()
                }
                esportsOptimizationEngine.applyOptimizationsForGame(activeGamePkg, uid)
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.w("Esports optimization failed", e)
            }

            // ----------------------------------------------------------------
            // Done
            // ----------------------------------------------------------------
            settingsRepository.setGamingModeActive(true)
            _isActive.value = true
            _state.value = GamingModeState.Active

        } catch (e: Exception) {
            _state.value = GamingModeState.Error(e.message ?: "Unexpected error during activation")
            settingsRepository.setGamingModeActive(false)
            _isActive.value = false
        }
    }



    /**
     * Full Gaming Mode deactivation sequence.
     *
     * 1. pm unsuspend on SAFE_TO_SUSPEND
     * 2. pm unsuspend + restore AppOps on previously-affected user packages
     * 3. Disable DND
     */
    suspend fun disableGamingMode() {
        _state.value = GamingModeState.Disabling

        try {
            // Unsuspend all OEM packages and user packages in a single batched IPC call
            val affectedUserPkgs = settingsRepository.getGamingAffectedPackages()
            val allToUnsuspend = (SAFE_TO_SUSPEND + affectedUserPkgs).distinct()

            shizukuManager.suspendPackages(allToUnsuspend, false)
            shizukuManager.setAppOpMode(allToUnsuspend, 70, 0)
            settingsRepository.setGamingAffectedPackages(emptySet())

            esportsOptimizationEngine.revertOptimizations()

            // Restore DND
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }

            // Restore original settings/overrides
            val prefs = context.getSharedPreferences("framex_settings", Context.MODE_PRIVATE)
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // 1. Ringtone volume
            val origVol = prefs.getInt("orig_ringtone_val", -1)
            if (origVol != -1) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, origVol, 0)
                } catch (e: Exception) {
                    com.framex.app.utils.FrameXLog.w("Failed to restore ringtone volume", e)
                }
                prefs.edit().remove("orig_ringtone_val").apply()
            }

            // 2. Settings (brightness, rotation)
            if (Settings.System.canWrite(context)) {
                val origMode = prefs.getInt("orig_brightness_mode", -1)
                if (origMode != -1) {
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, origMode)
                    prefs.edit().remove("orig_brightness_mode").apply()
                }
                val origRotate = prefs.getInt("orig_rotation_mode", -1)
                if (origRotate != -1) {
                    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, origRotate)
                    prefs.edit().remove("orig_rotation_mode").apply()
                }
            }

        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.w("Error during Gaming Mode deactivation", e)
        }

        settingsRepository.setGamingModeActive(false)
        _isActive.value = false
        _state.value = GamingModeState.Idle
    }

    /** Called on app start-up to recover state that was active before a kill. */
    fun recoverPersistedState() {
        if (settingsRepository.isGamingModeActive()) {
            _isActive.value = true
            _state.value = GamingModeState.Active
            if (!shizukuManager.isShizukuAvailable.value || !shizukuManager.hasPermission.value) {
                showRecoveryNotification()
            }
        }
    }

    private fun showRecoveryNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = Intent(context, com.framex.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = android.app.PendingIntent.getActivity(
            context, 0, tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, GamingModeService.CHANNEL_ID)
            .setContentTitle("Gaming Mode Interrupted")
            .setContentText("Tap to connect Shizuku and restore your apps.")
            .setSmallIcon(com.framex.app.R.drawable.ic_notification)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        nm.notify(RECOVERY_NOTIFICATION_ID, notification)
    }
}
