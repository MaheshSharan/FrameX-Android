package com.framex.app.ui.screens.about

import androidx.compose.runtime.Immutable
import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.DownloadState
import java.io.File

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
    val isVivoOptActive: Boolean = false,
    val showVivoDiagModal: Boolean = false,
    val disableThermalThrottling: Boolean = false,
    val hasCrashLog: Boolean = false,
    val pendingInstallApk: File? = null,
    val waitingForInstallPermission: Boolean = false
)

sealed interface AboutUiEvent {
    data class SetAutoUpdateCheck(val enabled: Boolean) : AboutUiEvent
    data object CheckForUpdates : AboutUiEvent
    data class SetVivoOptEnabled(val enabled: Boolean) : AboutUiEvent
    data class SetShowVivoDiagModal(val show: Boolean) : AboutUiEvent
    data class SetDisableThermalThrottling(val enabled: Boolean) : AboutUiEvent
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
    data class ShareLogIntent(val file: File) : AboutUiEffect
}
