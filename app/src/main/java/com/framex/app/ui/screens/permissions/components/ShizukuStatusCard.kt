package com.framex.app.ui.screens.permissions.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.R
import com.framex.app.ui.components.WovenNetBackground

@Composable
fun ShizukuStatusCard(
    isShizukuAvailable: Boolean,
    hasShizukuPermission: Boolean,
    onLaunchShizuku: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emeraldColor = Color(0xFF2FBF9F)
    val errorColor = MaterialTheme.colorScheme.error

    val isRunningAndGranted = isShizukuAvailable && hasShizukuPermission
    val statusBadgeColor by animateColorAsState(
        targetValue = if (isRunningAndGranted) emeraldColor else errorColor,
        label = "shizukuBadgeColor"
    )

    val statusText = when {
        isRunningAndGranted -> stringResource(R.string.perm_shizuku_running_granted)
        isShizukuAvailable -> stringResource(R.string.perm_shizuku_perm_required)
        else -> stringResource(R.string.perm_shizuku_not_running)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            WovenNetBackground(modifier = Modifier.matchParentSize())

            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3D9BE0).copy(alpha = 0.14f))
                            .border(1.dp, Color(0xFF3D9BE0).copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ADB",
                            color = Color(0xFF6EB8EE),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.perm_shizuku_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(statusBadgeColor.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusBadgeColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                color = statusBadgeColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.perm_shizuku_desc),
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLaunchShizuku,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.perm_launch_app),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = CircleShape,
                        enabled = !hasShizukuPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasShizukuPermission) emeraldColor else MaterialTheme.colorScheme.primary,
                            disabledContainerColor = emeraldColor.copy(alpha = 0.5f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (hasShizukuPermission) {
                                stringResource(R.string.perm_authorized)
                            } else {
                                stringResource(R.string.perm_grant_access)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
