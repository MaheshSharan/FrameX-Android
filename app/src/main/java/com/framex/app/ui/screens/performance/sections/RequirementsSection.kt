package com.framex.app.ui.screens.performance.sections

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.framex.app.ui.screens.performance.components.RequirementRow

@Composable
fun RequirementsSection(
    shizukuReady: Boolean,
    isShizukuAvailable: Boolean,
    hasWriteSettingsAccess: Boolean,
    hasDndAccess: Boolean,
    hasNotifListenerAccess: Boolean
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            "REQUIREMENTS & STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                com.framex.app.ui.components.WovenNetBackground(modifier = Modifier.matchParentSize())
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RequirementRow(
                    label = "Shizuku Service",
                    description = if (shizukuReady) "Connected — ADB shell is available"
                    else if (isShizukuAvailable) "Running but permission not granted"
                    else "Shizuku not running",
                    satisfied = shizukuReady,
                    onAction = if (!shizukuReady) ({
                        context.startActivity(
                            context.packageManager
                                .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                ?: Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        )
                    }) else null,
                    actionLabel = if (isShizukuAvailable) "Grant" else "Open"
                )
                HorizontalDivider(color = Color.White.copy(0.04f))
                RequirementRow(
                    label = "Write System Settings",
                    description = if (hasWriteSettingsAccess) "Authorized to modify brightness & rotation"
                    else "Required for custom brightness/rotate overrides",
                    satisfied = hasWriteSettingsAccess,
                    onAction = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                )
                HorizontalDivider(color = Color.White.copy(0.04f))
                RequirementRow(
                    label = "DND / Interruption Policy",
                    description = if (hasDndAccess) "Can suppress notifications via DND"
                    else "Required to enable Do Not Disturb during gaming",
                    satisfied = hasDndAccess,
                    onAction = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                )
                HorizontalDivider(color = Color.White.copy(0.04f))
                RequirementRow(
                    label = "Notification Listener",
                    description = if (hasNotifListenerAccess) "Active — system notifications will be cancelled"
                    else "Optional: cancels notifications that bypass DND",
                    satisfied = hasNotifListenerAccess,
                    onAction = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    actionLabel = "Enable"
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
    }
}
