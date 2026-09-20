package com.framex.app.ui.screens.performance

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.gaming.AppInfo
import com.framex.app.gaming.EsportsOptimizationEngine
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.gaming.GamingModeService
import com.framex.app.gaming.GamingModeState
import com.framex.app.gaming.SystemAuditLog
import com.framex.app.gaming.VivoGamingOptimizer
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import com.framex.app.gaming.ledger.ExecutionLedger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val metricsEngine: com.framex.app.metrics.MetricsEngine,
    private val deviceDiagnosticManager: com.framex.app.device.DeviceDiagnosticManager,
    private val executionLedger: ExecutionLedger
) : ViewModel() {

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    val gamingModeState = gamingModeEngine.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GamingModeState.Idle)

    val isShizukuAvailable = shizukuManager.isShizukuAvailable
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasShizukuPermission = shizukuManager.hasPermission
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val whitelist = settingsRepository.gamingModeWhitelist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val launcherGames = settingsRepository.launcherGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val metricsState = metricsEngine.metricsState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.framex.app.metrics.MetricsState())

    val cpuPriorityLock = settingsRepository.cpuPriorityLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val networkFirewall = settingsRepository.networkFirewall
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val refreshRateLock = settingsRepository.refreshRateLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val touchBoost = settingsRepository.touchBoost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val framePacingOverlay = settingsRepository.framePacingOverlay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val fixedPerformanceMode = settingsRepository.fixedPerformanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeGamingSession: StateFlow<ActiveGamingSession?> = combine(
        gamingModeEngine.state,
        gamingModeEngine.activeGamePackage,
        gamingModeEngine.suspendedPackagesCount,
        executionLedger.ops
    ) { state, activePkg, suspendedCount, _ ->
        if (state !is GamingModeState.Active) {
            null
        } else {
            val isVivo = deviceDiagnosticManager.isVivoOrIqoo()
            ActiveGamingSession(
                title = "Gaming Mode Active",
                isVivoDevice = isVivo,
                activeGamePackage = activePkg,
                suspendedAppsCount = suspendedCount,
                summary = executionLedger.getSummary()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleCpuPriorityLock(enabled: Boolean) = settingsRepository.setCpuPriorityLock(enabled)
    fun toggleNetworkFirewall(enabled: Boolean) = settingsRepository.setNetworkFirewall(enabled)
    fun toggleRefreshRateLock(enabled: Boolean) = settingsRepository.setRefreshRateLock(enabled)
    fun toggleTouchBoost(enabled: Boolean) = settingsRepository.setTouchBoost(enabled)
    fun toggleFramePacingOverlay(enabled: Boolean) = settingsRepository.setFramePacingOverlay(enabled)
    fun toggleFixedPerformanceMode(enabled: Boolean) = settingsRepository.setFixedPerformanceMode(enabled)

    private val _userApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val userApps = _userApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _googleApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val googleApps = _googleApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadUserApps()
        metricsEngine.setScreenOverrideModules(setOf("cpu", "ram", "ping"), requesterKey = "performance_screen")
    }

    override fun onCleared() {
        super.onCleared()
        metricsEngine.setScreenOverrideModules(emptySet(), requesterKey = "performance_screen")
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

            // Auto-prune uninstalled packages from launcherGames
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

    fun toggleWhitelist(packageName: String) {
        settingsRepository.toggleGamingWhitelistApp(packageName)
    }

    fun toggleLauncherGame(packageName: String) {
        settingsRepository.toggleLauncherGame(packageName)
    }

    fun getGameConfigBoostRam(pkg: String): Boolean = settingsRepository.getGameConfigBoostRam(pkg)
    fun setGameConfigBoostRam(pkg: String, enabled: Boolean) = settingsRepository.setGameConfigBoostRam(pkg, enabled)

    fun getGameConfigDisableBrightness(pkg: String): Boolean = settingsRepository.getGameConfigDisableBrightness(pkg)
    fun setGameConfigDisableBrightness(pkg: String, enabled: Boolean) = settingsRepository.setGameConfigDisableBrightness(pkg, enabled)

    fun getGameConfigDisableRotate(pkg: String): Boolean = settingsRepository.getGameConfigDisableRotate(pkg)
    fun setGameConfigDisableRotate(pkg: String, enabled: Boolean) = settingsRepository.setGameConfigDisableRotate(pkg, enabled)

    fun getGameConfigRingtoneVol(pkg: String): Int = settingsRepository.getGameConfigRingtoneVol(pkg)
    fun setGameConfigRingtoneVol(pkg: String, vol: Int) = settingsRepository.setGameConfigRingtoneVol(pkg, vol)

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
                        com.framex.app.utils.FrameXLog.w("Failed to force-stop ${app.packageName}", e)
                    }
                }
                shizukuManager.executeCommand("am kill-all")
            } catch (e: Exception) {
                com.framex.app.utils.FrameXLog.w("Error during manual RAM boost via Shizuku", e)
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
                com.framex.app.utils.FrameXLog.w("Shizuku ping check failed, falling back to socket probe", e)
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
                com.framex.app.utils.FrameXLog.w("Socket ping probe iteration $i failed", e)
            }
            delay(RETRY_DELAY_MS)
        }
        return minPing
    }

    fun enableGamingMode(context: Context) {
        viewModelScope.launch {
            val currentWhitelist = settingsRepository.gamingModeWhitelist.value
            gamingModeEngine.enableGamingMode(currentWhitelist)
            if (gamingModeEngine.state.value == GamingModeState.Active) {
                context.startForegroundService(Intent(context, GamingModeService::class.java))
                if (deviceDiagnosticManager.isVivoOrIqoo()) {
                    _toastEvent.emit("Gaming Mode active: Launch your game within 2 min for PID-locked performance optimizations.")
                }
            }
        }
    }

    fun launchGameWithOptimizations(context: Context, packageName: String, onLaunched: (Long) -> Unit) {
        viewModelScope.launch {
            val isGamingModeActive = gamingModeEngine.state.value is GamingModeState.Active
            val shouldBoostRam = settingsRepository.getGameConfigBoostRam(packageName)

            var freedMb = 0L
            if (shouldBoostRam) {
                val currentWhitelist = settingsRepository.gamingModeWhitelist.value
                val (freed, _) = manualBoostRam(currentWhitelist + packageName)
                freedMb = (freed / (1024L * 1024L)).coerceAtLeast(0L)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)

                // Promote PID and attach per-game optimizations if Gaming Mode is actively running
                if (isGamingModeActive) {
                    if (deviceDiagnosticManager.isVivoOrIqoo()) {
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
                    } else {
                        viewModelScope.launch(Dispatchers.IO) {
                            gamingModeEngine.promoteGamePid(packageName, 0)
                        }
                    }
                }
            }

            onLaunched(freedMb)
        }
    }

    fun disableGamingMode(context: Context) {
        viewModelScope.launch {
            gamingModeEngine.disableGamingMode()
            context.startService(
                Intent(context, GamingModeService::class.java).apply {
                    action = GamingModeService.ACTION_STOP
                }
            )
        }
    }

    fun compileGameSpeed(packageName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = vivoGamingOptimizer.compileSpeedAot(packageName)
            onComplete(result)
        }
    }

    fun checkGameSpeedCompiled(packageName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = vivoGamingOptimizer.isSpeedCompiled(packageName)
            onResult(result)
        }
    }

    fun toggleMemc(packageName: String, enabled: Boolean, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = vivoGamingOptimizer.setMemcTargetFps(packageName, enabled)
            onComplete(result)
        }
    }

    // null = not yet loaded, blank string = device returned empty list
    private val _rawPerfGameList = MutableStateFlow<String?>(null)
    val rawPerfGameList: StateFlow<String?> = _rawPerfGameList.asStateFlow()

    private val _vivoPerfGameList = MutableStateFlow<List<String>>(emptyList())
    val vivoPerfGameList: StateFlow<List<String>> = _vivoPerfGameList.asStateFlow()

    fun refreshVivoPerfGameList() {
        if (!deviceDiagnosticManager.isVivoOrIqoo()) return
        viewModelScope.launch {
            val raw = vivoGamingOptimizer.getRawPerfGameList()
            _rawPerfGameList.value = raw
            _vivoPerfGameList.value = raw.split(":").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    fun getPackageCompileFilter(packageName: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val filter = vivoGamingOptimizer.getPackageCompileFilter(packageName)
            onResult(filter)
        }
    }

    /** Adds all launcher games to perf_game_list one by one, refreshes list on completion. */
    fun addAllLauncherGamesToPerfList(packages: Set<String>, onComplete: (Boolean) -> Unit) {
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
            _toastEvent.emit(msg)
            onComplete(allSuccess)
        }
    }

    /** Removes all launcher games from perf_game_list one by one, refreshes list on completion. */
    fun removeAllLauncherGamesFromPerfList(packages: Set<String>, onComplete: (Boolean) -> Unit) {
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
            _toastEvent.emit(msg)
            onComplete(allSuccess)
        }
    }

    /** Compiles all launcher games with AOT speed mode. Reports overall success. */
    fun compileAllLauncherGamesSpeed(packages: Set<String>, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            var allSuccess = true
            for (pkg in packages) {
                val ok = vivoGamingOptimizer.compileSpeedAot(pkg)
                if (!ok) allSuccess = false
            }
            val msg = if (allSuccess) "AOT compiled ${packages.size} app(s) to speed filter ✓" else "AOT compilation failed for one or more apps"
            _toastEvent.emit(msg)
            onComplete(allSuccess)
        }
    }

    val isVivoDevice: Boolean get() = deviceDiagnosticManager.isVivoOrIqoo()

    val auditLoggingEnabled: StateFlow<Boolean> = settingsRepository.auditLoggingEnabled

    fun setAuditLoggingEnabled(enabled: Boolean) {
        settingsRepository.setAuditLoggingEnabled(enabled)
        if (!enabled) {
            vivoGamingOptimizer.clearAuditLogs()
        }
    }

    val vivoAuditLogs: StateFlow<List<SystemAuditLog>> = vivoGamingOptimizer.auditLogs

    fun clearVivoAuditLogs() {
        vivoGamingOptimizer.clearAuditLogs()
    }

    suspend fun resetToDeviceDefaults(): Boolean =
        esportsOptimizationEngine.resetToDeviceDefaults(forceReset = true)

    val safeToSuspendList: List<String> get() = gamingModeEngine.safeToSuspendPackages
    val googleSafeToSuspendList: List<String> get() = GamingModeEngine.GOOGLE_SAFE_TO_SUSPEND
    val gamingDaemonsList: List<String>
        get() = if (deviceDiagnosticManager.isVivoOrIqoo() && settingsRepository.vivoOptEnabled.value) GamingModeEngine.GAMING_DAEMONS else emptyList()

    companion object {
        private const val BYTES_TO_MB = 1024L * 1024L
        private const val SOCKET_TIMEOUT_MS = 1000
        private const val RETRY_DELAY_MS = 150L
    }
}
