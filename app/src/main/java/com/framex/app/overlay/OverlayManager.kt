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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.framex.app.metrics.METRIC_MODULE_REGISTRY
import com.framex.app.metrics.metricValueFor
import com.framex.app.metrics.resolveMetricModuleOrder
import com.framex.app.ui.theme.FrameXTheme
import com.framex.app.utils.FrameXLog
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
        FrameXLog.d("Attempting to show overlay...")
        if (composeView != null) {
            FrameXLog.d("Overlay already exists, returning.")
            return
        }

        FrameXLog.d("Creating new ComposeView...")
        composeView = ComposeView(context).apply {
            setContent {
                FrameXTheme {
                    val mode by settingsRepository.overlayMode.collectAsState()
                    val enabledModules by settingsRepository.enabledModules.collectAsState()
                    val moduleOrder by settingsRepository.moduleOrder.collectAsState()
                    val opacity by settingsRepository.overlayOpacity.collectAsState()
                    val textSize by settingsRepository.overlayTextSize.collectAsState()
                    val overlayScale by settingsRepository.overlayScale.collectAsState()
                    val useMonospace by settingsRepository.overlayUseMonospace.collectAsState()
                    val colorIndex by settingsRepository.overlayColorIndex.collectAsState()
                    val bgColorIndex by settingsRepository.overlayBgColorIndex.collectAsState()
                    val borderColorIndex by settingsRepository.overlayBorderColorIndex.collectAsState()
                    val textColorIndex by settingsRepository.overlayTextColorIndex.collectAsState()
                    val metricsState by metricsEngine.metricsState.collectAsState()
                    
                    OverlayContent(
                        mode = mode,
                        enabledModules = enabledModules,
                        moduleOrder = moduleOrder,
                        opacity = opacity,
                        textSize = textSize,
                        overlayScale = overlayScale,
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
                            com.framex.app.utils.FrameXLog.e("Failed to update window layout on drag", e)
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
            android.graphics.PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX == -1) DEFAULT_OVERLAY_POSITION_X else savedX
            y = if (savedY == -1) DEFAULT_OVERLAY_POSITION_Y else savedY
        }

        try {
            windowManager.addView(composeView, windowParams)
            com.framex.app.utils.FrameXLog.d("Overlay successfully added to WindowManager.")
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("Failed to add overlay to WindowManager: ${e.message}", e)
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
                        com.framex.app.utils.FrameXLog.e("Failed to update window layout on orientation change", e)
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

    companion object {
        private const val DEFAULT_OVERLAY_POSITION_X = 100
        private const val DEFAULT_OVERLAY_POSITION_Y = 100
    }
}

@Composable
fun OverlayContent(
    mode: String,
    enabledModules: Set<String>,
    moduleOrder: List<String>,
    opacity: Float,
    textSize: Int = 1,
    overlayScale: Float = 1.0f,
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
    com.framex.app.ui.components.OverlayPreviewContent(
        mode = mode,
        enabledModules = enabledModules,
        moduleOrder = moduleOrder,
        opacity = opacity,
        textSize = textSize,
        overlayScale = overlayScale,
        useMonospace = useMonospace,
        colorIndex = colorIndex,
        bgColorIndex = bgColorIndex,
        borderColorIndex = borderColorIndex,
        textColorIndex = textColorIndex,
        metricsState = metricsState,
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onModeToggle() })
            }
    )
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
