package com.framex.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framex.app.ui.components.PrimaryButton

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.framex.app.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val overlayMode = settingsRepository.overlayMode
    val enabledModules = settingsRepository.enabledModules
    val overlayOpacity = settingsRepository.overlayOpacity
    val overlayTextSize = settingsRepository.overlayTextSize
    val overlayUseMonospace = settingsRepository.overlayUseMonospace
    val overlayColorIndex = settingsRepository.overlayColorIndex
    val overlayBgColorIndex = settingsRepository.overlayBgColorIndex
    val overlayBorderColorIndex = settingsRepository.overlayBorderColorIndex
    val overlayTextColorIndex = settingsRepository.overlayTextColorIndex
    
    fun saveSettings(
        opacity: Float, textSize: Int, useMonospace: Boolean, colorIndex: Int,
        bgColorIndex: Int, borderColorIndex: Int, textColorIndex: Int
    ) {
        settingsRepository.setOverlayOpacity(opacity)
        settingsRepository.setOverlayTextSize(textSize)
        settingsRepository.setOverlayUseMonospace(useMonospace)
        settingsRepository.setOverlayColorIndex(colorIndex)
        settingsRepository.setOverlayBgColorIndex(bgColorIndex)
        settingsRepository.setOverlayBorderColorIndex(borderColorIndex)
        settingsRepository.setOverlayTextColorIndex(textColorIndex)
    }
}

