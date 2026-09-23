package com.framex.app.ui.screens.permissions

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.screens.permissions.components.PermissionRow
import com.framex.app.ui.screens.permissions.components.PermissionSummaryHeader
import com.framex.app.ui.screens.permissions.components.ShizukuStatusCard

/**
 * Pure, stateless permissions and system status screen.
 */
@Composable
fun PermissionsScreen(
    uiState: PermissionsUiState,
    onNavigateBack: () -> Unit,
    onLaunchShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRequestPermission: (PermissionId) -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionItems = remember(uiState) {
        listOf(
            PermissionItem(
                id = PermissionId.OVERLAY,
                titleRes = R.string.perm_overlay_title,
                descriptionRes = R.string.perm_overlay_desc,
                icon = Icons.Default.Layers,
                isGranted = uiState.hasOverlayPermission
            ),
            PermissionItem(
                id = PermissionId.USAGE_STATS,
                titleRes = R.string.perm_usage_title,
                descriptionRes = R.string.perm_usage_desc,
                icon = Icons.Default.BarChart,
                isGranted = uiState.hasUsageStatsPermission
            ),
            PermissionItem(
                id = PermissionId.BATTERY_OPT,
                titleRes = R.string.perm_battery_title,
                descriptionRes = R.string.perm_battery_desc,
                icon = Icons.Default.BatteryFull,
                isGranted = uiState.hasBatteryOptDisabled,
                actionTextRes = R.string.perm_battery_disable
            ),
            PermissionItem(
                id = PermissionId.NOTIFICATIONS,
                titleRes = R.string.perm_notifications_title,
                descriptionRes = R.string.perm_notifications_desc,
                icon = Icons.Default.CheckCircle,
                isGranted = uiState.hasNotificationPermission
            ),
            PermissionItem(
                id = PermissionId.WRITE_SETTINGS,
                titleRes = R.string.perm_write_settings_title,
                descriptionRes = R.string.perm_write_settings_desc,
                icon = Icons.Default.Settings,
                isGranted = uiState.hasWriteSettingsPermission
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.perm_screen_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.perm_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.perm_setup_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Hero Shizuku Card
                ShizukuStatusCard(
                    isShizukuAvailable = uiState.isShizukuAvailable,
                    hasShizukuPermission = uiState.hasShizukuPermission,
                    onLaunchShizuku = onLaunchShizuku,
                    onRequestPermission = onRequestShizukuPermission
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Animated Progress Summary Header
                PermissionSummaryHeader(
                    grantedCount = uiState.grantedPermissionsCount,
                    totalCount = uiState.totalPermissionsCount
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Permissions List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    permissionItems.forEach { item ->
                        PermissionRow(
                            item = item,
                            onGrantClick = { onRequestPermission(item.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Explainer Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(0.04f))
                        .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.perm_why_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.perm_why_desc),
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(110.dp))
            }
        }

        // Bottom Return Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isAllReady) MaterialTheme.colorScheme.primary else Color.White.copy(0.1f),
                    contentColor = if (uiState.isAllReady) Color.White else Color.Gray
                ),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
            ) {
                Text(
                    text = stringResource(R.string.perm_return_dashboard),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (uiState.isAllReady) Icons.Default.Check else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
