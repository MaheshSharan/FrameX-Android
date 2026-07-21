package com.framex.app.ui.screens.performance.dialogs

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameConfigModal(
    pkg: String,
    canWriteSettings: Boolean,
    getGameConfigBoostRam: (String) -> Boolean,
    setGameConfigBoostRam: (String, Boolean) -> Unit,
    getGameConfigDisableBrightness: (String) -> Boolean,
    setGameConfigDisableBrightness: (String, Boolean) -> Unit,
    getGameConfigDisableRotate: (String) -> Boolean,
    setGameConfigDisableRotate: (String, Boolean) -> Unit,
    getGameConfigRingtoneVol: (String) -> Int,
    setGameConfigRingtoneVol: (String, Int) -> Unit,
    onRemoveGame: (String) -> Unit,
    onLaunchGame: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    var boostRam by remember { mutableStateOf(getGameConfigBoostRam(pkg)) }
    var disableBrightness by remember { mutableStateOf(getGameConfigDisableBrightness(pkg)) }
    var disableRotate by remember { mutableStateOf(getGameConfigDisableRotate(pkg)) }
    var ringtoneVol by remember { mutableIntStateOf(getGameConfigRingtoneVol(pkg)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            val appLabel = remember(pkg) {
                try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    pkg.substringAfterLast('.')
                }
            }
            val appIcon = remember(pkg) {
                try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationIcon(ai).toBitmap().asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (appIcon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(appLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(pkg, color = Color.Gray, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !boostRam
                        boostRam = next
                        setGameConfigBoostRam(pkg, next)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto RAM Boost on Launch", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Purge non-essential caches before launching", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(checked = boostRam, onCheckedChange = {
                    boostRam = it
                    setGameConfigBoostRam(pkg, it)
                })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !disableBrightness
                        disableBrightness = next
                        setGameConfigDisableBrightness(pkg, next)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BrightnessAuto, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disable Auto-Brightness", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Lock current brightness level while gaming", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = disableBrightness,
                    onCheckedChange = {
                        disableBrightness = it
                        setGameConfigDisableBrightness(pkg, it)
                    },
                    enabled = canWriteSettings
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !disableRotate
                        disableRotate = next
                        setGameConfigDisableRotate(pkg, next)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disable Auto-Rotation", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Lock current screen orientation while gaming", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = disableRotate,
                    onCheckedChange = {
                        disableRotate = it
                        setGameConfigDisableRotate(pkg, it)
                    },
                    enabled = canWriteSettings
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Auto Ringtone Volume", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(if (ringtoneVol == -1) "Off" else "$ringtoneVol%", color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = if (ringtoneVol == -1) 0f else ringtoneVol.toFloat(),
                    onValueChange = {
                        val v = it.toInt()
                        ringtoneVol = v
                        setGameConfigRingtoneVol(pkg, v)
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onRemoveGame(pkg)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Remove")
                }

                Button(
                    onClick = {
                        onDismiss()
                        onLaunchGame(pkg)
                    },
                    modifier = Modifier.weight(1.5f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Launch Game", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
