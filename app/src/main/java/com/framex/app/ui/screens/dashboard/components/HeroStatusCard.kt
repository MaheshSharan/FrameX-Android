package com.framex.app.ui.screens.dashboard.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.components.PrimaryButton
import com.framex.app.ui.components.SectionCard
import com.framex.app.ui.screens.dashboard.DashboardUiState
import com.framex.app.ui.theme.FrameXAccessibility
import com.framex.app.ui.theme.FrameXBorders
import com.framex.app.ui.theme.FrameXMotion
import com.framex.app.ui.theme.FrameXShapes
import com.framex.app.ui.theme.FrameXSpacing

@Composable
fun HeroStatusCard(
    uiState: DashboardUiState,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emeraldColor = Color(0xFF22C55E)
    val amberColor = Color(0xFFFBBF24)

    val activeColor by animateColorAsState(
        targetValue = if (uiState.isOverlayRunning) emeraldColor else Color.Gray,
        animationSpec = tween(FrameXMotion.DurationSlow),
        label = "statusBadgeColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "heroGlowDotPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FrameXMotion.StandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroGlowDotAlpha"
    )

    SectionCard(modifier = modifier) {
        // Status Top Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = stringResource(R.string.dashboard_overlay_status),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Gray,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedContent(
                    targetState = uiState.isOverlayRunning,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
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
            }

            // Animated Status Chip
            Box(
                modifier = Modifier
                    .clip(FrameXShapes.Pill)
                    .background(activeColor.copy(alpha = 0.12f))
                    .border(FrameXBorders.ActiveBorderWidth, activeColor.copy(alpha = 0.25f), FrameXShapes.Pill)
                    .padding(horizontal = FrameXSpacing.Medium, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(FrameXSpacing.Small)
                            .graphicsLayer {
                                if (uiState.isOverlayRunning) {
                                    alpha = dotAlpha
                                }
                            }
                            .background(activeColor, FrameXShapes.Pill)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isOverlayRunning) {
                            stringResource(R.string.dashboard_badge_active)
                        } else {
                            stringResource(R.string.dashboard_badge_inactive)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = activeColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(FrameXSpacing.Large))

        // Sparkline Graph Card
        LiveFpsSparklineCard(
            fpsHistory = uiState.fpsHistory,
            stats = uiState.fpsStats
        )

        Spacer(modifier = Modifier.height(FrameXSpacing.XLarge))

        // Action CTA Row with animated state transitions
        AnimatedContent(
            targetState = when {
                uiState.isOverlayRunning -> OverlayActionState.RUNNING
                uiState.allPermissionsReady -> OverlayActionState.READY
                else -> OverlayActionState.MISSING_PERMISSIONS
            },
            transitionSpec = {
                fadeIn(tween(FrameXMotion.DurationMedium)) + expandVertically() togetherWith fadeOut(tween(FrameXMotion.DurationFast)) + shrinkVertically()
            },
            label = "overlayActionTransition"
        ) { actionState ->
            when (actionState) {
                OverlayActionState.RUNNING -> {
                    val primaryAccent = MaterialTheme.colorScheme.primary
                    Button(
                        onClick = onStopOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryAccent.copy(alpha = 0.15f),
                            contentColor = primaryAccent
                        ),
                        shape = FrameXShapes.Pill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = FrameXAccessibility.StandardRowHeight)
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_stop_overlay),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                OverlayActionState.READY -> {
                    PrimaryButton(
                        text = stringResource(R.string.dashboard_start_overlay),
                        onClick = onStartOverlay,
                        icon = {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    )
                }
                OverlayActionState.MISSING_PERMISSIONS -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_missing_prefix) + uiState.missingPermissions.joinToString(" · "),
                            color = amberColor,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onNavigateToPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = amberColor.copy(alpha = 0.12f),
                                contentColor = amberColor
                            ),
                            shape = FrameXShapes.Pill,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = FrameXAccessibility.StandardRowHeight)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(FrameXSpacing.Small))
                            Text(
                                text = stringResource(R.string.dashboard_complete_setup),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class OverlayActionState {
    RUNNING,
    READY,
    MISSING_PERMISSIONS
}
