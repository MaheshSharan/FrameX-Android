package com.framex.app.ui.screens.performance.dialogs

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
    canWriteSettings: Boolean,
    getGameConfigBoostRam: (String) -> Boolean,
    setGameConfigBoostRam: (String, Boolean) -> Unit,
    getGameConfigDisableBrightness: (String) -> Boolean,
    setGameConfigDisableBrightness: (String, Boolean) -> Unit,
    getGameConfigDisableRotate: (String) -> Boolean,
    setGameConfigDisableRotate: (String, Boolean) -> Unit,
    getGameConfigRingtoneVol: (String) -> Int,
    setGameConfigRingtoneVol: (String, Int) -> Unit,
    onBoostClicked: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = remember(pkg, userApps) {
        userApps.firstOrNull { it.packageName == pkg } ?: AppInfo(pkg, pkg.substringAfterLast('.'))
    }

    var boostRam by remember(pkg) { mutableStateOf(getGameConfigBoostRam(pkg)) }
    var disableBrightness by remember(pkg) { mutableStateOf(getGameConfigDisableBrightness(pkg)) }
    var disableRotate by remember(pkg) { mutableStateOf(getGameConfigDisableRotate(pkg)) }
    var ringtoneVol by remember(pkg) { mutableFloatStateOf(getGameConfigRingtoneVol(pkg).toFloat()) }

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
                            contentDescription = null,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                app.label.take(2).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            app.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            app.packageName,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "The following configuration will change automatically when the game starts.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "ENGINE OPTIMIZATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Boost RAM",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BrightnessMedium,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Disable auto brightness",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Lock brightness at current level",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = disableBrightness,
                        onCheckedChange = {
                            disableBrightness = it
                            setGameConfigDisableBrightness(pkg, it)
                            if (it && !canWriteSettings) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ScreenRotation,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Disable auto rotate",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Lock display in landscape mode",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = disableRotate,
                        onCheckedChange = {
                            disableRotate = it
                            setGameConfigDisableRotate(pkg, it)
                            if (it && !canWriteSettings) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Change ringtone volume",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = ringtoneVol,
                    onValueChange = {
                        ringtoneVol = it
                        setGameConfigRingtoneVol(pkg, it.toInt())
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

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
