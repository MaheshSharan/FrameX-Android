package com.framex.app.ui.screens.performance.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SwipeToActivate(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: SliderEffects.SliderPalette,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    showResult: Boolean = false,
    busyText: String = "OPTIMIZING…",
    resultText: String = "DONE",
    onActivated: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trackWidthDp = 300.dp
    val thumbSizeDp = 52.dp
    val maxDrag = with(density) { (trackWidthDp - thumbSizeDp - 8.dp).toPx() }

    var dragOffset by remember { mutableStateOf(0f) }
    var isCompleted by remember { mutableStateOf(false) }
    var hasTriggered by remember { mutableStateOf(false) }

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

    LaunchedEffect(isCompleted) {
        if (isCompleted && !hasTriggered) {
            hasTriggered = true
            onActivated()
        }
    }

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

    // Smooth particle time ticker for active track twinkle
    val infiniteTransition = rememberInfiniteTransition(label = "particleTimeline")
    val tickerTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleClock"
    )

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

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val progress = remember(animatedOffset, maxDrag) {
        SliderEffects.calculateProgress(animatedOffset, maxDrag)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Track Box
        Box(
            modifier = Modifier
                .width(trackWidthDp)
                .height(thumbSizeDp + 8.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A).copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .semantics {
                    contentDescription = if (isBusy) busyText else if (isCompleted || showResult) resultText else text
                    customActions = listOf(
                        CustomAccessibilityAction(label = text) {
                            if (!isBusy && !isCompleted && !showResult) {
                                isCompleted = true
                                true
                            } else {
                                false
                            }
                        }
                    )
                }
        ) {
            val trackHeightPx = with(density) { (thumbSizeDp + 8.dp).toPx() }
            val trackWidthPx = with(density) { trackWidthDp.toPx() }
            val thumbPaddingPx = with(density) { 4.dp.toPx() }
            val knobCenterPx = animatedOffset + thumbPaddingPx + (with(density) { thumbSizeDp.toPx() } / 2f)
            val trailRightEdgePx = SliderEffects.calculateTrailRightEdge(progress, knobCenterPx, trackWidthPx)

            // Dynamic Gradient & Twinkle Particle Canvas (strictly hidden in idle state: only active when dragging/activated)
            if (animatedOffset > 1f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

                    clipRect(right = trailRightEdgePx) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = palette.gradientColors,
                                startX = 0f,
                                endX = trackWidthPx
                            ),
                            size = Size(trackWidthPx, trackHeightPx),
                            cornerRadius = cornerRadius,
                            alpha = 0.25f + (0.25f * progress)
                        )

                        // Ambient particles along the illuminated track
                        val elapsedSec = (tickerTime.toDouble())
                        val wrapWidth = trackWidthPx + 20f

                        for (particle in SliderEffects.DEFAULT_PARTICLES) {
                            val originX = particle.horizontalRatio * trackWidthPx
                            val currentX = SliderEffects.computeParticleX(
                                originX = originX,
                                elapsedSec = elapsedSec,
                                speed = particle.speedPxPerSec.toDouble(),
                                wrapWidth = wrapWidth
                            )

                            if (currentX <= trailRightEdgePx) {
                                val currentY = (trackHeightPx * particle.verticalRatio)
                                val phaseRad = elapsedSec * particle.twinkleSpeed + particle.phase
                                val alpha = SliderEffects.computeTwinkleAlpha(phaseRad)

                                drawCircle(
                                    color = Color.White.copy(alpha = alpha),
                                    radius = particle.radiusDp * density.density,
                                    center = Offset(currentX, currentY)
                                )
                            }
                        }
                    }
                }
            }

            // Track Label Text
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val textAlpha = ((maxDrag - animatedOffset) / maxDrag).coerceIn(0f, 1f)
                Text(
                    text = when {
                        isBusy -> busyText
                        showResult -> resultText
                        isCompleted -> "DONE"
                        else -> text
                    },
                    color = Color.White.copy(alpha = (0.75f * textAlpha).coerceIn(0f, 1f)),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Elevated Tactile Slider Knob
            Box(
                modifier = Modifier
                    .offset(x = with(density) { (animatedOffset + 4.dp.toPx()).toDp() }, y = 4.dp)
                    .size(thumbSizeDp)
                    .shadow(
                        elevation = if (dragOffset > 0f) 8.dp else 4.dp,
                        shape = CircleShape,
                        spotColor = palette.endColor.copy(alpha = 0.4f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.thumbColor.copy(alpha = 0.95f),
                                palette.midColor
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .graphicsLayer {
                        if (idleBounceActive) {
                            scaleX = 1f + idleBounce * 0.012f
                            scaleY = 1f + idleBounce * 0.012f
                        } else if (dragOffset > 0f) {
                            scaleX = 1.04f
                            scaleY = 1.04f
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
