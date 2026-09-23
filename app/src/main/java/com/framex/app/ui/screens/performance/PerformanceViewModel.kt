package com.framex.app.ui.screens.performance

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.gaming.AppInfo
import com.framex.app.gaming.EsportsOptimizationEngine
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.gaming.GamingModeService
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.GamingPlatformPath
import com.framex.app.gaming.SystemAuditLog
import com.framex.app.gaming.VivoGamingOptimizer
import com.framex.app.gaming.VivoSuiteGate
import com.framex.app.gaming.ledger.ExecutionLedger
import com.framex.app.metrics.MetricsEngine
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

private data class SystemSettingsGroup(
    val fixedPerformanceMode: Boolean,
    val deepFreezeEnabled: Boolean,
    val hasSeenDeepFreezeNotice: Boolean,
    val auditLoggingEnabled: Boolean
)

private data class ShizukuAndGamingGroup(
    val gamingState: GamingModeState,
    val isShizukuAvailable: Boolean,
    val hasShizukuPermission: Boolean,
    val whitelist: Set<String>,
    val launcherGames: Set<String>
)

private data class VivoGroup(
    val isVivoSuiteEnabled: Boolean,
    val rawPerfGameList: String?,
    val vivoPerfGameList: List<String>,
    val vivoAuditLogs: List<SystemAuditLog>
)

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gamingModeEngine: GamingModeEngine,
    private val esportsOptimizationEngine: EsportsOptimizationEngine,
    private val vivoGamingOptimizer: VivoGamingOptimizer,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val metricsEngine: MetricsEngine,
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val executionLedger: ExecutionLedger,
    private val vivoSuiteGate: VivoSuiteGate
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
            actionStateStream,
            dialogStateStream
        ) { user, google, session, actions, dialogs ->
            IntermediateUiState(user, google, session, actions, dialogs)
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerformanceUiState())

    init {
        loadUserApps()
        metricsEngine.setScreenOverrideModules(setOf("cpu", "ram", "ping"), requesterKey = "performance_screen")
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "performance_screen")
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
                context.startForegroundService(Intent(context, GamingModeService::class.java))
                if (settingsRepository.getGamingPlatformPath() == GamingPlatformPath.VIVO) {
                    _effectChannel.send(PerformanceUiEffect.ShowToast("Gaming Mode active: Launch your game within 2 min for PID-locked performance optimizations."))
                }
            }
        }
    }

    fun disableGamingMode() {
        viewModelScope.launch {
            gamingModeEngine.disableGamingMode()
            context.startService(
                Intent(context, GamingModeService::class.java).apply {
                    action = GamingModeService.ACTION_STOP
                }
            )
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

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)

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

    suspend fun manualBoostRam(whitelist: Set<String>): Pair<Long, Int> {
        val availBefore = deviceDiagnosticManager.getAvailableMemoryBytes()

        var stoppedCount = 0
        if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
            try {
                shizukuManager.executeCommand("pm trim-caches 4G")
                val targets = withContext(Dispatchers.IO) {
                    gamingModeEngine.getInstalledUserApps()
                        .filter { it.packageName !in whitelist }
                }
                for (app in targets) {
                    try {
                        shizukuManager.executeCommand("am force-stop ${app.packageName}")
                        stoppedCount++
                    } catch (e: Exception) {
                        FrameXLog.w("Failed to force-stop ${app.packageName}", e)
                    }
                }
                shizukuManager.executeCommand("am kill-all")
            } catch (e: Exception) {
                FrameXLog.w("Error during manual RAM boost via Shizuku", e)
            }
        }
        System.gc()

        val availAfter = deviceDiagnosticManager.getAvailableMemoryBytes()
        val freed = ((availAfter - availBefore) / BYTES_TO_MB).coerceAtLeast(0L)
        return Pair(freed, stoppedCount)
    }

    suspend fun measureNetworkLatency(): Int? {
        if (shizukuManager.isShizukuAvailable.value && shizukuManager.hasPermission.value) {
            try {
                val output = shizukuManager.executeCommand("ping -c 1 8.8.8.8")
                if (output.contains("time=")) {
                    val pingMs = output.split("time=").getOrNull(1)
                        ?.split(" ")?.getOrNull(0)
                        ?.toFloatOrNull()
                        ?.toInt()
                    if (pingMs != null && pingMs > 0) return pingMs
                }
            } catch (e: Exception) {
                FrameXLog.w("Shizuku ping check failed, falling back to socket probe", e)
            }
        }
        var minPing: Int? = null
        for (i in 1..3) {
            try {
                val start = System.currentTimeMillis()
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), SOCKET_TIMEOUT_MS)
                val latency = (System.currentTimeMillis() - start).toInt()
                socket.close()
                minPing = minOf(minPing ?: latency, latency)
            } catch (e: Exception) {
                FrameXLog.w("Socket ping probe iteration $i failed", e)
            }
            delay(RETRY_DELAY_MS)
        }
        return minPing
    }

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

    companion object {
        private const val BYTES_TO_MB = 1024L * 1024L
        private const val SOCKET_TIMEOUT_MS = 1000
        private const val RETRY_DELAY_MS = 150L
    }
}

private data class ActionStateGroup(
    val isBoostingRam: Boolean,
    val isOptimizingNet: Boolean,
    val isResettingDefaults: Boolean,
    val bannerMessage: String?,
    val activeLatencyDiagnostic: Int?
)

private data class DialogStateGroup(
    val showAddGameSheet: Boolean,
    val configGamePkg: String?,
    val activeDeployingGamePkg: String?,
    val showRamResult: Boolean,
    val showPingResult: Boolean,
    val showResetResult: Boolean
)

private data class IntermediateUiState(
    val userApps: List<AppInfo>,
    val googleApps: List<AppInfo>,
    val activeSession: ActiveGamingSession?,
    val actions: ActionStateGroup,
    val dialogs: DialogStateGroup
)
