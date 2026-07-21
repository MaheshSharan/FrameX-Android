package com.framex.app.ui.screens.performance.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.framex.app.gaming.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameModal(
    userApps: List<AppInfo>,
    launcherGames: Set<String>,
    onDismiss: () -> Unit,
    onToggleLauncherGame: (String) -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("Add Games to Launcher", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(userApps) { app ->
                    val isAdded = launcherGames.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleLauncherGame(app.packageName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconDrawable = remember(app.packageName) {
                            try {
                                context.packageManager.getApplicationIcon(app.packageName)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (iconDrawable != null) {
                            AndroidView(
                                factory = { ctx ->
                                    android.widget.ImageView(ctx).apply {
                                        setImageDrawable(iconDrawable)
                                    }
                                },
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(app.label.take(2).uppercase(), color = Color.White, fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(app.label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Checkbox(
                            checked = isAdded,
                            onCheckedChange = { onToggleLauncherGame(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}
