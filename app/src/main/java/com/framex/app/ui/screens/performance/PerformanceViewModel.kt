package com.framex.app.ui.screens.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.gaming.AppInfo
import com.framex.app.gaming.GamingModeEngine
import com.framex.app.gaming.GamingModeService
import com.framex.app.gaming.GamingModeState
import com.framex.app.repository.SettingsRepository
import com.framex.app.shizuku.ShizukuManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val gamingModeEngine: GamingModeEngine,
    private val shizukuManager: ShizukuManager,
    private val settingsRepository: SettingsRepository,
    private val metricsEngine: com.framex.app.metrics.MetricsEngine
) : ViewModel() {

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

    fun toggleCpuPriorityLock(enabled: Boolean) = settingsRepository.setCpuPriorityLock(enabled)
    fun toggleNetworkFirewall(enabled: Boolean) = settingsRepository.setNetworkFirewall(enabled)
    fun toggleRefreshRateLock(enabled: Boolean) = settingsRepository.setRefreshRateLock(enabled)
    fun toggleTouchBoost(enabled: Boolean) = settingsRepository.setTouchBoost(enabled)
    fun toggleFramePacingOverlay(enabled: Boolean) = settingsRepository.setFramePacingOverlay(enabled)

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
            _userApps.value = withContext(Dispatchers.IO) {
                gamingModeEngine.getInstalledUserApps()
            }
            _googleApps.value = withContext(Dispatchers.IO) {
                gamingModeEngine.getGoogleAppsForWhitelist()
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

    fun canWriteSettings(context: Context): Boolean = android.provider.Settings.System.canWrite(context)

    suspend fun manualBoostRam(whitelist: Set<String>): Pair<Long, Int> {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfoBefore = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfoBefore)
        val availBefore = memInfoBefore.availMem

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
                    } catch (_: Exception) {}
                }
                shizukuManager.executeCommand("am kill-all")
            } catch (_: Exception) {}
        }
        System.gc()

        val memInfoAfter = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfoAfter)
        val availAfter = memInfoAfter.availMem

        val freed = ((availAfter - availBefore) / (1024L * 1024L)).coerceAtLeast(0L)
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
            } catch (e: Exception) {}
        }
        var minPing: Int? = null
        for (i in 1..3) {
            try {
                val start = System.currentTimeMillis()
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 1000)
                val latency = (System.currentTimeMillis() - start).toInt()
                socket.close()
                minPing = minOf(minPing ?: latency, latency)
            } catch (e: Exception) {}
            delay(150)
        }
        return minPing
    }

    fun enableGamingMode(context: Context) {
        viewModelScope.launch {
            val currentWhitelist = settingsRepository.gamingModeWhitelist.value
            gamingModeEngine.enableGamingMode(currentWhitelist)
            if (gamingModeEngine.state.value == GamingModeState.Active) {
                context.startForegroundService(Intent(context, GamingModeService::class.java))
            }
        }
    }

    fun launchGameWithOptimizations(context: Context, packageName: String, onLaunched: (Long) -> Unit) {
        viewModelScope.launch {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfoBefore = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfoBefore)
            val availBefore = memInfoBefore.availMem

            val currentWhitelist = settingsRepository.gamingModeWhitelist.value
            gamingModeEngine.enableGamingMode(currentWhitelist, packageName)

            context.startForegroundService(Intent(context, GamingModeService::class.java))

            val memInfoAfter = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfoAfter)
            val availAfter = memInfoAfter.availMem
            val freedMb = ((availAfter - availBefore) / (1024L * 1024L)).coerceAtLeast(0L)

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
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

    val safeToSuspendList: List<String> get() = gamingModeEngine.SAFE_TO_SUSPEND
    val googleSafeToSuspendList: List<String> get() = gamingModeEngine.GOOGLE_SAFE_TO_SUSPEND
    val gamingDaemonsList: List<String> get() = gamingModeEngine.GAMING_DAEMONS
}
