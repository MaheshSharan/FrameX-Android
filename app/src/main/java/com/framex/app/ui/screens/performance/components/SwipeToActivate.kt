package com.framex.app.ui.screens.performance.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SwipeToActivate(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    isBusy: Boolean,
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
                val trailWidth = with(density) {
                    (animatedOffset + thumbSizeDp.toPx() + 8.dp.toPx()).toDp()
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(trailWidth)
                        .background(accentColor.copy(0.12f), CircleShape)
                )

                val textAlpha = ((maxDrag - animatedOffset) / maxDrag).coerceIn(0f, 1f)
                Text(
                    text = when {
                        isBusy -> busyText
                        showResult -> resultText
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

                Box(
                    modifier = Modifier
                        .offset(x = with(density) { (animatedOffset + 4.dp.toPx()).toDp() })
                        .size(thumbSizeDp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .graphicsLayer {
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
