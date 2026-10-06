package com.framex.app.ui.screens.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.BuildConfig
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.gaming.VivoSuiteGate
import com.framex.app.repository.CrashLogRepository
import com.framex.app.repository.SettingsRepository
import com.framex.app.update.AppUpdateInfo
import com.framex.app.update.InstallResult
import com.framex.app.update.UpdateInstaller
import com.framex.app.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val autoUpdate: Boolean
)

private data class CommonSnapshot(
    val tab: ExecutionCenterTab,
    val thermal: Boolean,
    val ram: Boolean,
    val phantom: Boolean,
    val cpu: Boolean
)

private data class GenericSnapshot(
    val perf: Boolean,
    val net: Boolean,
    val refresh: Boolean,
    val touch: Boolean
)

private data class VivoSnapshot(
    val monster: Boolean,
    val vip: Boolean,
    val handshake: Boolean,
    val gyro: Boolean,
    val touch: Boolean,
    val fps144: Boolean,
    val pulse: Boolean,
    val pulseGamePlus: Boolean,
    val pulseStdPromo: Boolean,
    val pulseSceneFps: Boolean
)

private data class ExecutionCenterSnapshot(
    val selectedTab: ExecutionCenterTab,
    val disableThermal: Boolean,
    val ramCachePreTrim: Boolean,
    val disablePhantom: Boolean,
    val cpuPriority: Boolean,
    val fixedPerf: Boolean,
    val networkFirewall: Boolean,
    val refreshRateLock: Boolean,
    val touchBoost: Boolean,
    val vivoMonster: Boolean,
    val vivoVipThread: Boolean,
    val vivoHandshake: Boolean,
    val vivoGyro: Boolean,
    val vivoTouch: Boolean,
    val vivo144Fps: Boolean,
    val vivoPulse: Boolean,
    val vivoPulseGamePlus: Boolean,
    val vivoPulseStdPromo: Boolean,
    val vivoPulseSceneFps: Boolean
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
    private val settingsRepository: SettingsRepository,
    private val crashLogRepository: CrashLogRepository,
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val updateRepository: UpdateRepository,
    private val updateInstaller: UpdateInstaller,
    private val gamingModeEngine: GamingModeEngine,
    private val vivoSuiteGate: VivoSuiteGate
) : ViewModel() {

    private val _effectChannel = Channel<AboutUiEffect>(Channel.BUFFERED)
    val effect = _effectChannel.receiveAsFlow()

    val isVivoHardware: Boolean get() = deviceDiagnosticManager.isVivoOnly()
    val isIqooHardware: Boolean get() = deviceDiagnosticManager.isIqooOnly()
    val deviceModelInfo: String get() = deviceDiagnosticManager.getDeviceModelInfo()

    fun canInstallPackages(): Boolean = updateInstaller.canInstallPackages()
    fun openUnknownAppSourcesSettings() = updateInstaller.openUnknownAppSourcesSettings()

    private val _versionName = MutableStateFlow(BuildConfig.VERSION_NAME)
    private val _versionCode = MutableStateFlow(BuildConfig.VERSION_CODE.toLong())

    private val _selectedExecutionTab = MutableStateFlow(ExecutionCenterTab.COMMON)
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
        settingsRepository.autoUpdateCheckEnabled
    ) { vivo, auto ->
        SettingsSnapshot(vivo, auto)
    }

    private val executionCenterCommonStream = combine(
        _selectedExecutionTab,
        settingsRepository.disableThermalThrottling,
        settingsRepository.ramCachePreTrimEnabled,
        settingsRepository.disablePhantomProcKiller,
        settingsRepository.cpuPriorityLock
    ) { tab, thermal, ram, phantom, cpu ->
        CommonSnapshot(tab, thermal, ram, phantom, cpu)
    }

    private val executionCenterGenericStream = combine(
        settingsRepository.fixedPerformanceMode,
        settingsRepository.networkFirewall,
        settingsRepository.refreshRateLock,
        settingsRepository.touchBoost
    ) { perf, net, refresh, touch ->
        GenericSnapshot(perf, net, refresh, touch)
    }

    private val executionCenterVivoStream = combine(
        settingsRepository.vivoMonsterMode,
        settingsRepository.vivoVipThread,
        settingsRepository.vivoGameHandshake,
        settingsRepository.vivoGyroPromotion,
        settingsRepository.vivoTouchOptimization,
        settingsRepository.vivo144FpsUnlock,
        settingsRepository.vivoMaintenancePulse,
        settingsRepository.vivoPulseGamePlusMode,
        settingsRepository.vivoPulseStandardPromotion,
        settingsRepository.vivoPulseSceneMoreFps
    ) { args: Array<Boolean> ->
        VivoSnapshot(
            monster = args[0],
            vip = args[1],
            handshake = args[2],
            gyro = args[3],
            touch = args[4],
            fps144 = args[5],
            pulse = args[6],
            pulseGamePlus = args[7],
            pulseStdPromo = args[8],
            pulseSceneFps = args[9]
        )
    }

    private val executionCenterStream = combine(
        executionCenterCommonStream,
        executionCenterGenericStream,
        executionCenterVivoStream
    ) { common, generic, vivo ->
        ExecutionCenterSnapshot(
            selectedTab = common.tab,
            disableThermal = common.thermal,
            ramCachePreTrim = common.ram,
            disablePhantom = common.phantom,
            cpuPriority = common.cpu,
            fixedPerf = generic.perf,
            networkFirewall = generic.net,
            refreshRateLock = generic.refresh,
            touchBoost = generic.touch,
            vivoMonster = vivo.monster,
            vivoVipThread = vivo.vip,
            vivoHandshake = vivo.handshake,
            vivoGyro = vivo.gyro,
            vivoTouch = vivo.touch,
            vivo144Fps = vivo.fps144,
            vivoPulse = vivo.pulse,
            vivoPulseGamePlus = vivo.pulseGamePlus,
            vivoPulseStdPromo = vivo.pulseStdPromo,
            vivoPulseSceneFps = vivo.pulseSceneFps
        )
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
        executionCenterStream,
        updateRepository.downloadState,
        updateActionStream,
        combine(_versionName, _versionCode) { name, code -> Pair(name, code) }
    ) { settings, exec, downloadState, actions, (versionName, versionCode) ->
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
            isIqooDevice = isIqooHardware,
            isVivoOptActive = settings.vivoOpt,
            showVivoDiagModal = actions.showVivoModal,
            selectedExecutionTab = exec.selectedTab,
            disableThermalThrottling = exec.disableThermal,
            ramCachePreTrimEnabled = exec.ramCachePreTrim,
            disablePhantomProcKiller = exec.disablePhantom,
            cpuPriorityLock = exec.cpuPriority,
            fixedPerformanceMode = exec.fixedPerf,
            networkFirewall = exec.networkFirewall,
            refreshRateLock = exec.refreshRateLock,
            touchBoost = exec.touchBoost,
            vivoMonsterMode = exec.vivoMonster,
            vivoVipThread = exec.vivoVipThread,
            vivoGameHandshake = exec.vivoHandshake,
            vivoGyroPromotion = exec.vivoGyro,
            vivoTouchOptimization = exec.vivoTouch,
            vivo144FpsUnlock = exec.vivo144Fps,
            vivoMaintenancePulse = exec.vivoPulse,
            vivoPulseGamePlusMode = exec.vivoPulseGamePlus,
            vivoPulseStandardPromotion = exec.vivoPulseStdPromo,
            vivoPulseSceneMoreFps = exec.vivoPulseSceneFps,
            maxRefreshRate = deviceDiagnosticManager.getMaxHardwareRefreshRate().toInt().coerceAtLeast(60),
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
        _versionName.value = BuildConfig.VERSION_NAME
        _versionCode.value = BuildConfig.VERSION_CODE.toLong()
        viewModelScope.launch(Dispatchers.IO) {
            _hasCrashLog.value = crashLogRepository.hasCrashLog()
        }
    }

    fun onEvent(event: AboutUiEvent) {
        when (event) {
            is AboutUiEvent.SetAutoUpdateCheck -> settingsRepository.setAutoUpdateCheckEnabled(event.enabled)
            is AboutUiEvent.SelectExecutionCenterTab -> _selectedExecutionTab.value = event.tab
            is AboutUiEvent.SetDisableThermalThrottling -> settingsRepository.setDisableThermalThrottling(event.enabled)
            is AboutUiEvent.SetRamCachePreTrim -> settingsRepository.setRamCachePreTrimEnabled(event.enabled)
            is AboutUiEvent.SetDisablePhantomProcKiller -> settingsRepository.setDisablePhantomProcKiller(event.enabled)
            is AboutUiEvent.SetCpuPriorityLock -> settingsRepository.setCpuPriorityLock(event.enabled)
            is AboutUiEvent.SetFixedPerformanceMode -> settingsRepository.setFixedPerformanceMode(event.enabled)
            is AboutUiEvent.SetNetworkFirewall -> settingsRepository.setNetworkFirewall(event.enabled)
            is AboutUiEvent.SetRefreshRateLock -> settingsRepository.setRefreshRateLock(event.enabled)
            is AboutUiEvent.SetTouchBoost -> settingsRepository.setTouchBoost(event.enabled)
            is AboutUiEvent.SetVivoMonsterMode -> settingsRepository.setVivoMonsterMode(event.enabled)
            is AboutUiEvent.SetVivoVipThread -> settingsRepository.setVivoVipThread(event.enabled)
            is AboutUiEvent.SetVivoGameHandshake -> settingsRepository.setVivoGameHandshake(event.enabled)
            is AboutUiEvent.SetVivoGyroPromotion -> settingsRepository.setVivoGyroPromotion(event.enabled)
            is AboutUiEvent.SetVivoTouchOptimization -> settingsRepository.setVivoTouchOptimization(event.enabled)
            is AboutUiEvent.SetVivo144FpsUnlock -> settingsRepository.setVivo144FpsUnlock(event.enabled)
            is AboutUiEvent.SetVivoMaintenancePulse -> settingsRepository.setVivoMaintenancePulse(event.enabled)
            is AboutUiEvent.SetVivoPulseGamePlusMode -> settingsRepository.setVivoPulseGamePlusMode(event.enabled)
            is AboutUiEvent.SetVivoPulseStandardPromotion -> settingsRepository.setVivoPulseStandardPromotion(event.enabled)
            is AboutUiEvent.SetVivoPulseSceneMoreFps -> settingsRepository.setVivoPulseSceneMoreFps(event.enabled)
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
            AboutUiEvent.OnResumeCheckInstallPermission -> {
                checkPendingInstallOnResume()
                checkCrashLog()
            }
            AboutUiEvent.ShareCrashLog -> crashLogRepository.shareCrashLog()
            AboutUiEvent.ClearCrashLog -> {
                crashLogRepository.clearCrashLog()
                _hasCrashLog.value = false
            }
        }
    }

    fun checkCrashLog() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasCrashLog.value = crashLogRepository.hasCrashLog()
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
