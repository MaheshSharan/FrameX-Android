package com.framex.app.ui.screens.performance.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.framex.app.gaming.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GameConfigModal(
    pkg: String,
    userApps: List<AppInfo>,
    getGameConfigBoostRam: (String) -> Boolean,
    setGameConfigBoostRam: (String, Boolean) -> Unit,
    onBoostClicked: (String) -> Unit,
    onDismiss: () -> Unit,
    isVivo: Boolean = false,
    maxRefreshRate: Int = 120,
    getGameConfigMemc: (String) -> Boolean = { false },
    onToggleMemc: ((String, Boolean, (Boolean) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val app = remember(pkg, userApps) {
        userApps.firstOrNull { it.packageName == pkg } ?: AppInfo(pkg, pkg.substringAfterLast('.'))
    }

    var boostRam by remember(pkg) { mutableStateOf(getGameConfigBoostRam(pkg)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color.White.copy(0.06f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
                        value = withContext(Dispatchers.IO) {
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
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "GAME CONFIGURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Boost RAM Feature
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Boost RAM on Launch",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Force-stop background activities",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = boostRam,
                        onCheckedChange = {
                            boostRam = it
                            setGameConfigBoostRam(pkg, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (isVivo) {
                    var memcEnabled by remember(pkg) { mutableStateOf(getGameConfigMemc(pkg)) }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "VIVO HARDWARE SUITE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Cyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // MEMC Frame Generation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "MEMC ${maxRefreshRate} FPS Target",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                "Hardware IC 60->${maxRefreshRate} FPS interpolation",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = memcEnabled,
                            onCheckedChange = { checked ->
                                memcEnabled = checked
                                onToggleMemc?.invoke(pkg, checked) { success ->
                                    if (!success) {
                                        memcEnabled = !checked
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.Cyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onBoostClicked(pkg)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("BOOST", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
