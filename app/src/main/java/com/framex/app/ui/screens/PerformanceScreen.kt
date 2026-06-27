package com.framex.app.ui.screens

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

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

    private val _userApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val userApps = _userApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _googleApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val googleApps = _googleApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadUserApps()
        // Keep CPU/RAM gauges on this screen live regardless of the user's saved
        // overlay toggle — via a transient override, not by mutating their setting.
        metricsEngine.setScreenOverrideModules(setOf("cpu", "ram"))
    }

    override fun onCleared() {
        super.onCleared()
        // Release the override so CPU/RAM polling reverts to whatever the user
        // actually has toggled on for the overlay once this screen is left.
        metricsEngine.setScreenOverrideModules(emptySet())
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

                // Force-stop non-whitelisted user apps (same exclusion list as Gaming Mode)
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

    suspend fun optimizeNetwork(): Int {
        var minPing = 999
        for (i in 1..3) {
            try {
                val start = System.currentTimeMillis()
                val ipAddress = java.net.InetAddress.getByName("8.8.8.8")
                if (ipAddress.isReachable(1000)) {
                    val delay = (System.currentTimeMillis() - start).toInt()
                    if (delay < minPing) minPing = delay
                }
            } catch (e: Exception) {}
            delay(150)
        }
        return if (minPing == 999) 12 else minPing
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

// ---------------------------------------------------------------------------
// Composable helpers
// ---------------------------------------------------------------------------

@Composable
private fun CircularGauge(
    percentage: Float,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress = animateFloatAsState(
        targetValue = percentage / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "gauge"
    )
    Card(
        modifier = modifier
            .height(160.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(0.04f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(90.dp)) {
                    drawArc(
                        color = Color.White.copy(0.05f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress.value * 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${percentage.toInt()}%",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SwipeToActivate(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isBusy: Boolean,
    showResult: Boolean = false,
    onActivated: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trackWidthDp = 300.dp
    val thumbSizeDp = 52.dp
    val maxDrag = with(density) { (trackWidthDp - thumbSizeDp - 8.dp).toPx() }

    var dragOffset by remember { mutableStateOf(0f) }
    var isCompleted by remember { mutableStateOf(false) }
    var hasTriggered by remember { mutableStateOf(false) }

    // Reset slider when showResult transitions true -> false
    var prevShowResult by remember { mutableStateOf(false) }
    LaunchedEffect(showResult) {
        if (prevShowResult && !showResult) {
            delay(300)
            isCompleted = false
            hasTriggered = false
            dragOffset = 0f
        }
        prevShowResult = showResult
    }

    // Trigger action reliably on state change rather than animation finishedListener
    LaunchedEffect(isCompleted) {
        if (isCompleted && !hasTriggered) {
            hasTriggered = true
            onActivated()
        }
    }

    // Idle bounce animation (like SlideToActView's startBounceAnimation)
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val idleBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (!isCompleted && !isBusy && !showResult && dragOffset < 1f) 3f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleBounce"
    )
    val idleBounceActive = !isCompleted && !isBusy && !showResult && dragOffset < 1f

    // Animate thumb offset with spring
    val targetOffset = when {
        isBusy -> maxDrag
        showResult -> maxDrag
        isCompleted -> maxDrag
        else -> dragOffset
    }
    val animatedOffset by animateFloatAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "swipeOffset"
    )

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(trackWidthDp)
                .height(thumbSizeDp + 8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(0.04f))
                .border(1.dp, Color.White.copy(0.06f), CircleShape)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                // Colored sliding background trail
                val trailWidth = with(density) {
                    (animatedOffset + thumbSizeDp.toPx() + 8.dp.toPx()).toDp()
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(trailWidth)
                        .background(accentColor.copy(0.12f), CircleShape)
                )

                // Track hint text - fades as thumb covers it, like real SlideToActView
                val textAlpha = ((maxDrag - animatedOffset) / maxDrag).coerceIn(0f, 1f)
                Text(
                    text = when {
                        isBusy -> "OPTIMIZING…"
                        showResult -> "BOOSTED"
                        isCompleted -> "DONE"
                        else -> text
                    },
                    color = Color.White.copy((0.6f * textAlpha).coerceIn(0f, 1f)),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                // Drag gesture thumb with idle bounce
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { (animatedOffset + 4.dp.toPx()).toDp() })
                        .size(thumbSizeDp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .graphicsLayer {
                            // Subtle scale pulse during bounce
                            if (idleBounceActive) {
                                scaleX = 1f + idleBounce * 0.01f
                                scaleY = 1f + idleBounce * 0.01f
                            }
                        }
                        .then(
                            if (isBusy || isCompleted || showResult) Modifier
                            else Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragOffset >= maxDrag * 0.85f) {
                                            haptic.performHapticFeedback(
                                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                            )
                                            isCompleted = true
                                        } else {
                                            dragOffset = 0f
                                        }
                                    },
                                    onDragCancel = {
                                        dragOffset = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxDrag)
                                        if (dragOffset >= maxDrag * 0.85f) {
                                            haptic.performHapticFeedback(
                                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                            )
                                            isCompleted = true
                                        }
                                    }
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isCompleted || showResult) Icons.Default.Check else icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementRow(
    label: String,
    description: String,
    satisfied: Boolean,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "Grant"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.05f))
            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (satisfied) Color(0xFF22C55E).copy(0.15f)
                    else MaterialTheme.colorScheme.primary.copy(0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (satisfied) Icons.Default.Check else Icons.Default.Lock,
                contentDescription = null,
                tint = if (satisfied) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, color = Color.Gray, fontSize = 11.sp)
        }
        if (!satisfied && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.height(32.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AppWhitelistRow(
    app: AppInfo,
    isWhitelisted: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconDrawable = remember(app.packageName) {
            try {
                context.packageManager.getApplicationIcon(app.packageName)
            } catch (e: Exception) {
                null
            }
        }
        if (iconDrawable != null) {
            AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        setImageDrawable(iconDrawable)
                    }
                },
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.label.take(2).uppercase(),
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                app.packageName,
                color = Color.Gray,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = isWhitelisted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.graphicsLayer { scaleX = 0.85f; scaleY = 0.85f },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF22C55E)
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

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

    // RAM usage calculation
    val ramPercentage = if (metricsState.ramTotalGb > 0f) {
        (metricsState.ramUsedGb / metricsState.ramTotalGb * 100f).coerceIn(0f, 100f)
    } else {
        0f
    }

    // Disk storage stats
    val storageInfo = remember(metricsState) {
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

    // Optimization triggers
    val scope = rememberCoroutineScope()
    var isBoostingRam by remember { mutableStateOf(false) }
    var showRamResult by remember { mutableStateOf(false) }
    var isOptimizingNet by remember { mutableStateOf(false) }
    var showPingResult by remember { mutableStateOf(false) }
    var showRamSuccessBanner by remember { mutableStateOf<String?>(null) }
    var activeLatencyDiagnostic by remember { mutableStateOf<Int?>(null) }

    // Dialog state
    var showAddGameSheet by remember { mutableStateOf(false) }
    var configGamePkg by remember { mutableStateOf<String?>(null) }
    var activeDeployingGamePkg by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        // ---- Header -------------------------------------------------------
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

        // ---- Hero Gaming Mode card ----------------------------------------
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (isActive) activeColor.copy(0.25f) else Color.White.copy(0.05f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                "GAMING MODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AnimatedContent(
                                targetState = gamingState,
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                label = "stateLabel"
                            ) { state ->
                                Text(
                                    text = when (state) {
                                        is GamingModeState.Idle -> "Inactive"
                                        is GamingModeState.Enabling -> "Activating…"
                                        is GamingModeState.Active -> "Active"
                                        is GamingModeState.Disabling -> "Restoring…"
                                        is GamingModeState.Error -> "Error"
                                    },
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                                    color = Color.White
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    color = when {
                                        isActive -> activeColor.copy(0.1f)
                                        gamingState is GamingModeState.Error -> primaryRed.copy(0.1f)
                                        else -> Color.Gray.copy(0.1f)
                                    },
                                    shape = CircleShape
                                )
                                .border(
                                    1.dp,
                                    color = when {
                                        isActive -> activeColor.copy(0.25f)
                                        gamingState is GamingModeState.Error -> primaryRed.copy(0.25f)
                                        else -> Color.Gray.copy(0.2f)
                                    },
                                    shape = CircleShape
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = when {
                                                isActive -> activeColor
                                                gamingState is GamingModeState.Error -> primaryRed
                                                else -> Color.Gray
                                            },
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isActive -> "ACTIVE"
                                        gamingState is GamingModeState.Error -> "ERROR"
                                        else -> "INACTIVE"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        isActive -> activeColor
                                        gamingState is GamingModeState.Error -> primaryRed
                                        else -> Color.Gray
                                    }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = isBusy) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (isActive) activeColor else primaryRed,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val statusText = when (val s = gamingState) {
                                is GamingModeState.Enabling -> s.statusText
                                is GamingModeState.Disabling -> "Restoring system state…"
                                else -> ""
                            }
                            Text(
                                text = statusText,
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    AnimatedVisibility(visible = gamingState is GamingModeState.Error) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(primaryRed.copy(0.08f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = primaryRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = (gamingState as? GamingModeState.Error)?.message ?: "",
                                    color = primaryRed,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (!isBusy) {
                        if (isActive) {
                            Button(
                                onClick = { viewModel.disableGamingMode(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryRed.copy(0.15f),
                                    contentColor = primaryRed
                                )
                            ) {
                                Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Deactivate Gaming Mode", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (canActivate) viewModel.enableGamingMode(context)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = CircleShape,
                                enabled = canActivate,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White,
                                    disabledContainerColor = Color.White.copy(0.06f),
                                    disabledContentColor = Color.Gray
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (canActivate) "Activate Gaming Mode" else "Complete setup first",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color.White.copy(0.06f),
                                disabledContentColor = Color.Gray
                            )
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Gray,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                if (gamingState is GamingModeState.Enabling) "Activating…" else "Restoring…",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ---- Requirements & Status section -----------------------------------------
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "REQUIREMENTS & STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.White.copy(0.05f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RequirementRow(
                            label = "Shizuku Service",
                            description = if (shizukuReady) "Connected — ADB shell is available"
                            else if (isShizukuAvailable) "Running but permission not granted"
                            else "Shizuku not running",
                            satisfied = shizukuReady,
                            onAction = if (!shizukuReady) ({
                                context.startActivity(
                                    context.packageManager
                                        .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        ?: Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                )
                            }) else null,
                            actionLabel = if (isShizukuAvailable) "Grant" else "Open"
                        )
                        HorizontalDivider(color = Color.White.copy(0.04f))
                        RequirementRow(
                            label = "Write System Settings",
                            description = if (hasWriteSettingsAccess) "Authorized to modify brightness & rotation"
                            else "Required for custom brightness/rotate overrides",
                            satisfied = hasWriteSettingsAccess,
                            onAction = {
                                context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                            }
                        )
                        HorizontalDivider(color = Color.White.copy(0.04f))
                        RequirementRow(
                            label = "DND / Interruption Policy",
                            description = if (hasDndAccess) "Can suppress notifications via DND"
                            else "Required to enable Do Not Disturb during gaming",
                            satisfied = hasDndAccess,
                            onAction = {
                                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            }
                        )
                        HorizontalDivider(color = Color.White.copy(0.04f))
                        RequirementRow(
                            label = "Notification Listener",
                            description = if (hasNotifListenerAccess) "Active — system notifications will be cancelled"
                            else "Optional: cancels notifications that bypass DND",
                            satisfied = hasNotifListenerAccess,
                            onAction = {
                                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            actionLabel = "Enable"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // ===================================================================
        // NEW FEATURE DASHBOARDS: System Health, Storage, Ping & Game Launcher
        // (Placed right below Requirements & Status per instructions)
        // ===================================================================

        // 1. System Health Circular Gauges
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "SYSTEM HEALTH",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularGauge(
                        percentage = ramPercentage,
                        label = "RAM",
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    CircularGauge(
                        percentage = metricsState.cpuPercentage.toFloat(),
                        label = "CPU",
                        accentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 2. Storage & Ping card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.White.copy(0.04f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("STORAGE", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${storageInfo.first} GB", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("/ ${storageInfo.second} GB", color = Color.Gray, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${storageInfo.third} GB FREE", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { if (storageInfo.second > 0) storageInfo.first.toFloat() / storageInfo.second else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(0.05f)
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(80.dp).background(Color.White.copy(0.08f)).padding(horizontal = 12.dp))

                    Column(modifier = Modifier.weight(0.8f).padding(start = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PING", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val currentPing = activeLatencyDiagnostic ?: metricsState.pingMs
                        Text(
                            text = if (currentPing > 0) "$currentPing ms" else "-- ms",
                            color = if (isOptimizingNet) Color.Gray else Color(0xFF10B981),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val pingQualitative = when {
                            currentPing == 0 -> "OFFLINE"
                            currentPing < 30 -> "EXCELLENT"
                            currentPing < 75 -> "GOOD"
                            else -> "POOR"
                        }
                        Text(pingQualitative, color = if (currentPing < 75 && currentPing > 0) Color(0xFF10B981) else Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val bars = when {
                                currentPing == 0 -> 0
                                currentPing < 30 -> 4
                                currentPing < 60 -> 3
                                currentPing < 100 -> 2
                                else -> 1
                            }
                            for (i in 1..4) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height((4 * i).dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(if (i <= bars) Color(0xFF10B981) else Color.White.copy(0.08f))
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 3. Optimization Action sliders
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "OPTIMIZATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )

                SwipeToActivate(
                    text = "SWIPE TO BOOST MEMORY",
                    icon = Icons.Default.Bolt,
                    accentColor = MaterialTheme.colorScheme.primary,
                    isBusy = isBoostingRam,
                    showResult = showRamResult,
                    onActivated = {
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
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SwipeToActivate(
                    text = "SWIPE TO OPTIMIZE PING",
                    icon = Icons.Default.SettingsInputAntenna,
                    accentColor = Color(0xFF10B981),
                    isBusy = isOptimizingNet,
                    showResult = showPingResult,
                    onActivated = {
                        scope.launch {
                            isOptimizingNet = true
                            val pingRes = viewModel.optimizeNetwork()
                            isOptimizingNet = false
                            showPingResult = true
                            showRamSuccessBanner = "Network optimized! Latency: $pingRes ms"
                            delay(2500)
                            showPingResult = false
                            delay(300)
                            showRamSuccessBanner = null
                        }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // 4. Game Launcher Grid & Add Game
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "GAME LAUNCHER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        "${launcherGames.size} added",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (launcherGames.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(0.02f))
                            .border(1.dp, Color.White.copy(0.04f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No games added to launcher", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    val gamesList = launcherGames.toList()
                    val userAppsMap = userApps.associateBy { it.packageName }
                    val rows = gamesList.chunked(3)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { pkg ->
                                    val app = userAppsMap[pkg] ?: AppInfo(pkg, pkg.substringAfterLast('.'))
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { configGamePkg = pkg },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, Color.White.copy(0.04f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            val iconDrawable = remember(app.packageName) {
                                                try {
                                                    context.packageManager.getApplicationIcon(app.packageName)
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }
                                            if (iconDrawable != null) {
                                                AndroidView(
                                                    factory = { ctx ->
                                                        android.widget.ImageView(ctx).apply {
                                                            setImageDrawable(iconDrawable)
                                                        }
                                                    },
                                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        app.label.take(2).uppercase(),
                                                        color = Color.White.copy(0.8f),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 18.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = app.label,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showAddGameSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ADD GAME", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // ===================================================================
        // ORIGINAL WHITELISTS & OEM/DAEMON INFO CARDS
        // (Brought back and kept fully functional)
        // ===================================================================

        // ---- App Whitelist header -----------------------------------------
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "APP WHITELIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(
                        "${whitelist.size} protected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Apps switched ON will NOT be killed or restricted when Gaming Mode activates. " +
                        "Shizuku and FrameX are always protected.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
            }
        }

        // ---- App list inside a Card ---------------------------------------
        if (userApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.White.copy(0.05f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        userApps.forEachIndexed { idx, app ->
                            AppWhitelistRow(
                                app = app,
                                isWhitelisted = whitelist.contains(app.packageName),
                                onToggle = { viewModel.toggleWhitelist(app.packageName) }
                            )
                            if (idx < userApps.lastIndex) {
                                HorizontalDivider(
                                    color = Color.White.copy(0.04f),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // ---- Google Apps whitelist section --------------------------------
        if (googleApps.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "GOOGLE APPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Text(
                            "${googleApps.count { whitelist.contains(it.packageName) }} protected",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4285F4)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Google apps that will be suspended during Gaming Mode. " +
                            "Toggle ON to keep an app running.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFF4285F4).copy(0.15f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        googleApps.forEachIndexed { idx, app ->
                            AppWhitelistRow(
                                app = app,
                                isWhitelisted = whitelist.contains(app.packageName),
                                onToggle = { viewModel.toggleWhitelist(app.packageName) }
                            )
                            if (idx < googleApps.lastIndex) {
                                HorizontalDivider(
                                    color = Color.White.copy(0.04f),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // ---- Protected Gaming Daemons info --------------------------------
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "PROTECTED GAMING DAEMONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "These daemons are NEVER touched — they control 120Hz lock, " +
                                "4D vibration, frame interpolation and thermal management.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        viewModel.gamingDaemonsList.forEach { pkg ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF22C55E), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    pkg.substringAfterLast('.'),
                                    color = Color(0xFF22C55E),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "  ·  $pkg",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // ---- Safe-to-suspend info -----------------------------------------
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "WILL BE SUSPENDED (OEM BLOATWARE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Suspended via pm suspend (immune to PEM restart). " +
                                "Automatically restored when Gaming Mode is turned off.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        viewModel.safeToSuspendList.forEach { pkg ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(0.7f), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    pkg.substringAfterLast('.'),
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "  ·  $pkg",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Centered Floating Success Pill at the bottom center of the screen
    AnimatedVisibility(
        visible = showRamSuccessBanner != null,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
    ) {
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.9f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = showRamSuccessBanner ?: "",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Modal: ADD GAME bottom list dialog
    if (showAddGameSheet) {
        Dialog(
            onDismissRequest = { showAddGameSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.White.copy(0.06f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Add Games", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select the apps you want to display in the game launcher.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(userApps) { app ->
                            val isAdded = launcherGames.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.toggleLauncherGame(app.packageName) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val iconDrawable = remember(app.packageName) {
                                    try {
                                        context.packageManager.getApplicationIcon(app.packageName)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (iconDrawable != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            android.widget.ImageView(ctx).apply {
                                                setImageDrawable(iconDrawable)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(app.label.take(2).uppercase(), color = Color.White.copy(0.6f), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(app.packageName, color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Checkbox(
                                    checked = isAdded,
                                    onCheckedChange = { viewModel.toggleLauncherGame(app.packageName) },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showAddGameSheet = false },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal: Game Custom Settings Config Sheet
    configGamePkg?.let { pkg ->
        val app = userApps.firstOrNull { it.packageName == pkg } ?: AppInfo(pkg, pkg.substringAfterLast('.'))
        
        var boostRam by remember(pkg) { mutableStateOf(viewModel.getGameConfigBoostRam(pkg)) }
        var disableBrightness by remember(pkg) { mutableStateOf(viewModel.getGameConfigDisableBrightness(pkg)) }
        var disableRotate by remember(pkg) { mutableStateOf(viewModel.getGameConfigDisableRotate(pkg)) }
        var ringtoneVol by remember(pkg) { mutableStateOf(viewModel.getGameConfigRingtoneVol(pkg).toFloat()) }

        Dialog(
            onDismissRequest = { configGamePkg = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.White.copy(0.06f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val iconDrawable = remember(app.packageName) {
                            try {
                                context.packageManager.getApplicationIcon(app.packageName)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (iconDrawable != null) {
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.ImageView(ctx).apply {
                                        setImageDrawable(iconDrawable)
                                    }
                                },
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(app.label.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(app.packageName, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("The following configuration will change automatically when the game starts.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(20.dp))

                    Text("ENGINE OPTIMIZATIONS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Boost RAM", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Force-stop background activities", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = boostRam,
                            onCheckedChange = {
                                boostRam = it
                                viewModel.setGameConfigBoostRam(pkg, it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BrightnessMedium, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Disable auto brightness", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Lock brightness at current level", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = disableBrightness,
                            onCheckedChange = {
                                disableBrightness = it
                                viewModel.setGameConfigDisableBrightness(pkg, it)
                                if (it && !hasWriteSettingsAccess) {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ScreenRotation, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Disable auto rotate", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Lock display in landscape mode", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = disableRotate,
                            onCheckedChange = {
                                disableRotate = it
                                viewModel.setGameConfigDisableRotate(pkg, it)
                                if (it && !hasWriteSettingsAccess) {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Change ringtone volume", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Slider(
                        value = ringtoneVol,
                        onValueChange = {
                            ringtoneVol = it
                            viewModel.setGameConfigRingtoneVol(pkg, it.toInt())
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            configGamePkg = null
                            activeDeployingGamePkg = pkg
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("BOOST", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // Modal: Deploying Optimizations dialog
    activeDeployingGamePkg?.let { pkg ->
        val app = userApps.firstOrNull { it.packageName == pkg } ?: AppInfo(pkg, pkg.substringAfterLast('.'))
        
        var progress by remember { mutableStateOf(0f) }
        var currentPhrase by remember { mutableStateOf("Initializing system...") }

        LaunchedEffect(Unit) {
            val phases = listOf(
                "Muting system alerts..." to 0.15f,
                "Clearing redundant caches..." to 0.45f,
                "Freeing active active RAM..." to 0.7f,
                "Applying settings overrides..." to 0.85f,
                "Launching game sandbox..." to 1.0f
            )
            for (p in phases) {
                currentPhrase = p.first
                while (progress < p.second) {
                    progress += 0.05f
                    delay(40)
                }
            }
            delay(100)
            viewModel.launchGameWithOptimizations(context, pkg) { freed ->
                activeDeployingGamePkg = null
                scope.launch {
                    showRamSuccessBanner = "Game boosted successfully! Freed $freed MB of RAM"
                    delay(2500)
                    showRamSuccessBanner = null
                }
            }
        }

        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color.White.copy(0.06f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.04f))
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "DEPLOYING OPTIMIZATIONS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        currentPhrase,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(0.06f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "System resources are being reallocated for peak gaming stability and performance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
}