@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val savedOpacity by viewModel.overlayOpacity.collectAsState()
    val savedTextSize by viewModel.overlayTextSize.collectAsState()
    val savedUseMonospace by viewModel.overlayUseMonospace.collectAsState()
    val savedColorIndex by viewModel.overlayColorIndex.collectAsState()
    val savedBgColorIndex by viewModel.overlayBgColorIndex.collectAsState()
    val savedBorderColorIndex by viewModel.overlayBorderColorIndex.collectAsState()
    val savedTextColorIndex by viewModel.overlayTextColorIndex.collectAsState()
    val mode by viewModel.overlayMode.collectAsState()
    val enabledModules by viewModel.enabledModules.collectAsState()
    
    val context = LocalContext.current

    var opacity by remember(savedOpacity) { mutableStateOf(savedOpacity) }
    var selectedTextSize by remember(savedTextSize) { mutableStateOf(savedTextSize) }
    var useMonospace by remember(savedUseMonospace) { mutableStateOf(savedUseMonospace) }
    var selectedColorIndex by remember(savedColorIndex) { mutableStateOf(savedColorIndex) }
    var selectedBgColorIndex by remember(savedBgColorIndex) { mutableStateOf(savedBgColorIndex) }
    var selectedBorderColorIndex by remember(savedBorderColorIndex) { mutableStateOf(savedBorderColorIndex) }
    var selectedTextColorIndex by remember(savedTextColorIndex) { mutableStateOf(savedTextColorIndex) }
    
    val hasChanges = opacity != savedOpacity || selectedTextSize != savedTextSize ||
        useMonospace != savedUseMonospace || selectedColorIndex != savedColorIndex ||
        selectedBgColorIndex != savedBgColorIndex || selectedBorderColorIndex != savedBorderColorIndex ||
        selectedTextColorIndex != savedTextColorIndex
    
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF60A5FA),
        Color(0xFF34D399),
        Color(0xFF2DD4BF),
        Color(0xFFA78BFA),
        Color(0xFFFBBF24)
    )

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(imageVector = Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Appearance", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp)) // For balance
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // Live Preview
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("Respects selected Metrics config", style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(0.6f))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.DarkGray) // Mock background image
                ) {
                    // Dynamic Overlay inside
                    val availableModules = listOf(
                        Triple("fps", "FPS", "120") to Icons.Default.Speed,
                        Triple("cpu", "CPU", "34%") to Icons.Default.Memory,
                        Triple("ram", "RAM", "4.2 GB") to Icons.Default.DeveloperBoard,
                        Triple("temp", "TEMP", "38°C") to Icons.Default.DeviceThermostat,
                        Triple("net", "NET", "1.2 MB") to Icons.Default.NetworkCheck
                    )
                    
                    val activeList = availableModules.filter { enabledModules.contains(it.first.first) }
                    val accentColor = colors[selectedColorIndex]
                    val fontFamily = if (useMonospace) androidx.compose.ui.text.font.FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily
                    val textScale = when(selectedTextSize) { 0 -> 0.8f; 2 -> 1.2f; else -> 1.0f }

                    // Mirror OverlayContent logic so what you see in preview == what you get on overlay
                    val previewBgColors = listOf(Color.Black, Color(0xFF0D1117), Color(0xFF1C1C1E), Color.Transparent)
                    val previewBgBase = previewBgColors.getOrElse(selectedBgColorIndex) { Color.Black }
                    val previewBg = if (previewBgBase == Color.Transparent) Color.Transparent else previewBgBase.copy(alpha = opacity)
                    val previewBorderColor = when (selectedBorderColorIndex) {
                        1 -> Color.Transparent
                        2 -> Color.White.copy(alpha = 0.2f)
                        3 -> Color.White.copy(alpha = 0.05f)
                        else -> accentColor
                    }
                    val previewBorderWidth = if (selectedBorderColorIndex == 1) 0.dp else 1.dp
                    val previewTextValueColor = when (selectedTextColorIndex) {
                        1 -> accentColor
                        2 -> Color(0xFFCBD5E1)
                        else -> Color.White
                    }
                    
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(previewBg)
                                .border(previewBorderWidth, previewBorderColor, RoundedCornerShape(8.dp))
                                .padding(if (mode == "Minimal") (4 * textScale).dp else (8 * textScale).dp)
                        ) {
                            if (mode == "Expanded") {
                                Column(verticalArrangement = Arrangement.spacedBy((8 * textScale).dp)) {
                                    activeList.forEach { pair ->
                                        val info = pair.first
                                        val icon = pair.second
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size((16 * textScale).dp))
                                            Spacer(modifier = Modifier.width((8 * textScale).dp))
                                            Text(info.second, color = Color.Gray, fontSize = (10 * textScale).sp, fontFamily = fontFamily, modifier = Modifier.weight(1f))
                                            Text(info.third, color = previewTextValueColor, fontSize = (12 * textScale).sp, fontFamily = fontFamily, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy((12 * textScale).dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = (4 * textScale).dp)
                                ) {
                                    activeList.forEachIndexed { index, pair ->
                                        val info = pair.first
                                        if (mode == "Minimal") {
                                            Text(info.third, color = previewTextValueColor, fontFamily = fontFamily, fontSize = (14 * textScale).sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                        } else {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(info.second, color = Color.Gray, fontSize = (10 * textScale).sp, fontFamily = fontFamily, fontWeight = FontWeight.Bold)
                                                Text(info.third, color = previewTextValueColor, fontFamily = fontFamily, fontSize = (16 * textScale).sp, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }
                                        if (index < activeList.size - 1) {
                                            Box(modifier = Modifier.width(1.dp).height(if (mode == "Minimal") (12 * textScale).dp else (24 * textScale).dp).background(Color.DarkGray))
                                        }
                                    }
                                    if (activeList.isEmpty()) {
                                        Text("No active metrics", color = Color.Gray, fontSize = (12 * textScale).sp, fontFamily = fontFamily)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Visibility
                Text("VISIBILITY", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Overlay Opacity", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("${(opacity * 100).toInt()}%", color = colors[selectedColorIndex], fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = colors[selectedColorIndex],
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Container — background color + border style
                Text("CONTAINER", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Background Color
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Background", color = Color.White, fontWeight = FontWeight.Medium)
                            Text(listOf("Black", "Navy", "Charcoal", "Transparent")[selectedBgColorIndex], color = colors[selectedColorIndex], fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val bgColorSamples = listOf(Color.Black, Color(0xFF0D1117), Color(0xFF1C1C1E), Color.Transparent)
                        val bgColorLabels = listOf("Black", "Navy", "Charcoal", "Clear")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            bgColorSamples.forEachIndexed { index, col ->
                                val isSelected = selectedBgColorIndex == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (col == Color.Transparent) Color.White.copy(0.06f) else col)
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors[selectedColorIndex] else Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                                        .clickable { selectedBgColorIndex = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (col == Color.Transparent) {
                                        Text("T", color = if (isSelected) colors[selectedColorIndex] else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    } else if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = colors[selectedColorIndex], modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            bgColorLabels.forEach { label ->
                                Text(label, color = Color.Gray, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }

                        Divider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 16.dp))

                        // Border Style
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Border", color = Color.White, fontWeight = FontWeight.Medium)
                            Text(listOf("Accent", "None", "Subtle", "Ghost")[selectedBorderColorIndex], color = colors[selectedColorIndex], fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black).padding(4.dp)
                        ) {
                            listOf("Accent", "None", "Subtle", "Ghost").forEachIndexed { index, label ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedBorderColorIndex == index) MaterialTheme.colorScheme.surface else Color.Transparent)
                                        .clickable { selectedBorderColorIndex = index }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (selectedBorderColorIndex == index) Color.White else Color.Gray, fontWeight = if (selectedBorderColorIndex == index) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Typography
                Text("TYPOGRAPHY", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Text Size", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .padding(4.dp)
                            ) {
                                listOf("Small", "Medium", "Large").forEachIndexed { index, label ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selectedTextSize == index) MaterialTheme.colorScheme.surface else Color.Transparent)
                                            .clickable { selectedTextSize = index }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (selectedTextSize == index) Color.White else Color.Gray,
                                            fontWeight = if (selectedTextSize == index) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                        Divider(color = Color.White.copy(0.05f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Monospace Metrics", color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Use JetBrains Mono font", color = Color.Gray, fontSize = 12.sp, fontFamily = MaterialTheme.typography.labelSmall.fontFamily)
                            }
                            Switch(
                                checked = useMonospace,
                                onCheckedChange = { useMonospace = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors[selectedColorIndex])
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Theme
                Text("THEME", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Accent Color", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Custom", color = colors[selectedColorIndex], fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colors.forEachIndexed { index, color ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedColorIndex == index) 2.dp else 0.dp,
                                            color = if (selectedColorIndex == index) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorIndex = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedColorIndex == index) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }
                        }
                        
                        Divider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 16.dp))
                        
                        // Text Value Color
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Value Color", color = Color.White, fontWeight = FontWeight.Medium)
                            Text(listOf("White", "Accent", "Silver", "Auto")[selectedTextColorIndex], color = colors[selectedColorIndex], fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val textColorSamples = listOf(Color.White, colors[selectedColorIndex], Color(0xFFCBD5E1), Color(0xFF22C55E))
                        val textColorLabels = listOf("White", "Accent", "Silver", "Auto")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            textColorSamples.forEachIndexed { index, col ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(col.copy(alpha = 0.15f))
                                        .border(if (selectedTextColorIndex == index) 2.dp else 1.dp, if (selectedTextColorIndex == index) Color.White else col.copy(0.3f), CircleShape)
                                        .clickable { selectedTextColorIndex = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(textColorLabels[index].take(1), color = col, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp)) // padding for bottom button
            }
        }
        
        // Bottom Action
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .padding(24.dp)
        ) {
            Button(
                onClick = { 
                    if (hasChanges) {
                        viewModel.saveSettings(opacity, selectedTextSize, useMonospace, selectedColorIndex, selectedBgColorIndex, selectedBorderColorIndex, selectedTextColorIndex)
                        Toast.makeText(context, "Appearance configuration saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = hasChanges,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors[selectedColorIndex], 
                    contentColor = Color.White,
                    disabledContainerColor = Color.DarkGray.copy(alpha = 0.5f),
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (hasChanges) "Apply Changes" else "Applied", fontWeight = FontWeight.Bold)
            }
        }
    }
}
