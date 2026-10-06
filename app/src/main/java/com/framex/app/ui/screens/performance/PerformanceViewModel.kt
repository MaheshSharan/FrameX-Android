package com.framex.app.ui.screens.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.AppInfo
import com.framex.app.gaming.EsportsOptimizationEngine
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.GamingPlatformPath
import com.framex.app.gaming.GamingServiceController
import com.framex.app.gaming.VivoGamingOptimizer
import com.framex.app.gaming.VivoIqooSharedTools
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
    private val vivoGamingOptimizer: VivoGamingOptimizer,
    private val vivoIqooSharedTools: VivoIqooSharedTools,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val metricsEngine: MetricsEngine,
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val executionLedger: ExecutionLedger,
    private val vivoSuiteGate: VivoSuiteGate,
    private val gamingServiceController: GamingServiceController,
    private val esportsOptimizationEngine: EsportsOptimizationEngine
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

    private val _raw144MergeList = MutableStateFlow<String?>(null)
    val raw144MergeList = _raw144MergeList.asStateFlow()

    private val _mergeList144 = MutableStateFlow<List<String>>(emptyList())
    val mergeList144 = _mergeList144.asStateFlow()

    private val _isRefreshingPerfList = MutableStateFlow(false)
    val isRefreshingPerfList = _isRefreshingPerfList.asStateFlow()

    private val _isRefreshing144List = MutableStateFlow(false)
    val isRefreshing144List = _isRefreshing144List.asStateFlow()

    // Banner message state
    private val _bannerMessage = MutableStateFlow<String?>(null)

    // Optimization Sliders state
    private val _isBoostingRam = MutableStateFlow(false)
    private val _showRamResult = MutableStateFlow(false)
    private val _isOptimizingNet = MutableStateFlow(false)
    private val _showPingResult = MutableStateFlow(false)
    private val _isResettingDefaults = MutableStateFlow(false)
    private val _showResetResult = MutableStateFlow(false)
    private val _activeLatencyDiagnostic = MutableStateFlow<Int?>(null)

    // Dialog & Modal visibility states
    private val _showAddGameSheet = MutableStateFlow(false)
    private val _configGamePkg = MutableStateFlow<String?>(null)
    private val _activeDeployingGamePkg = MutableStateFlow<String?>(null)

    // System Access state
    private val _hasDndAccess = MutableStateFlow(deviceDiagnosticManager.hasDndAccess())
    private val _hasNotifListenerAccess = MutableStateFlow(deviceDiagnosticManager.hasNotificationListenerAccess())
    private val _hasWriteSettingsAccess = MutableStateFlow(deviceDiagnosticManager.hasWriteSettingsAccess())

    private val systemAccessStream = combine(
        _hasDndAccess,
        _hasNotifListenerAccess,
        _hasWriteSettingsAccess
    ) { dnd, notif, writeSettings ->
        SystemAccessGroup(dnd, notif, writeSettings)
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
        settingsRepository.hasSeenDeepFreezeNotice
    ) { fixedPerf, deepFreeze, hasSeenNotice ->
        SystemSettingsGroup(fixedPerf, deepFreeze, hasSeenNotice)
    }

    private val vivoStream = combine(
        combine(
            vivoSuiteGate.isVivoSuiteEnabledFlow,
            _rawPerfGameList,
            _vivoPerfGameList,
            _raw144MergeList,
            _mergeList144
        ) { enabled, rawPerf, perfList, raw144, list144 ->
            Tuple5(enabled, rawPerf, perfList, raw144, list144)
        },
        combine(
            _isRefreshingPerfList,
            _isRefreshing144List
        ) { refPerf, ref144 ->
            Pair(refPerf, ref144)
        }
    ) { (enabled, rawPerf, perfList, raw144, list144), (refPerf, ref144) ->
        VivoGroup(enabled, rawPerf, perfList, raw144, list144, refPerf, ref144)
    }

    private val actionStateStream = combine(
        combine(_isBoostingRam, _showRamResult, _isOptimizingNet) { bRam, sRam, oNet -> Triple(bRam, sRam, oNet) },
        combine(_showPingResult, _isResettingDefaults, _showResetResult, _activeLatencyDiagnostic) { sPing, rDef, sReset, lat ->
            Tuple4(sPing, rDef, sReset, lat)
        }
    ) { (bRam, sRam, oNet), (sPing, rDef, sReset, lat) ->
        ActionStateGroup(bRam, sRam, oNet, sPing, rDef, sReset, lat)
    }

    private val dialogStateStream = combine(
        _showAddGameSheet,
        _configGamePkg,
        _activeDeployingGamePkg
    ) { addGame, configPkg, deployingPkg ->
        DialogStateGroup(addGame, configPkg, deployingPkg)
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
            _bannerMessage,
            combine(dialogStateStream, systemAccessStream, actionStateStream) { dialogs, access, actions ->
                Triple(dialogs, access, actions)
            }
        ) { user, google, session, banner, (dialogs, access, actions) ->
            IntermediateUiState(user, google, session, banner, actions, dialogs, access)
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
            raw144MergeList = vivo.raw144MergeList,
            mergeList144 = vivo.mergeList144,
            isRefreshingPerfList = vivo.isRefreshingPerfList,
            isRefreshing144List = vivo.isRefreshing144List,
            maxRefreshRate = maxRefreshRate,
            hasDndAccess = inter.systemAccess.hasDndAccess,
            hasNotifListenerAccess = inter.systemAccess.hasNotifListenerAccess,
            hasWriteSettingsAccess = inter.systemAccess.hasWriteSettingsAccess,
            bannerMessage = inter.bannerMessage,
            isBoostingRam = inter.actions.isBoostingRam,
            showRamResult = inter.actions.showRamResult,
            isOptimizingNet = inter.actions.isOptimizingNet,
            showPingResult = inter.actions.showPingResult,
            isResettingDefaults = inter.actions.isResettingDefaults,
            showResetResult = inter.actions.showResetResult,
            activeLatencyDiagnostic = inter.actions.activeLatencyDiagnostic,
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
            maxRefreshRate = maxRefreshRate,
            hasDndAccess = _hasDndAccess.value,
            hasNotifListenerAccess = _hasNotifListenerAccess.value,
            hasWriteSettingsAccess = _hasWriteSettingsAccess.value,
            bannerMessage = null,
            showAddGameSheet = false,
            configGamePkg = null,
            activeDeployingGamePkg = null
        )
    }

    init {
        loadUserApps()
        refreshSystemState()
        metricsEngine.setScreenOverrideModules(setOf("cpu", "ram", "fps"), requesterKey = "performance_screen")
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "performance_screen")
    }

    fun refreshSystemState() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasDndAccess.value = deviceDiagnosticManager.hasDndAccess()
            _hasNotifListenerAccess.value = deviceDiagnosticManager.hasNotificationListenerAccess()
            _hasWriteSettingsAccess.value = deviceDiagnosticManager.hasWriteSettingsAccess()
        }
    }

    fun onEvent(event: PerformanceUiEvent) {
        when (event) {
            is PerformanceUiEvent.ToggleWhitelist -> settingsRepository.toggleGamingWhitelistApp(event.packageName)
            is PerformanceUiEvent.ToggleLauncherGame -> settingsRepository.toggleLauncherGame(event.packageName)
            is PerformanceUiEvent.ToggleDeepFreeze -> settingsRepository.setDeepFreezeEnabled(event.enabled)
            PerformanceUiEvent.DismissDeepFreezeNotice -> settingsRepository.setHasSeenDeepFreezeNotice(true)
            PerformanceUiEvent.EnableGamingMode -> enableGamingMode()
            PerformanceUiEvent.DisableGamingMode -> disableGamingMode()
            is PerformanceUiEvent.LaunchGame -> launchGameWithOptimizations(event.packageName)
            PerformanceUiEvent.BoostRam -> boostRamAction()
            PerformanceUiEvent.CheckPing -> checkPingAction()
            PerformanceUiEvent.ResetDefaults -> resetDefaultsAction()
            PerformanceUiEvent.RefreshVivoPerfList -> refreshVivoPerfGameList()
            is PerformanceUiEvent.AddAllToPerfList -> addAllLauncherGamesToPerfList(event.packages, event.onComplete)
            is PerformanceUiEvent.RemoveAllFromPerfList -> removeAllLauncherGamesFromPerfList(event.packages, event.onComplete)
            is PerformanceUiEvent.CompileAllSpeed -> compileAllLauncherGamesSpeed(event.packages, event.onComplete)
            PerformanceUiEvent.Refresh144MergeList -> refresh144MergeList()
            is PerformanceUiEvent.AddAllTo144MergeList -> addAllLauncherGamesTo144List(event.packages, event.onComplete)
            is PerformanceUiEvent.RemoveAllFrom144MergeList -> removeAllLauncherGamesFrom144List(event.packages, event.onComplete)
            is PerformanceUiEvent.AddTo144MergeList -> addSingleGameTo144List(event.packageName)
            is PerformanceUiEvent.RemoveFrom144MergeList -> removeSingleGameFrom144List(event.packageName)
            PerformanceUiEvent.RefreshInstalledApps -> loadUserApps()
            PerformanceUiEvent.RefreshSystemState -> {
                loadUserApps()
                refreshSystemState()
            }
            is PerformanceUiEvent.SetAddGameSheetVisible -> _showAddGameSheet.value = event.visible
            is PerformanceUiEvent.SetConfigGamePkg -> _configGamePkg.value = event.packageName
            is PerformanceUiEvent.SetDeployingGamePkg -> _activeDeployingGamePkg.value = event.packageName
            is PerformanceUiEvent.SetGameConfigBoostRam -> settingsRepository.setGameConfigBoostRam(event.packageName, event.enabled)
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

    suspend fun manualBoostRam(whitelist: Set<String>): Pair<Long, Int> =
        PerformanceUtils.manualBoostRam(whitelist, deviceDiagnosticManager, shizukuManager, gamingModeEngine)

    fun refreshVivoPerfGameList() {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return
        viewModelScope.launch {
            _isRefreshingPerfList.value = true
            try {
                val raw = vivoIqooSharedTools.getRawPerfGameList()
                _rawPerfGameList.value = raw
                _vivoPerfGameList.value = raw.split(":").map { it.trim() }.filter { it.isNotBlank() }
            } finally {
                _isRefreshingPerfList.value = false
            }
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
            try {
                for (pkg in packages) {
                    val ok = vivoIqooSharedTools.injectPerfGameList(pkg)
                    if (!ok) allSuccess = false
                }
                val raw = vivoIqooSharedTools.getRawPerfGameList()
                _rawPerfGameList.value = raw
                _vivoPerfGameList.value = raw.split(":").map { it.trim() }.filter { it.isNotBlank() }
                val msg = if (allSuccess) "Added ${packages.size} game(s) to Perf List ✓" else "Some games could not be added to Perf List"
                _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
            } catch (t: Throwable) {
                FrameXLog.e("addAllLauncherGamesToPerfList failed", t)
                allSuccess = false
            } finally {
                onComplete(allSuccess)
            }
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
            try {
                for (pkg in packages) {
                    val ok = vivoIqooSharedTools.removePerfGame(pkg)
                    if (!ok) allSuccess = false
                }
                val raw = vivoIqooSharedTools.getRawPerfGameList()
                _rawPerfGameList.value = raw
                _vivoPerfGameList.value = raw.split(":").map { it.trim() }.filter { it.isNotBlank() }
                val msg = if (allSuccess) "Removed ${packages.size} game(s) from Perf List ✓" else "Some games could not be removed from Perf List"
                _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
            } catch (t: Throwable) {
                FrameXLog.e("removeAllLauncherGamesFromPerfList failed", t)
                allSuccess = false
            } finally {
                onComplete(allSuccess)
            }
        }
    }

    fun refresh144MergeList() {
        if (!vivoSuiteGate.isVivoSuiteEnabled) return
        viewModelScope.launch {
            _isRefreshing144List.value = true
            try {
                val raw = vivoIqooSharedTools.getRaw144MergeList()
                _raw144MergeList.value = raw
                _mergeList144.value = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } finally {
                _isRefreshing144List.value = false
            }
        }
    }

    fun addAllLauncherGamesTo144List(packages: Set<String>, onComplete: (Boolean) -> Unit) {
        if (packages.isEmpty()) {
            _effectChannel.trySend(PerformanceUiEffect.ShowToast("Kindly add a minimum of one game in game launcher"))
            onComplete(false)
            return
        }
        viewModelScope.launch {
            var allSuccess = true
            try {
                for (pkg in packages) {
                    val ok = vivoIqooSharedTools.addTo144MergeList(pkg)
                    if (!ok) allSuccess = false
                }
                val raw = vivoIqooSharedTools.getRaw144MergeList()
                _raw144MergeList.value = raw
                _mergeList144.value = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val msg = if (allSuccess) "Added ${packages.size} game(s) to 144Hz Merge List ✓" else "Some games could not be added to 144Hz Merge List"
                _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
            } catch (t: Throwable) {
                FrameXLog.e("addAllLauncherGamesTo144List failed", t)
                allSuccess = false
            } finally {
                onComplete(allSuccess)
            }
        }
    }

    fun removeAllLauncherGamesFrom144List(packages: Set<String>, onComplete: (Boolean) -> Unit) {
        if (packages.isEmpty()) {
            _effectChannel.trySend(PerformanceUiEffect.ShowToast("Kindly add a minimum of one game in game launcher"))
            onComplete(false)
            return
        }
        viewModelScope.launch {
            var allSuccess = true
            try {
                for (pkg in packages) {
                    val ok = vivoIqooSharedTools.removeFrom144MergeList(pkg)
                    if (!ok) allSuccess = false
                }
                val raw = vivoIqooSharedTools.getRaw144MergeList()
                _raw144MergeList.value = raw
                _mergeList144.value = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val msg = if (allSuccess) "Removed ${packages.size} game(s) from 144Hz Merge List ✓" else "Some games could not be removed from 144Hz Merge List"
                _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
            } catch (t: Throwable) {
                FrameXLog.e("removeAllLauncherGamesFrom144List failed", t)
                allSuccess = false
            } finally {
                onComplete(allSuccess)
            }
        }
    }

    fun addSingleGameTo144List(packageName: String) {
        viewModelScope.launch {
            val ok = vivoIqooSharedTools.addTo144MergeList(packageName)
            val raw = vivoIqooSharedTools.getRaw144MergeList()
            _raw144MergeList.value = raw
            _mergeList144.value = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val msg = if (ok) "Added to 144Hz Merge List ✓" else "Failed to add to 144Hz Merge List"
            _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
        }
    }

    fun removeSingleGameFrom144List(packageName: String) {
        viewModelScope.launch {
            val ok = vivoIqooSharedTools.removeFrom144MergeList(packageName)
            val raw = vivoIqooSharedTools.getRaw144MergeList()
            _raw144MergeList.value = raw
            _mergeList144.value = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val msg = if (ok) "Removed from 144Hz Merge List ✓" else "Failed to remove from 144Hz Merge List"
            _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
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
            try {
                for (pkg in packages) {
                    val ok = vivoIqooSharedTools.compileSpeedAot(pkg)
                    if (!ok) allSuccess = false
                }
                val msg = if (allSuccess) "AOT compiled ${packages.size} app(s) to speed filter ✓" else "AOT compilation failed for one or more apps"
                _effectChannel.send(PerformanceUiEffect.ShowToast(msg))
            } catch (t: Throwable) {
                FrameXLog.e("compileAllLauncherGamesSpeed failed", t)
                allSuccess = false
            } finally {
                onComplete(allSuccess)
            }
        }
    }

    fun getGameConfigBoostRam(pkg: String): Boolean = settingsRepository.getGameConfigBoostRam(pkg)
    fun setGameConfigBoostRam(pkg: String, enabled: Boolean) = settingsRepository.setGameConfigBoostRam(pkg, enabled)

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

    suspend fun measureNetworkLatency(): Int? =
        PerformanceUtils.measureNetworkLatency(shizukuManager)

    suspend fun resetToDeviceDefaults(): Boolean =
        esportsOptimizationEngine.resetToDeviceDefaults(forceReset = true)
}
