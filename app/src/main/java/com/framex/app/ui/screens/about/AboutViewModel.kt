package com.framex.app.ui.screens.about

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.BuildConfig
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.gaming.VivoSuiteGate
import com.framex.app.repository.SettingsRepository
import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.InstallResult
import com.framex.app.update.UpdateInstaller
import com.framex.app.update.UpdateRepository
import com.framex.app.utils.CrashHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private data class SettingsSnapshot(
    val vivoOpt: Boolean,
    val autoUpdate: Boolean,
    val disableThermal: Boolean
)

private data class UpdateActionSnapshot(
    val isChecking: Boolean,
    val statusMsg: String?,
    val updateInfo: AppUpdateInfo?,
    val signatureError: String?,
    val showVivoModal: Boolean,
    val hasCrashLog: Boolean,
    val pendingApk: File?,
    val waitingForInstall: Boolean
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    val deviceDiagnosticManager: DeviceDiagnosticManager,
    val updateRepository: UpdateRepository,
    val updateInstaller: UpdateInstaller,
    private val gamingModeEngine: GamingModeEngine,
    val vivoSuiteGate: VivoSuiteGate
) : ViewModel() {

    private val _effectChannel = Channel<AboutUiEffect>(Channel.BUFFERED)
    val effect = _effectChannel.receiveAsFlow()

    val isVivoHardware: Boolean get() = vivoSuiteGate.isVivoHardware

    private val _versionName = MutableStateFlow(BuildConfig.VERSION_NAME)
    private val _versionCode = MutableStateFlow(BuildConfig.VERSION_CODE.toLong())

    private val _isCheckingUpdate = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _updateInfoState = MutableStateFlow<AppUpdateInfo?>(null)
    private val _signatureErrorMessage = MutableStateFlow<String?>(null)
    private val _showVivoDiagModal = MutableStateFlow(false)
    private val _hasCrashLog = MutableStateFlow(false)
    private val _pendingInstallApk = MutableStateFlow<File?>(null)
    private val _waitingForInstallPermission = MutableStateFlow(false)

    private val settingsStream = combine(
        settingsRepository.vivoOptEnabled,
        settingsRepository.autoUpdateCheckEnabled,
        settingsRepository.disableThermalThrottling
    ) { vivo, auto, thermal ->
        SettingsSnapshot(vivo, auto, thermal)
    }

    private val updateActionStream = combine(
        combine(_isCheckingUpdate, _statusMessage) { c, m -> Pair(c, m) },
        combine(_updateInfoState, _signatureErrorMessage) { u, s -> Pair(u, s) },
        combine(_showVivoDiagModal, _hasCrashLog) { v, h -> Pair(v, h) },
        combine(_pendingInstallApk, _waitingForInstallPermission) { p, w -> Pair(p, w) }
    ) { (isChecking, statusMsg), (updateInfo, sigError), (vivoModal, crashLog), (pendingApk, waitingInstall) ->
        UpdateActionSnapshot(
            isChecking = isChecking,
            statusMsg = statusMsg,
            updateInfo = updateInfo,
            signatureError = sigError,
            showVivoModal = vivoModal,
            hasCrashLog = crashLog,
            pendingApk = pendingApk,
            waitingForInstall = waitingInstall
        )
    }

    val uiState: StateFlow<AboutUiState> = combine(
        settingsStream,
        updateRepository.downloadState,
        updateActionStream,
        combine(_versionName, _versionCode) { name, code -> Pair(name, code) }
    ) { settings, downloadState, actions, (versionName, versionCode) ->
        AboutUiState(
            versionName = versionName,
            versionCode = versionCode,
            autoUpdateEnabled = settings.autoUpdate,
            isCheckingUpdate = actions.isChecking,
            statusMessage = actions.statusMsg,
            updateInfoState = actions.updateInfo,
            signatureErrorMessage = actions.signatureError,
            downloadState = downloadState,
            isVivoDevice = isVivoHardware,
            isVivoOptActive = settings.vivoOpt,
            showVivoDiagModal = actions.showVivoModal,
            disableThermalThrottling = settings.disableThermal,
            hasCrashLog = actions.hasCrashLog,
            pendingInstallApk = actions.pendingApk,
            waitingForInstallPermission = actions.waitingForInstall
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AboutUiState())

    init {
        loadPackageAndCrashInfo()
        observeToastEvents()
    }

    private fun observeToastEvents() {
        viewModelScope.launch {
            gamingModeEngine.toastEvents.collect { msg ->
                _effectChannel.send(AboutUiEffect.ShowToast(msg))
            }
        }
    }

    private fun loadPackageAndCrashInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                _versionName.value = pInfo?.versionName ?: BuildConfig.VERSION_NAME
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo?.longVersionCode ?: BuildConfig.VERSION_CODE.toLong()
                } else {
                    @Suppress("DEPRECATION")
                    pInfo?.versionCode?.toLong() ?: BuildConfig.VERSION_CODE.toLong()
                }
                _versionCode.value = code
            }
            _hasCrashLog.value = CrashHandler.hasCrashLog(context)
        }
    }

    fun onEvent(event: AboutUiEvent) {
        when (event) {
            is AboutUiEvent.SetAutoUpdateCheck -> settingsRepository.setAutoUpdateCheckEnabled(event.enabled)
            is AboutUiEvent.SetDisableThermalThrottling -> settingsRepository.setDisableThermalThrottling(event.enabled)
            is AboutUiEvent.SetVivoOptEnabled -> {
                val wasEnabled = settingsRepository.vivoOptEnabled.value
                settingsRepository.setVivoOptEnabled(event.enabled)
                if (!event.enabled && wasEnabled) {
                    gamingModeEngine.onVivoOptToggledOffMidSession()
                }
            }
            is AboutUiEvent.SetShowVivoDiagModal -> _showVivoDiagModal.value = event.show
            AboutUiEvent.CheckForUpdates -> performUpdateCheck()
            is AboutUiEvent.SetUpdateInfo -> _updateInfoState.value = event.info
            is AboutUiEvent.SetSignatureError -> _signatureErrorMessage.value = event.message
            is AboutUiEvent.InstallDownloadedApk -> installDownloadedApk(event.apkFile)
            AboutUiEvent.ResumeOrDownloadUpdate -> {
                val info = _updateInfoState.value
                if (info != null) {
                    updateRepository.requestDownloadOrResume(info)
                }
            }
            AboutUiEvent.CancelOrResetDownload -> updateRepository.resetDownloadState()
            AboutUiEvent.HandleSignatureMismatchUninstall -> handleSignatureMismatch()
            AboutUiEvent.OnResumeCheckInstallPermission -> checkPendingInstallOnResume()
            AboutUiEvent.ShareCrashLog -> CrashHandler.shareCrashLog(context)
            AboutUiEvent.ClearCrashLog -> {
                CrashHandler.clearCrashLog(context)
                _hasCrashLog.value = false
            }
        }
    }

    private fun performUpdateCheck() {
        _isCheckingUpdate.value = true
        _statusMessage.value = "Checking GitHub..."
        viewModelScope.launch {
            val result = updateRepository.checkForUpdates()
            _isCheckingUpdate.value = false
            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    _updateInfoState.value = info
                    _statusMessage.value = "Update available: v${info.versionName}"
                } else {
                    _statusMessage.value = "FrameX is up to date (v${_versionName.value})"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Check failed"
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
                        _effectChannel.trySend(AboutUiEffect.OpenUnknownSourcesSettings)
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
            }
        }
    }
}
