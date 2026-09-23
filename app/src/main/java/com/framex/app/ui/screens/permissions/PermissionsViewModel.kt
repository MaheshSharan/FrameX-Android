package com.framex.app.ui.screens.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    val isShizukuAvailable: StateFlow<Boolean> = shizukuManager.isShizukuAvailable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasShizukuPermission: StateFlow<Boolean> = shizukuManager.hasPermission
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val systemPermissionsState = MutableStateFlow(readSystemPermissions())

    val uiState: StateFlow<PermissionsUiState> = combine(
        isShizukuAvailable,
        hasShizukuPermission,
        systemPermissionsState
    ) { isAvailable, hasPermission, sysState ->
        PermissionsUiState(
            isShizukuAvailable = isAvailable,
            hasShizukuPermission = hasPermission,
            hasOverlayPermission = sysState.hasOverlayPermission,
            hasUsageStatsPermission = sysState.hasUsageStatsPermission,
            hasBatteryOptDisabled = sysState.hasBatteryOptDisabled,
            hasNotificationPermission = sysState.hasNotificationPermission,
            hasWriteSettingsPermission = sysState.hasWriteSettingsPermission
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PermissionsUiState()
    )

    fun onEvent(event: PermissionsUiEvent) {
        when (event) {
            PermissionsUiEvent.RequestShizukuPermission -> requestShizukuPermission()
            PermissionsUiEvent.RefreshPermissions -> refreshPermissions()
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun refreshPermissions() {
        shizukuManager.refreshState()
        systemPermissionsState.value = readSystemPermissions()
    }

    fun updateNotificationPermission(granted: Boolean) {
        systemPermissionsState.value = systemPermissionsState.value.copy(
            hasNotificationPermission = granted
        )
    }

    private fun readSystemPermissions(): PermissionsUiState {
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasWriteSettings = Settings.System.canWrite(context)
        val hasUsage = checkUsageStatsPermission()
        val hasBattery = checkBatteryOptimizationDisabled()
        val hasNotifications = checkNotificationPermission()

        return PermissionsUiState(
            hasOverlayPermission = hasOverlay,
            hasUsageStatsPermission = hasUsage,
            hasBatteryOptDisabled = hasBattery,
            hasNotificationPermission = hasNotifications,
            hasWriteSettingsPermission = hasWriteSettings
        )
    }

    private fun checkNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkBatteryOptimizationDisabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
