package com.framex.app.ui.screens.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.R
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.AppInfo
import com.framex.app.gaming.GamingDeviceReset
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.GamingServiceController
import com.framex.app.gaming.ledger.ExecutionLedger
import com.framex.app.metrics.MetricsEngine
import com.framex.app.repository.SettingsRepository
import com.framex.app.privilege.PrivilegeManager
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
    private val privilegeManager: PrivilegeManager,
    private val settingsRepository: SettingsRepository,
    private val metricsEngine: MetricsEngine,
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val executionLedger: ExecutionLedger,
    private val gamingServiceController: GamingServiceController,
    private val gamingDeviceReset: GamingDeviceReset
) : ViewModel() {

    private val _effectChannel = Channel<PerformanceUiEffect>(Channel.BUFFERED)
    val effect = _effectChannel.receiveAsFlow()

    val maxRefreshRate: Int = deviceDiagnosticManager.getMaxHardwareRefreshRate().toInt().coerceAtLeast(60)

    // Dynamic state streams
    private val _userApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val userApps = _userApps.asStateFlow()

    private val _googleApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val googleApps = _googleApps.asStateFlow()

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
                activeGamePackage = activePkg,
                suspendedAppsCount = suspendedCount,
                summary = executionLedger.getSummary()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Grouped streams to stay strictly within combine limits
    private val shizukuAndGamingStream = combine(
        gamingModeEngine.state,
        privilegeManager.isAvailable,
        privilegeManager.isReady,
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
    ) { sg, sys, metrics, inter ->
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
        val activeSession = if (gamingModeEngine.state.value is GamingModeState.Active) {
            ActiveGamingSession(
                title = "Gaming Mode Active",
                activeGamePackage = gamingModeEngine.activeGamePackage.value,
                suspendedAppsCount = gamingModeEngine.suspendedPackagesCount.value,
                summary = executionLedger.getSummary()
            )
        } else null

        return PerformanceUiState(
            gamingState = gamingModeEngine.state.value,
            isShizukuAvailable = privilegeManager.isAvailable.value,
            hasShizukuPermission = privilegeManager.isReady.value,
            whitelist = settingsRepository.gamingModeWhitelist.value,
            launcherGames = settingsRepository.launcherGames.value,
            userApps = emptyList(),
            googleApps = emptyList(),
            metricsState = metricsEngine.metricsState.value,
            fixedPerformanceMode = settingsRepository.fixedPerformanceMode.value,
            deepFreezeEnabled = settingsRepository.deepFreezeEnabled.value,
            hasSeenDeepFreezeNotice = settingsRepository.hasSeenDeepFreezeNotice.value,
            activeGamingSession = activeSession,
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
            _effectChannel.trySend(PerformanceUiEffect.ShowToast(R.string.perf_toast_add_one_game))
            return
        }
        viewModelScope.launch {
            val currentWhitelist = settingsRepository.gamingModeWhitelist.value
            gamingModeEngine.enableGamingMode(currentWhitelist)
            if (gamingModeEngine.state.value == GamingModeState.Active) {
                gamingServiceController.startGamingService()
            }
        }
    }

    fun disableGamingMode() {
        viewModelScope.launch {
            gamingModeEngine.disableGamingMode()
            gamingServiceController.stopGamingService()
        }
    }

    /** Launches the game, then attaches the per-game catalog entries once it is known to be live. */
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
            if (launched && isGamingModeActive) {
                viewModelScope.launch(Dispatchers.IO) {
                    gamingModeEngine.promoteGamePid(packageName)
                }
            }

            onLaunched?.invoke(freedMb)
        }
    }

    suspend fun manualBoostRam(whitelist: Set<String>): Pair<Long, Int> =
        PerformanceUtils.manualBoostRam(whitelist, deviceDiagnosticManager, privilegeManager, gamingModeEngine)

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
            val outcome = resetToDeviceDefaults()
            _isResettingDefaults.value = false
            _showResetResult.value = true
            _bannerMessage.value = when {
                outcome.usedLegacyFallback ->
                    "Original values could not be restored precisely; a generic reset was applied instead"
                outcome.success -> "Device settings reset to their values before the session"
                else -> "Device reset partially completed"
            }
            delay(2500)
            _showResetResult.value = false
            delay(300)
            _bannerMessage.value = null
        }
    }

    suspend fun measureNetworkLatency(): Int? =
        PerformanceUtils.measureNetworkLatency(privilegeManager)

    suspend fun resetToDeviceDefaults() =
        gamingDeviceReset.resetToDeviceDefaults(forceReset = true)
}
