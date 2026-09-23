package com.framex.app.ui.screens.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.device.StorageInfo
import com.framex.app.gaming.AppInfo
import com.framex.app.gaming.EsportsOptimizationEngine
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.GamingPlatformPath
import com.framex.app.gaming.GamingServiceController
import com.framex.app.gaming.SystemAuditLog
import com.framex.app.gaming.VivoGamingOptimizer
import com.framex.app.gaming.VivoSuiteGate
import com.framex.app.gaming.ledger.ExecutionLedger
import com.framex.app.metrics.MetricsEngine
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val gamingModeEngine: GamingModeEngine,
    private val esportsOptimizationEngine: EsportsOptimizationEngine,
    private val vivoGamingOptimizer: VivoGamingOptimizer,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val metricsEngine: MetricsEngine,
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val executionLedger: ExecutionLedger,
    private val vivoSuiteGate: VivoSuiteGate,
    private val gamingServiceController: GamingServiceController
) : ViewModel() {

    private val _effectChannel = Channel<PerformanceUiEffect>(Channel.BUFFERED)
    val effect = _effectChannel.receiveAsFlow()

    val maxRefreshRate: Int = deviceDiagnosticManager.getMaxHardwareRefreshRate().toInt().coerceAtLeast(60)

    // Dynamic state streams
    private val _userApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val userApps = _userApps.asStateFlow()

    private val _googleApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val googleApps = _googleApps.asStateFlow()

    private val _rawPerfGameList = MutableStateFlow<String?>(null)
    val rawPerfGameList = _rawPerfGameList.asStateFlow()

    private val _vivoPerfGameList = MutableStateFlow<List<String>>(emptyList())
    val vivoPerfGameList = _vivoPerfGameList.asStateFlow()

    // Interactive Action states
    private val _isBoostingRam = MutableStateFlow(false)
    private val _isOptimizingNet = MutableStateFlow(false)
    private val _isResettingDefaults = MutableStateFlow(false)
    private val _bannerMessage = MutableStateFlow<String?>(null)
    private val _activeLatencyDiagnostic = MutableStateFlow<Int?>(null)
    private val _showRamResult = MutableStateFlow(false)
    private val _showPingResult = MutableStateFlow(false)
    private val _showResetResult = MutableStateFlow(false)

    // Dialog & Modal visibility states
    private val _showAddGameSheet = MutableStateFlow(false)
    private val _configGamePkg = MutableStateFlow<String?>(null)
    private val _activeDeployingGamePkg = MutableStateFlow<String?>(null)

    // Storage & System Access state
    private val _storageInfo = MutableStateFlow(deviceDiagnosticManager.getStorageInfo())
    private val _hasDndAccess = MutableStateFlow(deviceDiagnosticManager.hasDndAccess())
    private val _hasNotifListenerAccess = MutableStateFlow(deviceDiagnosticManager.hasNotificationListenerAccess())
    private val _hasWriteSettingsAccess = MutableStateFlow(deviceDiagnosticManager.hasWriteSettingsAccess())

    private val systemAccessStream = combine(
        _storageInfo,
        _hasDndAccess,
        _hasNotifListenerAccess,
        _hasWriteSettingsAccess
    ) { storage, dnd, notif, writeSettings ->
        SystemAccessGroup(storage, dnd, notif, writeSettings)
    }

    // Active session stream
    val activeGamingSession: StateFlow<ActiveGamingSession?> = combine(
        gamingModeEngine.state,
        gamingModeEngine.activeGamePackage,
        gamingModeEngine.suspendedPackagesCount,
        executionLedger.ops
    ) { state, activePkg, suspendedCount, _ ->
        if (state !is GamingModeState.Active) {
            null
        } else {
            ActiveGamingSession(
                title = "Gaming Mode Active",
                isVivoDevice = vivoSuiteGate.isVivoHardware,
                activeGamePackage = activePkg,
                suspendedAppsCount = suspendedCount,
                summary = executionLedger.getSummary()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Grouped streams to stay strictly within combine limits
    private val shizukuAndGamingStream = combine(
        gamingModeEngine.state,
        shizukuManager.isShizukuAvailable,
        shizukuManager.hasPermission,
        settingsRepository.gamingModeWhitelist,
        settingsRepository.launcherGames
    ) { state, available, perm, wl, games ->
        ShizukuAndGamingGroup(state, available, perm, wl, games)
    }

    private val systemSettingsStream = combine(
        settingsRepository.fixedPerformanceMode,
        settingsRepository.deepFreezeEnabled,
        settingsRepository.hasSeenDeepFreezeNotice,
        settingsRepository.auditLoggingEnabled
    ) { fixedPerf, deepFreeze, hasSeenNotice, auditEnabled ->
        SystemSettingsGroup(fixedPerf, deepFreeze, hasSeenNotice, auditEnabled)
    }

    private val vivoStream = combine(
        vivoSuiteGate.isVivoSuiteEnabledFlow,
        _rawPerfGameList,
        _vivoPerfGameList,
        vivoGamingOptimizer.auditLogs
    ) { enabled, raw, list, logs ->
        VivoGroup(enabled, raw, list, logs)
    }

    private val actionStateStream = combine(
        _isBoostingRam,
        _isOptimizingNet,
        _isResettingDefaults,
        _bannerMessage,
        _activeLatencyDiagnostic
    ) { boosting, optimizingNet, resetting, banner, ping ->
        ActionStateGroup(boosting, optimizingNet, resetting, banner, ping)
    }

    private val dialogStateStream = combine(
        _showAddGameSheet,
        _configGamePkg,
        _activeDeployingGamePkg,
        _showRamResult,
        combine(_showPingResult, _showResetResult) { p, r -> Pair(p, r) }
    ) { addGame, configPkg, deployingPkg, ramRes, (pingRes, resetRes) ->
        DialogStateGroup(addGame, configPkg, deployingPkg, ramRes, pingRes, resetRes)
    }

    val uiState: StateFlow<PerformanceUiState> = combine(
        shizukuAndGamingStream,
        systemSettingsStream,
        vivoStream,
        metricsEngine.metricsState,
        combine(
            _userApps,
            _googleApps,
            activeGamingSession,
            combine(actionStateStream, dialogStateStream, systemAccessStream) { actions, dialogs, access ->
                Triple(actions, dialogs, access)
            }
        ) { user, google, session, (actions, dialogs, access) ->
            IntermediateUiState(user, google, session, actions, dialogs, access)
        }
    ) { sg, sys, vivo, metrics, inter ->
        PerformanceUiState(
            gamingState = sg.gamingState,
            isShizukuAvailable = sg.isShizukuAvailable,
            hasShizukuPermission = sg.hasShizukuPermission,
            whitelist = sg.whitelist,
            launcherGames = sg.launcherGames,
            userApps = inter.userApps,
            googleApps = inter.googleApps,
            metricsState = metrics,
            fixedPerformanceMode = sys.fixedPerformanceMode,
            deepFreezeEnabled = sys.deepFreezeEnabled,
            hasSeenDeepFreezeNotice = sys.hasSeenDeepFreezeNotice,
            activeGamingSession = inter.activeSession,
            isVivoSuiteEnabled = vivo.isVivoSuiteEnabled,
            rawPerfGameList = vivo.rawPerfGameList,
            vivoPerfGameList = vivo.vivoPerfGameList,
            vivoAuditLogs = vivo.vivoAuditLogs,
            auditLoggingEnabled = sys.auditLoggingEnabled,
            maxRefreshRate = maxRefreshRate,
            safeToSuspendList = gamingModeEngine.safeToSuspendPackages,
            gamingDaemonsList = if (vivo.isVivoSuiteEnabled) GamingModeEngine.GAMING_DAEMONS else emptyList(),
            storageInfo = inter.systemAccess.storageInfo,
            hasDndAccess = inter.systemAccess.hasDndAccess,
            hasNotifListenerAccess = inter.systemAccess.hasNotifListenerAccess,
            hasWriteSettingsAccess = inter.systemAccess.hasWriteSettingsAccess,
            isBoostingRam = inter.actions.isBoostingRam,
            isOptimizingNet = inter.actions.isOptimizingNet,
            isResettingDefaults = inter.actions.isResettingDefaults,
            bannerMessage = inter.actions.bannerMessage,
            activeLatencyDiagnostic = inter.actions.activeLatencyDiagnostic,
            showRamResult = inter.dialogs.showRamResult,
            showPingResult = inter.dialogs.showPingResult,
            showResetResult = inter.dialogs.showResetResult,
            showAddGameSheet = inter.dialogs.showAddGameSheet,
            configGamePkg = inter.dialogs.configGamePkg,
            activeDeployingGamePkg = inter.dialogs.activeDeployingGamePkg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), createInitialUiState())

    private fun createInitialUiState(): PerformanceUiState {
        val vivoEnabled = vivoSuiteGate.isVivoSuiteEnabled
        val activeSession = if (gamingModeEngine.state.value is GamingModeState.Active) {
            ActiveGamingSession(
                title = "Gaming Mode Active",
                isVivoDevice = vivoSuiteGate.isVivoHardware,
                activeGamePackage = gamingModeEngine.activeGamePackage.value,
                suspendedAppsCount = gamingModeEngine.suspendedPackagesCount.value,
                summary = executionLedger.getSummary()
            )
        } else null

        return PerformanceUiState(
            gamingState = gamingModeEngine.state.value,
            isShizukuAvailable = shizukuManager.isShizukuAvailable.value,
            hasShizukuPermission = shizukuManager.hasPermission.value,
            whitelist = settingsRepository.gamingModeWhitelist.value,
            launcherGames = settingsRepository.launcherGames.value,
            userApps = emptyList(),
            googleApps = emptyList(),
            metricsState = metricsEngine.metricsState.value,
            fixedPerformanceMode = settingsRepository.fixedPerformanceMode.value,
            deepFreezeEnabled = settingsRepository.deepFreezeEnabled.value,
            hasSeenDeepFreezeNotice = settingsRepository.hasSeenDeepFreezeNotice.value,
            activeGamingSession = activeSession,
            isVivoSuiteEnabled = vivoEnabled,
            rawPerfGameList = null,
            vivoPerfGameList = emptyList(),
            vivoAuditLogs = emptyList(),
            auditLoggingEnabled = settingsRepository.auditLoggingEnabled.value,
            maxRefreshRate = maxRefreshRate,
            safeToSuspendList = gamingModeEngine.safeToSuspendPackages,
            gamingDaemonsList = if (vivoEnabled) GamingModeEngine.GAMING_DAEMONS else emptyList(),
            storageInfo = _storageInfo.value,
            hasDndAccess = _hasDndAccess.value,
            hasNotifListenerAccess = _hasNotifListenerAccess.value,
            hasWriteSettingsAccess = _hasWriteSettingsAccess.value,
            isBoostingRam = false,
            isOptimizingNet = false,
            isResettingDefaults = false,
            bannerMessage = null,
            activeLatencyDiagnostic = null,
            showRamResult = false,
            showPingResult = false,
            showResetResult = false,
            showAddGameSheet = false,
            configGamePkg = null,
            activeDeployingGamePkg = null
        )
    }

    init {
        loadUserApps()
        refreshSystemState()
        metricsEngine.setScreenOverrideModules(setOf("cpu", "ram", "ping"), requesterKey = "performance_screen")
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "performance_screen")
    }

    fun refreshSystemState() {
        viewModelScope.launch(Dispatchers.IO) {
            _storageInfo.value = deviceDiagnosticManager.getStorageInfo()
            _hasDndAccess.value = deviceDiagnosticManager.hasDndAccess()
            _hasNotifListenerAccess.value = deviceDiagnosticManager.hasNotificationListenerAccess()
            _hasWriteSettingsAccess.value = deviceDiagnosticManager.hasWriteSettingsAccess()
        }
    }

    fun onEvent(event: PerformanceUiEvent) {
        when (event) {
            is PerformanceUiEvent.ToggleWhitelist -> settingsRepository.toggleGamingWhitelistApp(event.packageName)
            is PerformanceUiEvent.ToggleLauncherGame -> settingsRepository.toggleLauncherGame(event.packageName)
            is PerformanceUiEvent.ToggleFixedPerformanceMode -> settingsRepository.setFixedPerformanceMode(event.enabled)
            is PerformanceUiEvent.ToggleDeepFreeze -> settingsRepository.setDeepFreezeEnabled(event.enabled)
            PerformanceUiEvent.DismissDeepFreezeNotice -> settingsRepository.setHasSeenDeepFreezeNotice(true)
            PerformanceUiEvent.EnableGamingMode -> enableGamingMode()
            PerformanceUiEvent.DisableGamingMode -> disableGamingMode()
            is PerformanceUiEvent.LaunchGame -> launchGameWithOptimizations(event.packageName)
            PerformanceUiEvent.BoostRam -> boostRamAction()
            PerformanceUiEvent.CheckPing -> checkPingAction()
            PerformanceUiEvent.ResetDefaults -> resetDefaultsAction()
            PerformanceUiEvent.RefreshVivoPerfList -> refreshVivoPerfGameList()
            is PerformanceUiEvent.AddAllToPerfList -> addAllLauncherGamesToPerfList(event.packages) {}
            is PerformanceUiEvent.RemoveAllFromPerfList -> removeAllLauncherGamesFromPerfList(event.packages) {}
            is PerformanceUiEvent.CompileAllSpeed -> compileAllLauncherGamesSpeed(event.packages) {}
            is PerformanceUiEvent.ToggleAuditLogging -> setAuditLoggingEnabled(event.enabled)
            PerformanceUiEvent.ClearAuditLogs -> clearVivoAuditLogs()
            PerformanceUiEvent.RefreshInstalledApps -> loadUserApps()
            PerformanceUiEvent.RefreshSystemState -> {
                loadUserApps()
                refreshSystemState()
            }
            is PerformanceUiEvent.SetAddGameSheetVisible -> _showAddGameSheet.value = event.visible
            is PerformanceUiEvent.SetConfigGamePkg -> _configGamePkg.value = event.packageName
            is PerformanceUiEvent.SetDeployingGamePkg -> _activeDeployingGamePkg.value = event.packageName
            is PerformanceUiEvent.SetGameConfigBoostRam -> settingsRepository.setGameConfigBoostRam(event.packageName, event.enabled)
            is PerformanceUiEvent.ToggleMemc -> toggleMemc(event.packageName, event.enabled, event.onComplete)
        }
    }

    fun loadUserApps() {
        viewModelScope.launch {
            val installedUser = withContext(Dispatchers.IO) {
                gamingModeEngine.getInstalledUserApps()
            }
            val installedGoogle = withContext(Dispatchers.IO) {
                gamingModeEngine.getGoogleAppsForWhitelist()
            }
            _userApps.value = installedUser
            _googleApps.value = installedGoogle

            val installedPkgs = installedUser.map { it.packageName }.toSet()
            if (installedPkgs.isNotEmpty()) {
                val currentLauncher = settingsRepository.launcherGames.value
                val validLauncher = currentLauncher.filter { it in installedPkgs }.toSet()
                if (validLauncher.size != currentLauncher.size) {
                    settingsRepository.setLauncherGames(validLauncher)
                }
            }
        }
    }

    fun enableGamingMode() {
        if (settingsRepository.launcherGames.value.isEmpty()) {
            _effectChannel.trySend(PerformanceUiEffect.ShowToast("Kindly add a minimum of one game in game launcher"))
            return
        }
        viewModelScope.launch {
            val currentWhitelist = settingsRepository.gamingModeWhitelist.value
            gamingModeEngine.enableGamingMode(currentWhitelist)
            if (gamingModeEngine.state.value == GamingModeState.Active) {
                gamingServiceController.startGamingService()
                if (settingsRepository.getGamingPlatformPath() == GamingPlatformPath.VIVO) {
                    _effectChannel.send(PerformanceUiEffect.ShowToast("Gaming Mode active: Launch your game within 2 min for PID-locked performance optimizations."))
                }
            }
        }
    }

    fun disableGamingMode() {
        viewModelScope.launch {
            gamingModeEngine.disableGamingMode()
            gamingServiceController.stopGamingService()
        }
    }

    fun launchGameWithOptimizations(packageName: String, onLaunched: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val isGamingModeActive = gamingModeEngine.state.value is GamingModeState.Active
            val shouldBoostRam = settingsRepository.getGameConfigBoostRam(packageName)

            var freedMb = 0L
            if (shouldBoostRam) {
                val currentWhitelist = settingsRepository.gamingModeWhitelist.value
                val (freed, _) = manualBoostRam(currentWhitelist + packageName)
                // Critical Fix: manualBoostRam already returns freed in MB, do not divide by 1024*1024 again!
                freedMb = freed.coerceAtLeast(0L)
            }

            val launched = gamingServiceController.launchApp(packageName)
            if (launched) {
                if (isGamingModeActive) {
                    val sessionPath = settingsRepository.getGamingPlatformPath()
                    if (sessionPath == GamingPlatformPath.VIVO) {
                        viewModelScope.launch(Dispatchers.IO) {
                            for (i in 1..10) {
                                delay(500L)
                                val pid = gamingModeEngine.resolveProcessPid(packageName)
                                if (pid > 0) {
                                    gamingModeEngine.promoteGamePid(packageName, pid)
                                    break
                                }
                            }
                        }
                    } else if (sessionPath == GamingPlatformPath.GENERIC) {
                        viewModelScope.launch(Dispatchers.IO) {
                            gamingModeEngine.promoteGamePid(packageName, 0)
                        }
                    }
                }
            }

            onLaunched?.invoke(freedMb)
        }
    }

    private fun boostRamAction() {
        viewModelScope.launch {
            _isBoostingRam.value = true
            val (freed, stopped) = manualBoostRam(settingsRepository.gamingModeWhitelist.value)
            _isBoostingRam.value = false
            _showRamResult.value = true
            _bannerMessage.value = "Boosted! Freed $freed MB, stopped $stopped apps"
            delay(2500)
            _showRamResult.value = false
            delay(300)
            _bannerMessage.value = null
        }
    }

    private fun checkPingAction() {
        viewModelScope.launch {
            _isOptimizingNet.value = true
            val pingRes = measureNetworkLatency()
            _isOptimizingNet.value = false
            _showPingResult.value = true
            _activeLatencyDiagnostic.value = pingRes ?: 0
            _bannerMessage.value = if (pingRes != null) "Latency check complete: $pingRes ms" else "Latency check failed: network unreachable"
            delay(2500)
            _showPingResult.value = false
            delay(300)
            _bannerMessage.value = null
        }
    }

    private fun resetDefaultsAction() {
        viewModelScope.launch {
            _isResettingDefaults.value = true
            val resetOk = resetToDeviceDefaults()
            _isResettingDefaults.value = false
            _showResetResult.value = true
            _bannerMessage.value = if (resetOk) "Device settings reset to OS defaults" else "Device reset partially completed"
            delay(2500)
            _showResetResult.value = false
            delay(300)
            _bannerMessage.value = null
        }
    }

    suspend fun manualBoostRam(whitelist: Set<String>): Pair<Long, Int> =
        PerformanceUtils.manualBoostRam(whitelist, deviceDiagnosticManager, shizukuManager, gamingModeEngine)

    suspend fun measureNetworkLatency(): Int? =
        PerformanceUtils.measureNetworkLatency(shizukuManager)

    fun refreshVivoPerfGameList() {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return
        viewModelScope.launch {
            val raw = vivoGamingOptimizer.getRawPerfGameList()
            _rawPerfGameList.value = raw
            _vivoPerfGameList.value = raw.split(":").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    fun addAllLauncherGamesToPerfList(packages: Set<String>, onComplete: (Boolean) -> Unit) {
        if (packages.isEmpty()) {
            _effectChannel.trySend(PerformanceUiEffect.ShowToast("Kindly add a minimum of one game in game launcher"))
            onComplete(false)
            return
        }
        viewModelScope.launch {
            var allSuccess = true
            for (pkg in packages) {
                val ok = vivoGamingOptimizer.injectPerfGameList(pkg)
                if (!ok) allSuccess = false
            }
            val raw = vivoGamingOptimizer.getRawPerfGameList()
            _rawPerfGameList.value = raw
            _vivoPerfGameList.value = raw.split(":").map { it.trim() }.filter { it.isNotBlank() }
            val msg = if (allSuccess) "Added ${packages.size} game(s) to Perf List ✓" else "Some games could not be added to Perf List"
            _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
            onComplete(allSuccess)
        }
    }

    fun removeAllLauncherGamesFromPerfList(packages: Set<String>, onComplete: (Boolean) -> Unit) {
        if (packages.isEmpty()) {
            _effectChannel.trySend(PerformanceUiEffect.ShowToast("Kindly add a minimum of one game in game launcher"))
            onComplete(false)
            return
        }
        viewModelScope.launch {
            var allSuccess = true
            for (pkg in packages) {
                val ok = vivoGamingOptimizer.removePerfGame(pkg)
                if (!ok) allSuccess = false
            }
            val raw = vivoGamingOptimizer.getRawPerfGameList()
            _rawPerfGameList.value = raw
            _vivoPerfGameList.value = raw.split(":").map { it.trim() }.filter { it.isNotBlank() }
            val msg = if (allSuccess) "Removed ${packages.size} game(s) from Perf List ✓" else "Some games could not be removed from Perf List"
            _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
            onComplete(allSuccess)
        }
    }

    fun compileAllLauncherGamesSpeed(packages: Set<String>, onComplete: (Boolean) -> Unit) {
        if (packages.isEmpty()) {
            _effectChannel.trySend(PerformanceUiEffect.ShowToast("Kindly add a minimum of one game in game launcher"))
            onComplete(false)
            return
        }
        viewModelScope.launch {
            var allSuccess = true
            for (pkg in packages) {
                val ok = vivoGamingOptimizer.compileSpeedAot(pkg)
                if (!ok) allSuccess = false
            }
            val msg = if (allSuccess) "AOT compiled ${packages.size} app(s) to speed filter ✓" else "AOT compilation failed for one or more apps"
            _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
            onComplete(allSuccess)
        }
    }

    fun setAuditLoggingEnabled(enabled: Boolean) {
        settingsRepository.setAuditLoggingEnabled(enabled)
        if (!enabled) {
            vivoGamingOptimizer.clearAuditLogs()
        }
    }

    fun clearVivoAuditLogs() {
        vivoGamingOptimizer.clearAuditLogs()
    }

    suspend fun resetToDeviceDefaults(): Boolean =
        esportsOptimizationEngine.resetToDeviceDefaults(forceReset = true)

    fun getGameConfigBoostRam(pkg: String): Boolean = settingsRepository.getGameConfigBoostRam(pkg)
    fun setGameConfigBoostRam(pkg: String, enabled: Boolean) = settingsRepository.setGameConfigBoostRam(pkg, enabled)
    fun getGameConfigMemc(pkg: String): Boolean = settingsRepository.getGameConfigMemc(pkg)

    fun toggleMemc(packageName: String, enabled: Boolean, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = vivoGamingOptimizer.setMemcTargetFps(packageName, enabled)
            if (result) {
                settingsRepository.setGameConfigMemc(packageName, enabled)
            }
            onComplete(result)
        }
    }

    val safeToSuspendList: List<String> get() = gamingModeEngine.safeToSuspendPackages
    val gamingDaemonsList: List<String>
        get() = if (vivoSuiteGate.isVivoSuiteEnabled) GamingModeEngine.GAMING_DAEMONS else emptyList()
}
