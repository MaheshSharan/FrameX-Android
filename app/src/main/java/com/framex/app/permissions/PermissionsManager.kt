package com.framex.app.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.framex.app.privilege.PrivilegeManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner of system permission state. Screens observe [status] and never poll the
 * platform themselves; the process lifecycle observer in the Application calls [refresh]
 * whenever the app returns to the foreground, which is where grants change.
 */
@Singleton
class PermissionsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    privilegeManager: PrivilegeManager
) {
    // Lives as long as the process; refreshes are short and need no cancellation or cleanup.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(AppPermission.entries.associateWith { false })

    /** Last known grant state of every [AppPermission]. */
    val status: StateFlow<Map<AppPermission, Boolean>> = _status.asStateFlow()

    /** True when the selected privilege backend is connected and every essential permission is granted. */
    val isReady: StateFlow<Boolean> = combine(_status, privilegeManager.isReady, ::isSetupReady)
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Re-reads every permission off the main thread. Safe to call repeatedly. */
    fun refresh() {
        scope.launch { _status.value = AppPermission.entries.associateWith(::isGranted) }
    }

    /** Fresh platform check, for callers that cannot rely on the cached [status] (boot, tile). */
    fun isGranted(permission: AppPermission): Boolean = when (permission) {
        AppPermission.OVERLAY -> Settings.canDrawOverlays(context)
        AppPermission.NOTIFICATIONS -> hasNotificationPermission()
        AppPermission.USAGE_STATS -> hasUsageStatsAccess()
        AppPermission.BATTERY_OPT -> context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
        AppPermission.WRITE_SETTINGS -> Settings.System.canWrite(context)
        AppPermission.DND -> context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
        AppPermission.NOTIF_LISTENER ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

/** The one readiness rule shared by the Dashboard, System Status and onboarding. */
internal fun isSetupReady(status: Map<AppPermission, Boolean>, privilegeReady: Boolean): Boolean =
    privilegeReady && AppPermission.ESSENTIAL.all { status[it] == true }
