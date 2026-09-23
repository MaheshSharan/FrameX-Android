package com.framex.app.ui.screens.splash

import androidx.compose.runtime.Immutable
import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.DownloadState
import java.io.File

@Immutable
data class SplashUiState(
    val isOnboardingCompleted: Boolean = false,
    val autoUpdateEnabled: Boolean = false,
    val isCheckingUpdates: Boolean = false,
    val updateInfoState: AppUpdateInfo? = null,
    val signatureErrorMessage: String? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val pendingInstallApk: File? = null,
    val waitingForInstallPermission: Boolean = false
)

sealed interface SplashUiEvent {
    data object StartInitialization : SplashUiEvent
    data object ProceedToNextScreen : SplashUiEvent
    data class InstallDownloadedApk(val apkFile: File) : SplashUiEvent
    data object ResumeOrDownloadUpdate : SplashUiEvent
    data object CancelOrResetDownload : SplashUiEvent
    data object DismissUpdateDialog : SplashUiEvent
    data object HandleSignatureMismatchUninstall : SplashUiEvent
    data object DismissSignatureError : SplashUiEvent
    data object OnResumeCheckInstallPermission : SplashUiEvent
}

sealed interface SplashUiEffect {
    data object NavigateToOnboarding : SplashUiEffect
    data object NavigateToDashboard : SplashUiEffect
    data object OpenUnknownSourcesSettings : SplashUiEffect
}
