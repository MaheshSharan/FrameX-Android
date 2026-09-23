package com.framex.app.ui.screens.dashboard.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.components.ActionGridCard
import com.framex.app.ui.components.ActionRowTile

@Composable
fun QuickAccessGrid(
    isShizukuReady: Boolean,
    onNavigateToOverlayCustomization: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToThermalDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emeraldColor = Color(0xFF2FBF9F)
    val errorColor = MaterialTheme.colorScheme.error

    // Pulsing glow animation scoped exclusively to graphicsLayer to prevent recomposition spill
    val infiniteTransition = rememberInfiniteTransition(label = "shizukuGlowPulse")
    val alphaGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shizukuGlowAlpha"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_quick_access),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        // Row 1: Metrics & Theme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionGridCard(
                title = stringResource(R.string.dashboard_metrics_title),
                subtitle = stringResource(R.string.dashboard_metrics_desc),
                iconContainerColor = Color(0xFF6C6CE0).copy(alpha = 0.14f),
                iconContentColor = Color(0xFF9494EE),
                onClick = onNavigateToOverlayCustomization,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = null
                    )
                }
            )

            ActionGridCard(
                title = stringResource(R.string.dashboard_theme_title),
                subtitle = stringResource(R.string.dashboard_theme_desc),
                iconContainerColor = Color(0xFFE8A23C).copy(alpha = 0.14f),
                iconContentColor = Color(0xFFF0BB6E),
                onClick = onNavigateToAppearance,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null
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
            ActionGridCard(
                title = stringResource(R.string.dashboard_performance_title),
                subtitle = stringResource(R.string.dashboard_performance_desc),
                iconContainerColor = Color(0xFF2FBF9F).copy(alpha = 0.14f),
                iconContentColor = Color(0xFF4FDCB8),
                onClick = onNavigateToPerformance,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null
                    )
                }
            )

            ActionGridCard(
                title = stringResource(R.string.dashboard_shizuku_title),
                subtitle = if (isShizukuReady) {
                    stringResource(R.string.dashboard_connected)
                } else {
                    stringResource(R.string.dashboard_disconnected)
                },
                iconContainerColor = if (isShizukuReady) {
                    Color(0xFF3D9BE0).copy(alpha = 0.14f)
                } else {
                    errorColor.copy(alpha = 0.14f)
                },
                iconContentColor = if (isShizukuReady) {
                    Color(0xFF6EB8EE)
                } else {
                    errorColor
                },
                onClick = onNavigateToPermissions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                statusTag = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
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
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isShizukuReady) emeraldColor else errorColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                icon = {
                    Text(
                        text = "ADB",
                        color = if (isShizukuReady) Color(0xFF6EB8EE) else errorColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 3: Full Width Thermal Diagnostics Tile
        ActionRowTile(
            title = stringResource(R.string.dashboard_thermal_title),
            subtitle = stringResource(R.string.dashboard_thermal_desc),
            iconContainerColor = Color(0xFFE8324A).copy(alpha = 0.14f),
            iconContentColor = Color(0xFFF0576E),
            onClick = onNavigateToThermalDiagnostics,
            showChevron = true,
            icon = {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null
                )
            }
        )
    }
}
