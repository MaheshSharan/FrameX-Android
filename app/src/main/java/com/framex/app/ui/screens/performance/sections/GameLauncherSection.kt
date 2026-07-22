package com.framex.app.ui.screens.performance.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.framex.app.gaming.AppInfo

@Composable
fun GameLauncherSection(
    launcherGames: Set<String>,
    userApps: List<AppInfo>,
    onAddGameClicked: () -> Unit,
    onGameConfigClicked: (String) -> Unit
) {
    val context = LocalContext.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "GAME LAUNCHER",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(
                "${launcherGames.size} added",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (launcherGames.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(0.02f))
                    .border(1.dp, Color.White.copy(0.04f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No games added to launcher", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            val userAppsMap = remember(userApps) { userApps.associateBy { it.packageName } }
            
            // Replaced chunked grid with LazyRow to eliminate forced spacer weights
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(launcherGames.toList(), key = { it }) { pkg ->
                    val app = userAppsMap[pkg] ?: AppInfo(pkg, pkg.substringAfterLast('.'))
                    Card(
                        modifier = Modifier.clickable { onGameConfigClicked(pkg) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, Color.White.copy(0.04f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = app.packageName) {
                                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val drawable = context.packageManager.getApplicationIcon(app.packageName)
                                        drawable.toBitmap().asImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }
                            val currentBitmap = iconBitmap
                            if (currentBitmap != null) {
                                Image(
                                    bitmap = currentBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        app.label.take(2).uppercase(),
                                        color = Color.White.copy(0.8f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = app.label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddGameClicked,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ADD GAME", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}