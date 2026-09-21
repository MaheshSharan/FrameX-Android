package com.framex.app.ui.screens.performance

import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.framex.app.gaming.GamingModeState
import com.framex.app.ui.screens.performance.PerformanceViewModel
import com.framex.app.ui.screens.performance.dialogs.*
import com.framex.app.ui.screens.performance.sections.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: PerformanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val gamingState by viewModel.gamingModeState.collectAsState()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val hasShizukuPermission by viewModel.hasShizukuPermission.collectAsState()
    val whitelist by viewModel.whitelist.collectAsState()
    val launcherGames by viewModel.launcherGames.collectAsState()
    val userApps by viewModel.userApps.collectAsState()
    val googleApps by viewModel.googleApps.collectAsState()
    val metricsState by viewModel.metricsState.collectAsState()
    val fixedPerformanceMode by viewModel.fixedPerformanceMode.collectAsState()
    val activeGamingSession by viewModel.activeGamingSession.collectAsState()
    val isVivoSuiteEnabled by viewModel.isVivoSuiteEnabled.collectAsState()

    val nm = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    var hasDndAccess by remember { mutableStateOf(nm.isNotificationPolicyAccessGranted) }
    var hasNotifListenerAccess by remember {
        mutableStateOf(
            android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            )?.contains(context.packageName) == true
        )
    }
    var hasWriteSettingsAccess by remember { mutableStateOf(android.provider.Settings.System.canWrite(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasDndAccess = nm.isNotificationPolicyAccessGranted
                hasNotifListenerAccess = android.provider.Settings.Secure.getString(
                    context.contentResolver, "enabled_notification_listeners"
                )?.contains(context.packageName) == true
                hasWriteSettingsAccess = android.provider.Settings.System.canWrite(context)
                viewModel.loadUserApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val shizukuReady = isShizukuAvailable && hasShizukuPermission
    val canActivate = shizukuReady

    val isActive = gamingState is GamingModeState.Active
    val isBusy = gamingState is GamingModeState.Enabling || gamingState is GamingModeState.Disabling

    val activeColor = Color(0xFF22C55E)
    val primaryRed = MaterialTheme.colorScheme.primary

    val progressTarget = when (val s = gamingState) {
        is GamingModeState.Enabling -> s.progress
        is GamingModeState.Disabling -> 0.5f
        else -> 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(300),
        label = "progress"
    )

    val ramPercentage = if (metricsState.ramTotalGb > 0f) {
        (metricsState.ramUsedGb / metricsState.ramTotalGb * 100f).coerceIn(0f, 100f)
    } else {
        0f
    }

    val storageInfo by produceState(initialValue = Triple(0L, 0L, 0L)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val stat = StatFs(Environment.getDataDirectory().path)
                val totalBytes = stat.blockCountLong * stat.blockSizeLong
                val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
                val totalGb = totalBytes / (1024L * 1024L * 1024L)
                val freeGb = freeBytes / (1024L * 1024L * 1024L)
                val usedGb = totalGb - freeGb
                Triple(usedGb, totalGb, freeGb)
            } catch (e: Exception) {
                Triple(0L, 0L, 0L)
            }
        }
    }

    val scope = rememberCoroutineScope()
    var isBoostingRam by remember { mutableStateOf(false) }
    var showRamResult by remember { mutableStateOf(false) }
    var isOptimizingNet by remember { mutableStateOf(false) }
    var showPingResult by remember { mutableStateOf(false) }
    var isResettingDefaults by remember { mutableStateOf(false) }
    var showResetResult by remember { mutableStateOf(false) }
    var showRamSuccessBanner by remember { mutableStateOf<String?>(null) }
    var activeLatencyDiagnostic by remember { mutableStateOf<Int?>(null) }

    var showAddGameSheet by remember { mutableStateOf(false) }
    var configGamePkg by remember { mutableStateOf<String?>(null) }
    var activeDeployingGamePkg by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "Performance",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            // Hero Gaming Mode card
            item {
                HeroGamingCard(
                    gamingState = gamingState,
                    animatedProgress = animatedProgress,
                    canActivate = canActivate,
                    isActive = isActive,
                    isBusy = isBusy,
                    activeColor = activeColor,
                    primaryRed = primaryRed,
                    activeSession = activeGamingSession,
                    onActivate = { if (canActivate) viewModel.enableGamingMode(context) },
                    onDeactivate = { viewModel.disableGamingMode(context) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Requirements & Status section
            item {
                RequirementsSection(
                    shizukuReady = shizukuReady,
                    isShizukuAvailable = isShizukuAvailable,
                    hasWriteSettingsAccess = hasWriteSettingsAccess,
                    hasDndAccess = hasDndAccess,
                    hasNotifListenerAccess = hasNotifListenerAccess
                )
            }

            // Dedicated Vivo & iQOO Hardware Suite (AOT Speed Compile & Game Space Whitelist)
            if (isVivoSuiteEnabled) {
                item {
                    val perfGameList by viewModel.vivoPerfGameList.collectAsState()
                    val rawPerfGameList by viewModel.rawPerfGameList.collectAsState()
                    VivoPerformanceToolsSection(
                        launcherGames = launcherGames,
                        perfGameList = perfGameList,
                        rawPerfGameList = rawPerfGameList,
                        onRefreshPerfList = { viewModel.refreshVivoPerfGameList() },
                        onAddAllToPerfList = { pkgs, cb -> viewModel.addAllLauncherGamesToPerfList(pkgs, cb) },
                        onRemoveAllFromPerfList = { pkgs, cb -> viewModel.removeAllLauncherGamesFromPerfList(pkgs, cb) },
                        onCompileAll = { pkgs, cb -> viewModel.compileAllLauncherGamesSpeed(pkgs, cb) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }


            // System Health Gauges
            item {
                SystemHealthGaugesSection(
                    ramPercentage = ramPercentage,
                    cpuPercentage = metricsState.cpuPercentage?.toFloat()
                )
            }

            // Storage & Ping card
            item {
                StorageAndPingCard(
                    storageInfo = storageInfo,
                    currentPing = activeLatencyDiagnostic ?: metricsState.pingMs,
                    isOptimizingNet = isOptimizingNet
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Optimization sliders
            item {
                OptimizationSlidersSection(
                    isBoostingRam = isBoostingRam,
                    showRamResult = showRamResult,
                    isOptimizingNet = isOptimizingNet,
                    showPingResult = showPingResult,
                    isResettingDefaults = isResettingDefaults,
                    showResetResult = showResetResult,
                    onBoostRam = {
                        scope.launch {
                            isBoostingRam = true
                            val (freed, stopped) = viewModel.manualBoostRam(whitelist)
                            isBoostingRam = false
                            showRamResult = true
                            showRamSuccessBanner = "Boosted! Freed $freed MB, stopped $stopped apps"
                            delay(2500)
                            showRamResult = false
                            delay(300)
                            showRamSuccessBanner = null
                        }
                    },
                    onCheckPing = {
                        scope.launch {
                            isOptimizingNet = true
                            val pingRes = viewModel.measureNetworkLatency()
                            isOptimizingNet = false
                            showPingResult = true
                            activeLatencyDiagnostic = pingRes ?: 0
                            showRamSuccessBanner = if (pingRes != null) "Latency check complete: $pingRes ms" else "Latency check failed: network unreachable"
                            delay(2500)
                            showPingResult = false
                            delay(300)
                            showRamSuccessBanner = null
                        }
                    },
                    onResetDefaults = {
                        scope.launch {
                            isResettingDefaults = true
                            val resetOk = viewModel.resetToDeviceDefaults()
                            isResettingDefaults = false
                            showResetResult = true
                            showRamSuccessBanner = if (resetOk) "Device settings reset to OS defaults" else "Device reset partially completed"
                            delay(2500)
                            showResetResult = false
                            delay(300)
                            showRamSuccessBanner = null
                        }
                    }
                )
            }

            // Fixed Performance Mode Toggle Card
            item {
                com.framex.app.ui.screens.performance.sections.FixedPerformanceModeCard(
                    enabled = fixedPerformanceMode,
                    onToggle = { viewModel.toggleFixedPerformanceMode(it) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Game Launcher
            item {
                GameLauncherSection(
                    launcherGames = launcherGames,
                    userApps = userApps,
                    onAddGameClicked = { showAddGameSheet = true },
                    onGameConfigClicked = { pkg -> configGamePkg = pkg },
                    onRemoveGame = { pkg -> viewModel.toggleLauncherGame(pkg) }
                )
            }

            // App Whitelist (100% Lazy Composition)
            AppWhitelistSection(
                userApps = userApps,
                whitelist = whitelist,
                onToggleWhitelist = { pkg -> viewModel.toggleWhitelist(pkg) }
            )

            // Google Apps (100% Lazy Composition)
            GoogleAppsSection(
                googleApps = googleApps,
                whitelist = whitelist,
                onToggleWhitelist = { pkg -> viewModel.toggleWhitelist(pkg) }
            )

            // Protected Daemons
            item {
                ProtectedDaemonsSection(daemonsList = viewModel.gamingDaemonsList)
            }

            // OEM Suspended Packages
            item {
                OemPackagesSection(safeToSuspendList = viewModel.safeToSuspendList)
            }

            // System Optimization Audit Console (Activation, Deactivation, 2-Min Pulse logs)
            item {
                val auditLogs by viewModel.vivoAuditLogs.collectAsState()
                val isAuditLoggingEnabled by viewModel.auditLoggingEnabled.collectAsState()
                com.framex.app.ui.screens.performance.sections.SystemAuditLogSection(
                    isLoggingEnabled = isAuditLoggingEnabled,
                    onToggleLogging = { viewModel.setAuditLoggingEnabled(it) },
                    auditLogs = auditLogs,
                    onClearLogs = { viewModel.clearVivoAuditLogs() }
                )
            }
        }

        // Floating Success Banner
        RamSuccessBanner(bannerText = showRamSuccessBanner)

        // Add Game Modal
        if (showAddGameSheet) {
            AddGameModal(
                userApps = userApps,
                launcherGames = launcherGames,
                onDismiss = { showAddGameSheet = false },
                onToggleLauncherGame = { pkg -> viewModel.toggleLauncherGame(pkg) }
            )
        }

        // Per-Game Config Modal
        configGamePkg?.let { targetPkg ->
            GameConfigModal(
                pkg = targetPkg,
                userApps = userApps,
                getGameConfigBoostRam = { p -> viewModel.getGameConfigBoostRam(p) },
                setGameConfigBoostRam = { p, v -> viewModel.setGameConfigBoostRam(p, v) },
                onBoostClicked = { tPkg ->
                    configGamePkg = null
                    activeDeployingGamePkg = tPkg
                },
                onDismiss = { configGamePkg = null },
                isVivo = isVivoSuiteEnabled,
                onToggleMemc = { p, v, cb -> viewModel.toggleMemc(p, v, cb) }
            )
        }

        // Deploying Game Modal
        activeDeployingGamePkg?.let { pkg ->
            DeployingGameModal(
                pkg = pkg,
                onAnimationComplete = { tPkg ->
                    viewModel.launchGameWithOptimizations(context, tPkg) { freed ->
                        activeDeployingGamePkg = null
                        scope.launch {
                            showRamSuccessBanner = "Game boosted successfully! Freed $freed MB of RAM"
                            delay(2500)
                            showRamSuccessBanner = null
                        }
                    }
                }
            )
        }
    }
}
