package com.framex.app.ui.screens.about

import androidx.compose.runtime.Immutable
import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.DownloadState
import java.io.File

enum class ExecutionCenterTab(val label: String) {
    COMMON("Common"),
    GENERIC("Generic"),
    VIVO("Vivo"),
    IQOO("iQOO")
}

@Immutable
data class AboutUiState(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val autoUpdateEnabled: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val statusMessage: String? = null,
    val updateInfoState: AppUpdateInfo? = null,
    val signatureErrorMessage: String? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val isVivoDevice: Boolean = false,
    val isIqooDevice: Boolean = false,
    val isVivoOptActive: Boolean = false,
    val showVivoDiagModal: Boolean = false,
    val selectedExecutionTab: ExecutionCenterTab = ExecutionCenterTab.COMMON,
    val disableThermalThrottling: Boolean = false,
    val ramCachePreTrimEnabled: Boolean = true,
    val disablePhantomProcKiller: Boolean = true,
    val cpuPriorityLock: Boolean = true,
    val fixedPerformanceMode: Boolean = false,
    val networkFirewall: Boolean = false,
    val refreshRateLock: Boolean = false,
    val touchBoost: Boolean = false,
    val vivoMonsterMode: Boolean = false,
    val vivoVipThread: Boolean = false,
    val vivoGameHandshake: Boolean = false,
    val vivoGyroPromotion: Boolean = false,
    val vivoTouchOptimization: Boolean = false,
    val vivo144FpsUnlock: Boolean = false,
    val vivoMaintenancePulse: Boolean = false,
    val vivoPulseGamePlusMode: Boolean = false,
    val vivoPulseStandardPromotion: Boolean = false,
    val vivoPulseSceneMoreFps: Boolean = false,
    val maxRefreshRate: Int = 60,
    val hasCrashLog: Boolean = false,
    val pendingInstallApk: File? = null,
    val waitingForInstallPermission: Boolean = false
)

sealed interface AboutUiEvent {
    data class SetAutoUpdateCheck(val enabled: Boolean) : AboutUiEvent
    data object CheckForUpdates : AboutUiEvent
    data class SetVivoOptEnabled(val enabled: Boolean) : AboutUiEvent
    data class SetShowVivoDiagModal(val show: Boolean) : AboutUiEvent
    data class SelectExecutionCenterTab(val tab: ExecutionCenterTab) : AboutUiEvent
    data class SetDisableThermalThrottling(val enabled: Boolean) : AboutUiEvent
    data class SetRamCachePreTrim(val enabled: Boolean) : AboutUiEvent
    data class SetDisablePhantomProcKiller(val enabled: Boolean) : AboutUiEvent
    data class SetCpuPriorityLock(val enabled: Boolean) : AboutUiEvent
    data class SetFixedPerformanceMode(val enabled: Boolean) : AboutUiEvent
    data class SetNetworkFirewall(val enabled: Boolean) : AboutUiEvent
    data class SetRefreshRateLock(val enabled: Boolean) : AboutUiEvent
    data class SetTouchBoost(val enabled: Boolean) : AboutUiEvent
    data class SetVivoMonsterMode(val enabled: Boolean) : AboutUiEvent
    data class SetVivoVipThread(val enabled: Boolean) : AboutUiEvent
    data class SetVivoGameHandshake(val enabled: Boolean) : AboutUiEvent
    data class SetVivoGyroPromotion(val enabled: Boolean) : AboutUiEvent
    data class SetVivoTouchOptimization(val enabled: Boolean) : AboutUiEvent
    data class SetVivo144FpsUnlock(val enabled: Boolean) : AboutUiEvent
    data class SetVivoMaintenancePulse(val enabled: Boolean) : AboutUiEvent
    data class SetVivoPulseGamePlusMode(val enabled: Boolean) : AboutUiEvent
    data class SetVivoPulseStandardPromotion(val enabled: Boolean) : AboutUiEvent
    data class SetVivoPulseSceneMoreFps(val enabled: Boolean) : AboutUiEvent
    data object ShareCrashLog : AboutUiEvent
    data object ClearCrashLog : AboutUiEvent
    data class SetUpdateInfo(val info: AppUpdateInfo?) : AboutUiEvent
    data class SetSignatureError(val message: String?) : AboutUiEvent
    data class InstallDownloadedApk(val apkFile: File) : AboutUiEvent
    data object ResumeOrDownloadUpdate : AboutUiEvent
    data object CancelOrResetDownload : AboutUiEvent
    data object HandleSignatureMismatchUninstall : AboutUiEvent
    data object OnResumeCheckInstallPermission : AboutUiEvent
}

sealed interface AboutUiEffect {
    data class ShowToast(val message: String) : AboutUiEffect
    data object OpenUnknownSourcesSettings : AboutUiEffect
    data class OpenBrowser(val url: String) : AboutUiEffect
}
