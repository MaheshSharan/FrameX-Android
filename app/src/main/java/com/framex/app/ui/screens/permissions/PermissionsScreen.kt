package com.framex.app.ui.screens.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.permissions.AppPermission
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.ui.screens.permissions.components.PrivilegeModeSelector
import androidx.compose.ui.platform.testTag
import com.framex.app.ui.components.FrameXTopBar
import com.framex.app.ui.screens.permissions.components.PermissionRow
import com.framex.app.ui.screens.permissions.components.PermissionSummaryHeader
import com.framex.app.ui.screens.permissions.components.ShizukuStatusCard
import com.framex.app.ui.theme.FrameXSpacing

/**
 * Pure, stateless permissions and system status screen.
 */
@Composable
fun PermissionsScreen(
    uiState: PermissionsUiState,
    onNavigateBack: () -> Unit,
    onEvent: (PermissionsUiEvent) -> Unit,
    onGrantPermission: (AppPermission) -> Unit,
    onLaunchShizuku: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = FrameXSpacing.MaxContentWidth)
                .fillMaxSize()
        ) {
            // Header Bar
            FrameXTopBar(
                title = stringResource(R.string.perm_screen_title),
                onNavigateBack = onNavigateBack
            )

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

                PrivilegeModeSelector(
                    selectedMode = uiState.selectedMode,
                    isReady = uiState.isPrivilegeReady,
                    onModeSelected = { onEvent(PermissionsUiEvent.SelectMode(it)) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.selectedMode == PrivilegeMode.SHIZUKU) {
                    ShizukuStatusCard(
                        isShizukuAvailable = uiState.isPrivilegeAvailable,
                        hasShizukuPermission = uiState.isPrivilegeReady,
                        onLaunchShizuku = onLaunchShizuku,
                        onRequestPermission = { onEvent(PermissionsUiEvent.RequestPrivilegeAccess) }
                    )
                } else if (!uiState.isPrivilegeReady) {
                    OutlinedButton(
                        onClick = { onEvent(PermissionsUiEvent.RequestPrivilegeAccess) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("privilege_root_retry")
                    ) {
                        Text(stringResource(R.string.perm_mode_root_retry))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Animated Progress Summary Header
                PermissionSummaryHeader(
                    grantedCount = uiState.grantedCount,
                    totalCount = uiState.items.size
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Permissions List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.items.forEach { item ->
                        PermissionRow(
                            item = item,
                            onGrantClick = { onGrantPermission(item.permission) }
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
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isReady) MaterialTheme.colorScheme.primary else Color.White.copy(0.1f),
                    contentColor = if (uiState.isReady) Color.White else Color.Gray
                ),
                shape = CircleShape,
                modifier = Modifier
                    .widthIn(max = FrameXSpacing.MaxContentWidth)
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
                    imageVector = if (uiState.isReady) Icons.Default.Check else Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
