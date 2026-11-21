package com.framex.app.ui.screens.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.theme.Amber
import com.framex.app.ui.theme.FrameXMotion
import com.framex.app.ui.theme.MutedBlue
import com.framex.app.ui.theme.MutedGreen
import com.framex.app.ui.theme.StatusAmber
import com.framex.app.ui.theme.StatusEmerald
import com.framex.app.ui.theme.StatusGreen

/**
 * 2x2 Quick Access Grid for primary configuration destinations:
 * Metrics, Theme, Performance, and Shizuku.
 * Section icon and header text are kept pure white per design specification.
 */
@Composable
fun QuickAccessGrid(
    isShizukuReady: Boolean,
    onNavigateToOverlayCustomization: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = FrameXMotion.rememberReduceMotion()
    val emeraldColor = StatusGreen
    val errorColor = MaterialTheme.colorScheme.error

    // Pulsing glow animation scoped exclusively to graphicsLayer; static when reduce motion is enabled
    val alphaGlow = if (reduceMotion || !isShizukuReady) {
        1.0f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "shizukuGlowPulse")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shizukuGlowAlpha"
        )
        animated
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header: Pure white icon and typography
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dashboard_quick_access),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
        }

        // Row 1: Metrics & Theme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                title = stringResource(R.string.dashboard_metrics_title),
                subtitle = stringResource(R.string.dashboard_metrics_desc),
                iconContainerColor = Color(0xFF6366F1).copy(alpha = 0.16f),
                iconContentColor = Color(0xFF818CF8),
                onClick = onNavigateToOverlayCustomization,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            QuickAccessCard(
                title = stringResource(R.string.dashboard_theme_title),
                subtitle = stringResource(R.string.dashboard_theme_desc),
                iconContainerColor = StatusAmber.copy(alpha = 0.16f),
                iconContentColor = Amber,
                onClick = onNavigateToAppearance,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 2: Performance & Shizuku
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                title = stringResource(R.string.dashboard_performance_title),
                subtitle = stringResource(R.string.dashboard_performance_desc),
                iconContainerColor = StatusEmerald.copy(alpha = 0.16f),
                iconContentColor = MutedGreen,
                onClick = onNavigateToPerformance,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            QuickAccessCard(
                title = stringResource(R.string.dashboard_shizuku_title),
                subtitle = if (isShizukuReady) {
                    stringResource(R.string.dashboard_connected)
                } else {
                    stringResource(R.string.dashboard_disconnected)
                },
                subtitleContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .graphicsLayer {
                                    if (isShizukuReady) {
                                        alpha = alphaGlow
                                    }
                                }
                                .background(if (isShizukuReady) emeraldColor else errorColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isShizukuReady) {
                                stringResource(R.string.dashboard_connected)
                            } else {
                                stringResource(R.string.dashboard_disconnected)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isShizukuReady) emeraldColor else errorColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                iconContainerColor = if (isShizukuReady) {
                    Color(0xFF3B82F6).copy(alpha = 0.16f)
                } else {
                    errorColor.copy(alpha = 0.16f)
                },
                iconContentColor = if (isShizukuReady) {
                    MutedBlue
                } else {
                    errorColor
                },
                onClick = onNavigateToPermissions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                icon = {
                    Text(
                        text = stringResource(R.string.dashboard_adb),
                        color = if (isShizukuReady) MutedBlue else errorColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp
                    )
                }
            )
        }
    }
}

private val GridCardShape = RoundedCornerShape(14.dp)
private val GridIconShape = RoundedCornerShape(10.dp)

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitleContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(GridCardShape)
            .background(Color(0xFF0F1015))
            .border(1.dp, Color(0xFF1E2028), GridCardShape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(GridIconShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides iconContentColor
                ) {
                    icon()
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (subtitleContent != null) {
                    subtitleContent()
                } else {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF616161),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
