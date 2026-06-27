package com.framex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.framex.app.ui.theme.FrameXTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: com.framex.app.repository.SettingsRepository,
    private val metricsEngine: com.framex.app.metrics.MetricsEngine
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var windowParams: WindowManager.LayoutParams? = null

    fun showOverlay() {
        android.util.Log.d("FrameX_Overlay", "Attempting to show overlay...")
        if (composeView != null) {
            android.util.Log.d("FrameX_Overlay", "Overlay already exists, returning.")
            return
        }

        android.util.Log.d("FrameX_Overlay", "Creating new ComposeView...")
        composeView = ComposeView(context).apply {
            setContent {
                FrameXTheme {
                    val mode by settingsRepository.overlayMode.collectAsState()
                    val enabledModules by settingsRepository.enabledModules.collectAsState()
                    val opacity by settingsRepository.overlayOpacity.collectAsState()
                    val textSize by settingsRepository.overlayTextSize.collectAsState()
                    val useMonospace by settingsRepository.overlayUseMonospace.collectAsState()
                    val colorIndex by settingsRepository.overlayColorIndex.collectAsState()
                    val bgColorIndex by settingsRepository.overlayBgColorIndex.collectAsState()
                    val borderColorIndex by settingsRepository.overlayBorderColorIndex.collectAsState()
                    val textColorIndex by settingsRepository.overlayTextColorIndex.collectAsState()
                    val metricsState by metricsEngine.metricsState.collectAsState()
                    
                    OverlayContent(
                        mode = mode,
                        enabledModules = enabledModules,
                        opacity = opacity,
                        textSize = textSize,
                        useMonospace = useMonospace,
                        colorIndex = colorIndex,
                        bgColorIndex = bgColorIndex,
                        borderColorIndex = borderColorIndex,
                        textColorIndex = textColorIndex,
                        metricsState = metricsState,
                        onDrag = { dx, dy ->
                            windowParams?.let { p ->
                                p.x += dx.toInt()
                                p.y += dy.toInt()
                                
                                val screenSize = getScreenSize()
                                val viewWidth = composeView?.width ?: 0
                                val viewHeight = composeView?.height ?: 0
                                p.x = p.x.coerceIn(0, (screenSize.x - viewWidth).coerceAtLeast(0))
                                p.y = p.y.coerceIn(0, (screenSize.y - viewHeight).coerceAtLeast(0))
                                
                                composeView?.let { windowManager.updateViewLayout(it, p) }
                            }
                        },
                        onDragEnd = {
                            windowParams?.let { p ->
                                val currentOrientation = context.resources.configuration.orientation
                                val isCurrentLandscape = currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                settingsRepository.setOverlayPosition(isCurrentLandscape, p.x, p.y)
                            }
                        },
                        onModeToggle = {
                            // Long-press the overlay to cycle Compact → Minimal → Expanded → Compact
                            // without leaving the game.
                            val modes = listOf("Compact", "Minimal", "Expanded")
                            val current = settingsRepository.overlayMode.value
                            val next = modes[(modes.indexOf(current) + 1) % modes.size]
                            settingsRepository.setOverlayMode(next)
                        }
                    )
                }
            }
        }

        val lifecycleOwner = OverlayLifecycleOwner()
        overlayLifecycleOwner = lifecycleOwner
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        composeView?.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView?.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        composeView?.setViewTreeViewModelStoreOwner(lifecycleOwner)
        // Explicitly transparent so the window's RGBA_8888 pixel format can show through.
        // Without this, the View default background can block alpha compositing on some ROMs.
        composeView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        composeView?.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val width = right - left
            val height = bottom - top
            val oldWidth = oldRight - oldLeft
            val oldHeight = oldBottom - oldTop
            
            if (width > 0 && height > 0 && (width != oldWidth || height != oldHeight)) {
                val currentOrientation = context.resources.configuration.orientation
                val isCurrentLandscape = currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val screenSize = getScreenSize()
                val (currentX, currentY) = settingsRepository.getOverlayPosition(isCurrentLandscape)
                
                var targetX = currentX
                var targetY = currentY
                
                if (targetX == -1 || targetY == -1) {
                    targetX = (screenSize.x - width) / 2
                    targetY = (screenSize.y - height) / 2
                } else {
                    targetX = targetX.coerceIn(0, (screenSize.x - width).coerceAtLeast(0))
                    targetY = targetY.coerceIn(0, (screenSize.y - height).coerceAtLeast(0))
                }
                
                windowParams?.let { params ->
                    params.x = targetX
                    params.y = targetY
                    composeView?.let { view ->
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val (savedX, savedY) = settingsRepository.getOverlayPosition(isLandscape)

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            // RGBA_8888 gives an explicit 8-bit alpha channel — more reliable than TRANSLUCENT
            // on MediaTek / vivo OEM ROMs for accurate opacity rendering.
            android.graphics.PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX == -1) 100 else savedX
            y = if (savedY == -1) 100 else savedY
        }

        try {
            windowManager.addView(composeView, windowParams)
            android.util.Log.d("FrameX_Overlay", "Overlay successfully added to WindowManager.")
        } catch (e: Exception) {
            android.util.Log.e("FrameX_Overlay", "Failed to add overlay to WindowManager: ${e.message}", e)
        }
    }

    fun hideOverlay() {
        composeView?.let {
            windowManager.removeView(it)
            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            composeView = null
            overlayLifecycleOwner = null
            windowParams = null
        }
    }

    fun handleOrientationChange(orientation: Int) {
        val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val screenSize = getScreenSize()
        val viewWidth = composeView?.width ?: 0
        val viewHeight = composeView?.height ?: 0

        if (viewWidth > 0 && viewHeight > 0) {
            val (savedX, savedY) = settingsRepository.getOverlayPosition(isLandscape)
            var targetX = savedX
            var targetY = savedY

            if (targetX == -1 || targetY == -1) {
                targetX = (screenSize.x - viewWidth) / 2
                targetY = (screenSize.y - viewHeight) / 2
            } else {
                targetX = targetX.coerceIn(0, (screenSize.x - viewWidth).coerceAtLeast(0))
                targetY = targetY.coerceIn(0, (screenSize.y - viewHeight).coerceAtLeast(0))
            }

            windowParams?.let { params ->
                params.x = targetX
                params.y = targetY
                composeView?.let { view ->
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun getScreenSize(): android.graphics.Point {
        val size = android.graphics.Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            size.x = bounds.width()
            size.y = bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            display.getSize(size)
        }
        return size
    }
}

@Composable
fun OverlayContent(
    mode: String,
    enabledModules: Set<String>,
    opacity: Float,
    textSize: Int,
    useMonospace: Boolean,
    colorIndex: Int,
    bgColorIndex: Int = 0,
    borderColorIndex: Int = 0,
    textColorIndex: Int = 0,
    metricsState: com.framex.app.metrics.MetricsState,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onModeToggle: () -> Unit = {}
) {
    val availableModules = listOf(
        Triple("fps", "FPS", "${metricsState.fps}") to Icons.Default.Speed,
        Triple("cpu", "CPU", "${metricsState.cpuMhz} MHz") to Icons.Default.Memory,
        Triple("ram", "RAM", String.format("%.1f GB", metricsState.ramUsedGb)) to Icons.Default.DeveloperBoard,
        Triple("temp", "TEMP", String.format("%.1f°C", metricsState.batteryTempC)) to Icons.Default.DeviceThermostat,
        Triple("net", "NET", if (metricsState.networkRxKbps > 1024) String.format("%.1f MB/s", (metricsState.networkRxKbps + metricsState.networkTxKbps) / 1024f) else String.format("%.0f KB/s", metricsState.networkRxKbps + metricsState.networkTxKbps)) to Icons.Default.NetworkCheck
    )
    
    val activeList = availableModules.filter { enabledModules.contains(it.first.first) }
    
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF60A5FA),
        Color(0xFF34D399),
        Color(0xFF2DD4BF),
        Color(0xFFA78BFA),
        Color(0xFFFBBF24)
    )
    val accentColor = colors[colorIndex]
    val fontFamily = if (useMonospace) androidx.compose.ui.text.font.FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily
    val textScale = when(textSize) { 0 -> 0.8f; 2 -> 1.2f; else -> 1.0f }

    // Background: chosen base color with opacity applied (Transparent ignores opacity slider).
    val bgColors = listOf(Color.Black, Color(0xFF0D1117), Color(0xFF1C1C1E), Color.Transparent)
    val bgBase = bgColors.getOrElse(bgColorIndex) { Color.Black }
    val effectiveBg = if (bgBase == Color.Transparent) Color.Transparent else bgBase.copy(alpha = opacity)

    // Border color: Accent / None / White-Subtle / Ghost
    val effectiveBorderColor = when (borderColorIndex) {
        1 -> Color.Transparent
        2 -> Color.White.copy(alpha = 0.2f)
        3 -> Color.White.copy(alpha = 0.05f)
        else -> accentColor
    }
    val effectiveBorderWidth = if (borderColorIndex == 1) 0.dp else 1.dp

    // Metric value text color: White / Accent / Silver / Auto-FPS coloring
    val autoFpsColor = when {
        metricsState.fps >= 60 -> Color(0xFF22C55E)
        metricsState.fps >= 30 -> Color(0xFFFBBF24)
        else -> Color(0xFFEF4444)
    }
    val textValueColor = when (textColorIndex) {
        1 -> accentColor
        2 -> Color(0xFFCBD5E1)
        3 -> autoFpsColor
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                // Drag to reposition. onDragEnd fires when finger lifts — persists the position.
                detectDragGestures(
                    onDragEnd = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                // Long-press (stationary ≥500ms) cycles the overlay display mode.
                // Does not conflict with drag since drag requires movement; this requires stillness.
                detectTapGestures(onLongPress = { onModeToggle() })
            }
            .clip(RoundedCornerShape(8.dp))
            .background(effectiveBg)
            .border(effectiveBorderWidth, effectiveBorderColor, RoundedCornerShape(8.dp))
            .padding(if (mode == "Minimal") (4 * textScale).dp else (8 * textScale).dp)
    ) {
        if (mode == "Expanded") {
            Column(verticalArrangement = Arrangement.spacedBy((8 * textScale).dp)) {
                activeList.forEach { (info, icon) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size((16 * textScale).dp))
                        Spacer(modifier = Modifier.width((8 * textScale).dp))
                        Text(info.second, color = Color.Gray, fontSize = (10 * textScale).sp, fontFamily = fontFamily, modifier = Modifier.weight(1f))
                        Text(info.third, color = textValueColor, fontSize = (12 * textScale).sp, fontFamily = fontFamily, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy((12 * textScale).dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = (4 * textScale).dp)
            ) {
                activeList.forEachIndexed { index, (info, _) ->
                    if (mode == "Minimal") {
                        Text(info.third, color = textValueColor, fontFamily = fontFamily, fontSize = (14 * textScale).sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(info.second, color = Color.Gray, fontFamily = fontFamily, fontSize = (10 * textScale).sp, fontWeight = FontWeight.Bold)
                            Text(info.third, color = textValueColor, fontFamily = fontFamily, fontSize = (16 * textScale).sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    if (index < activeList.size - 1) {
                        Box(modifier = Modifier.width(1.dp).height(if (mode == "Minimal") (12 * textScale).dp else (24 * textScale).dp).background(Color.DarkGray))
                    }
                }
                if (activeList.isEmpty()) {
                    Text("No modules", color = Color.Gray, fontFamily = fontFamily, fontSize = (12 * textScale).sp)
                }
            }
        }
    }
}

private class OverlayLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }
}
