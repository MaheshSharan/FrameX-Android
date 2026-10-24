package com.framex.app.ui.screens.dashboard.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.screens.dashboard.DashboardUiState
import com.framex.app.ui.screens.dashboard.OverlayActionState
import com.framex.app.ui.theme.FrameXMotion
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.StatusGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory bitmap cache for the dashboard hero character artwork.
 * Prevents main thread bitmap decoding and allocation churn during Compose composition.
 */
private object DashboardHeroCache {
    @Volatile
    private var cachedBitmap: ImageBitmap? = null

    fun get(): ImageBitmap? = cachedBitmap

    suspend fun load(context: Context, resId: Int): ImageBitmap? {
        cachedBitmap?.let { return it }
        return withContext(Dispatchers.IO) {
            cachedBitmap ?: synchronized(this) {
                cachedBitmap ?: runCatching {
                    val options = BitmapFactory.Options().apply {
                        inScaled = false
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeResource(context.resources, resId, options)
                        ?.asImageBitmap()
                        ?.also { cachedBitmap = it }
                }.getOrNull()
            }
        }
    }
}

/**
 * Redesigned Hero Status Card featuring the custom character artwork,
 * dark gradient scrim for readability, glowing accent border, status pill,
 * and prominent action CTA.
 */
@Composable
fun HeroStatusCard(
    uiState: DashboardUiState,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reduceMotion = FrameXMotion.rememberReduceMotion()
    val accentColor = MaterialTheme.colorScheme.primary
    val emeraldColor = StatusGreen
    val cardBackground = Color(0xFF0C0A0D)

    val heroBitmap by produceState<ImageBitmap?>(
        initialValue = remember { DashboardHeroCache.get() }
    ) {
        if (value == null) {
            value = DashboardHeroCache.load(context, R.drawable.dash_hero)
        }
    }

    val dotAlpha = if (reduceMotion || !uiState.isOverlayRunning) {
        1.0f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "heroDotPulse")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FrameXMotion.StandardEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "heroDotAlpha"
        )
        animated
    }

    val cardShape = FrameXShapes.Card

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp)
            .clip(cardShape)
            .background(cardBackground)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.50f),
                        accentColor.copy(alpha = 0.15f),
                        Color(0xFF1E2028)
                    )
                ),
                shape = cardShape
            )
    ) {
        // 1. Character artwork aligned to the right side
        heroBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.85f)
            )
        }

        // 2. Horizontal gradient scrim: Dark on the left for contrast, transparent on right
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to cardBackground,
                        0.40f to cardBackground.copy(alpha = 0.95f),
                        0.65f to cardBackground.copy(alpha = 0.50f),
                        1.0f to Color.Transparent
                    )
                )
        )

        // 3. Subtle bottom vertical gradient scrim
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.75f to cardBackground.copy(alpha = 0.40f),
                        1.0f to cardBackground.copy(alpha = 0.85f)
                    )
                )
        )

        // 4. Foreground Content Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Overlay Status Row + Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dashboard_overlay_status),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9E9E9E),
                    letterSpacing = 0.8.sp,
                    fontSize = 10.sp
                )

                Box(
                    modifier = Modifier
                        .clip(FrameXShapes.Pill)
                        .background(Color(0xFF161820).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF282B36), FrameXShapes.Pill)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .graphicsLayer {
                                    if (uiState.isOverlayRunning) {
                                        alpha = dotAlpha
                                    }
                                }
                                .background(if (uiState.isOverlayRunning) emeraldColor else Color(0xFF757575))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (uiState.isOverlayRunning) {
                                stringResource(R.string.dashboard_badge_active)
                            } else {
                                stringResource(R.string.dashboard_badge_inactive)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isOverlayRunning) emeraldColor else Color(0xFF9E9E9E),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Headline Title
            AnimatedContent(
                targetState = uiState.isOverlayRunning,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                    }
                },
                label = "statusTitleTransition"
            ) { isRunning ->
                Text(
                    text = if (isRunning) {
                        stringResource(R.string.dashboard_status_active)
                    } else {
                        stringResource(R.string.dashboard_status_ready)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle Description (constrained width preserves helmet visibility on right)
            Text(
                text = if (uiState.isOverlayRunning) {
                    stringResource(R.string.dashboard_hero_desc_active)
                } else {
                    stringResource(R.string.dashboard_hero_desc_ready)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9E9E9E),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(0.68f)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Action CTA Button
            AnimatedContent(
                targetState = when {
                    uiState.isOverlayRunning -> OverlayActionState.RUNNING
                    uiState.allPermissionsReady -> OverlayActionState.READY
                    else -> OverlayActionState.MISSING_PERMISSIONS
                },
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(FrameXMotion.DurationMedium)) togetherWith fadeOut(tween(FrameXMotion.DurationFast))
                    }
                },
                label = "heroActionTransition"
            ) { actionState ->
                when (actionState) {
                    OverlayActionState.RUNNING -> {
                        Button(
                            onClick = onStopOverlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = FrameXShapes.Pill,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.dashboard_stop_overlay),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    OverlayActionState.READY -> {
                        Button(
                            onClick = onStartOverlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = FrameXShapes.Pill,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(44.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_play_premium),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.dashboard_start_overlay),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    OverlayActionState.MISSING_PERMISSIONS -> {
                        Button(
                            onClick = onNavigateToPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = FrameXShapes.Pill,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.dashboard_complete_setup),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
