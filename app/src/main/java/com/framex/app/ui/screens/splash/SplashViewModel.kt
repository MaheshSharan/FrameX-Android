package com.framex.app.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.BuildConfig
import com.framex.app.repository.SettingsRepository
import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.InstallResult
import com.framex.app.update.UpdateInstaller
import com.framex.app.update.UpdateInstallerBus
import com.framex.app.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    val updateRepository: UpdateRepository,
    val updateInstaller: UpdateInstaller
) : ViewModel() {

    private val _effectChannel = Channel<SplashUiEffect>(Channel.BUFFERED)
    val effect = _effectChannel.receiveAsFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    private val _updateInfoState = MutableStateFlow<AppUpdateInfo?>(null)
    private val _signatureErrorMessage = MutableStateFlow<String?>(null)
    private val _pendingInstallApk = MutableStateFlow<File?>(null)
    private val _waitingForInstallPermission = MutableStateFlow(false)

    private var hasInitialized = false

    val uiState: StateFlow<SplashUiState> = combine(
        settingsRepository.isOnboardingCompleted,
        settingsRepository.autoUpdateCheckEnabled,
        updateRepository.downloadState,
        _isCheckingUpdates,
        _updateInfoState
    ) { completed, autoUpdate, downloadState, isChecking, updateInfo ->
        SplashUiState(
            isOnboardingCompleted = completed,
            autoUpdateEnabled = autoUpdate,
            isCheckingUpdates = isChecking,
            updateInfoState = updateInfo,
            signatureErrorMessage = _signatureErrorMessage.value,
            downloadState = downloadState,
            pendingInstallApk = _pendingInstallApk.value,
            waitingForInstallPermission = _waitingForInstallPermission.value
        )
    }.combine(_signatureErrorMessage) { state, sigError ->
        state.copy(signatureErrorMessage = sigError)
    }.combine(_pendingInstallApk) { state, pendingApk ->
        state.copy(pendingInstallApk = pendingApk)
    }.combine(_waitingForInstallPermission) { state, waitingPerm ->
        state.copy(waitingForInstallPermission = waitingPerm)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SplashUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            updateRepository.cleanupStaleUpdateApks()
        }
        observeInstallBus()
    }

    private fun observeInstallBus() {
        viewModelScope.launch {
            UpdateInstallerBus.installEvents.collect { result ->
                when (result) {
                    is InstallResult.SignatureMismatch -> {
                        _signatureErrorMessage.value = result.errorMessage
                        _updateInfoState.value = null
                    }
                    is InstallResult.PermissionRequired -> {
                        _waitingForInstallPermission.value = true
                        _effectChannel.send(SplashUiEffect.OpenUnknownSourcesSettings)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onEvent(event: SplashUiEvent) {
        when (event) {
            SplashUiEvent.StartInitialization -> startInitialization()
            SplashUiEvent.ProceedToNextScreen -> proceedToNextScreen()
            is SplashUiEvent.InstallDownloadedApk -> installDownloadedApk(event.apkFile)
            SplashUiEvent.ResumeOrDownloadUpdate -> {
                val info = _updateInfoState.value
                if (info != null) {
                    updateRepository.requestDownloadOrResume(info)
                }
            }
            SplashUiEvent.CancelOrResetDownload -> updateRepository.resetDownloadState()
            SplashUiEvent.DismissUpdateDialog -> {
                _updateInfoState.value = null
                updateRepository.resetDownloadState()
                proceedToNextScreen()
            }
            SplashUiEvent.HandleSignatureMismatchUninstall -> handleSignatureMismatch()
            SplashUiEvent.DismissSignatureError -> {
                _signatureErrorMessage.value = null
                proceedToNextScreen()
            }
            SplashUiEvent.OnResumeCheckInstallPermission -> checkPendingInstallOnResume()
        }
    }

    private fun startInitialization() {
        if (hasInitialized) return
        hasInitialized = true

        viewModelScope.launch {
            val autoUpdate = settingsRepository.autoUpdateCheckEnabled.first()
            if (autoUpdate) {
                _isCheckingUpdates.value = true
                val checkJob = withTimeoutOrNull(3000L) {
                    runCatching { updateRepository.checkForUpdates() }.getOrNull()
                }
                _isCheckingUpdates.value = false

                if (checkJob != null && checkJob.isSuccess) {
                    val info = checkJob.getOrNull()
                    if (info != null && info.isUpdateAvailable) {
                        _updateInfoState.value = info
                        return@launch
                    }
                }
                delay(600)
                proceedToNextScreen()
            } else {
                delay(1200)
                proceedToNextScreen()
            }
        }
    }

    private fun proceedToNextScreen() {
        viewModelScope.launch {
            val completed = settingsRepository.isOnboardingCompleted.first()
            if (completed) {
                _effectChannel.send(SplashUiEffect.NavigateToDashboard)
            } else {
                _effectChannel.send(SplashUiEffect.NavigateToOnboarding)
            }
        }
    }

    private fun installDownloadedApk(apkFile: File) {
        _pendingInstallApk.value = apkFile
        viewModelScope.launch {
            updateInstaller.installApk(apkFile) { installResult ->
                when (installResult) {
                    is InstallResult.PermissionRequired -> {
                        _waitingForInstallPermission.value = true
                        _effectChannel.trySend(SplashUiEffect.OpenUnknownSourcesSettings)
                    }
                    is InstallResult.SignatureMismatch -> {
                        _signatureErrorMessage.value = installResult.errorMessage
                        _updateInfoState.value = null
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun checkPendingInstallOnResume() {
        val apkFile = _pendingInstallApk.value
        if (_waitingForInstallPermission.value && apkFile != null && updateInstaller.canInstallPackages()) {
            _waitingForInstallPermission.value = false
            installDownloadedApk(apkFile)
        }
    }

    private fun handleSignatureMismatch() {
        viewModelScope.launch {
            val targetVer = _updateInfoState.value?.versionName ?: BuildConfig.VERSION_NAME
            val apkFile = updateRepository.getCachedApkIfValid(targetVer, _updateInfoState.value?.sha256)
            updateInstaller.handleSignatureMismatch(apkFile, targetVer) {
                _signatureErrorMessage.value = null
                _updateInfoState.value = null
                proceedToNextScreen()
            }
        }
    }
}
