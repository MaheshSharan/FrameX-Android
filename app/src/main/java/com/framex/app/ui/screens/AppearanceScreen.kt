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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.framex.app.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val overlayMode = settingsRepository.overlayMode
    val enabledModules = settingsRepository.enabledModules
    val moduleOrder = settingsRepository.moduleOrder
    val overlayOpacity = settingsRepository.overlayOpacity
    val overlayTextSize = settingsRepository.overlayTextSize
    val overlayScale = settingsRepository.overlayScale
    val overlayUseMonospace = settingsRepository.overlayUseMonospace
    val overlayColorIndex = settingsRepository.overlayColorIndex
    val overlayBgColorIndex = settingsRepository.overlayBgColorIndex
    val overlayBorderColorIndex = settingsRepository.overlayBorderColorIndex
    val overlayTextColorIndex = settingsRepository.overlayTextColorIndex

    fun saveSettings(
        opacity: Float, scale: Float, useMonospace: Boolean, colorIndex: Int,
        bgColorIndex: Int, borderColorIndex: Int, textColorIndex: Int
    ) {
        settingsRepository.setOverlayOpacity(opacity)
        settingsRepository.setOverlayScale(scale)
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
    val savedScale by viewModel.overlayScale.collectAsState()
    val savedUseMonospace by viewModel.overlayUseMonospace.collectAsState()
    val savedColorIndex by viewModel.overlayColorIndex.collectAsState()
    val savedBgColorIndex by viewModel.overlayBgColorIndex.collectAsState()
    val savedBorderColorIndex by viewModel.overlayBorderColorIndex.collectAsState()
    val savedTextColorIndex by viewModel.overlayTextColorIndex.collectAsState()
    val mode by viewModel.overlayMode.collectAsState()
    val enabledModules by viewModel.enabledModules.collectAsState()
    val moduleOrder by viewModel.moduleOrder.collectAsState()
    
    val context = LocalContext.current

    var opacity by remember(savedOpacity) { mutableStateOf(savedOpacity) }
    var selectedTextSize by remember(savedTextSize) { mutableStateOf(savedTextSize) }
    var overlayScale by remember(savedScale) { mutableStateOf(savedScale) }
    var useMonospace by remember(savedUseMonospace) { mutableStateOf(savedUseMonospace) }
    var selectedColorIndex by remember(savedColorIndex) { mutableStateOf(savedColorIndex) }
    var selectedBgColorIndex by remember(savedBgColorIndex) { mutableStateOf(savedBgColorIndex) }
    var selectedBorderColorIndex by remember(savedBorderColorIndex) { mutableStateOf(savedBorderColorIndex) }
    var selectedTextColorIndex by remember(savedTextColorIndex) { mutableStateOf(savedTextColorIndex) }
    
    val hasChanges = opacity != savedOpacity || selectedTextSize != savedTextSize ||
        kotlin.math.abs(overlayScale - savedScale) > 0.01f ||
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
                        .background(Color.DarkGray)
                ) {
                    // Dynamic Overlay inside — ordered per the user's Overlay Config
                    // customization so this preview always matches the real overlay.
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        com.framex.app.ui.components.OverlayPreviewContent(
                            mode = mode,
                            enabledModules = enabledModules,
                            moduleOrder = moduleOrder,
                            opacity = opacity,
                            textSize = selectedTextSize,
                            overlayScale = overlayScale,
                            useMonospace = useMonospace,
                            colorIndex = selectedColorIndex,
                            bgColorIndex = selectedBgColorIndex,
                            borderColorIndex = selectedBorderColorIndex,
                            textColorIndex = selectedTextColorIndex
                        )
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

                        HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 16.dp))

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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Text Size & Scale", color = Color.White, fontWeight = FontWeight.Medium)
                                Text("${(overlayScale * 100).toInt()}%", color = colors[selectedColorIndex], fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .padding(4.dp)
                            ) {
                                listOf("Small", "Medium", "Large").forEachIndexed { index, label ->
                                    val isSelected = selectedTextSize == index
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                            .clickable {
                                                selectedTextSize = index
                                                overlayScale = when (index) {
                                                    0 -> 0.8f
                                                    2 -> 1.2f
                                                    else -> 1.0f
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else Color.Gray,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Slider(
                                value = overlayScale,
                                onValueChange = { newScale ->
                                    overlayScale = newScale
                                    selectedTextSize = when {
                                        kotlin.math.abs(newScale - 0.8f) < 0.05f -> 0
                                        kotlin.math.abs(newScale - 1.0f) < 0.05f -> 1
                                        kotlin.math.abs(newScale - 1.2f) < 0.05f -> 2
                                        else -> -1
                                    }
                                },
                                valueRange = 0.5f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = colors[selectedColorIndex],
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(0.05f))
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
                        
                        HorizontalDivider(color = Color.White.copy(0.05f), modifier = Modifier.padding(vertical = 16.dp))
                        
                        // Text Value Color
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Value Color", color = Color.White, fontWeight = FontWeight.Medium)
                            val selectedName = if (selectedTextColorIndex == 3) "Auto (Dynamic FPS)" else listOf("White", "Accent", "Silver", "Auto")[selectedTextColorIndex]
                            Text(selectedName, color = if (selectedTextColorIndex == 3) Color(0xFF22C55E) else colors[selectedColorIndex], fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        val textColorSamples = listOf(Color.White, colors[selectedColorIndex], Color(0xFFCBD5E1), Color(0xFF22C55E))
                        val textColorLabels = listOf("White", "Accent", "Silver", "Auto (Dynamic FPS)")
                        val autoGradient = androidx.compose.ui.graphics.Brush.sweepGradient(
                            listOf(Color(0xFF22C55E), Color(0xFFEAB308), Color(0xFFEF4444), Color(0xFF22C55E))
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            textColorSamples.forEachIndexed { index, col ->
                                val isAuto = index == 3
                                val bgModifier = if (isAuto) {
                                    Modifier.background(autoGradient)
                                } else {
                                    Modifier.background(col.copy(alpha = 0.15f))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .then(bgModifier)
                                        .border(
                                            if (selectedTextColorIndex == index) 2.dp else 1.dp,
                                            if (selectedTextColorIndex == index) Color.White else if (isAuto) Color.White.copy(0.4f) else col.copy(0.3f),
                                            CircleShape
                                        )
                                        .semantics {
                                            contentDescription = if (isAuto) {
                                                "Auto Value Color: Dynamically colors metrics (Green, Yellow, Red) based on live FPS performance"
                                            } else {
                                                "${textColorLabels[index]} Value Color"
                                            }
                                        }
                                        .clickable { selectedTextColorIndex = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isAuto) {
                                        Text(textColorLabels[index].take(1), color = col, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
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
                        viewModel.saveSettings(opacity, overlayScale, useMonospace, selectedColorIndex, selectedBgColorIndex, selectedBorderColorIndex, selectedTextColorIndex)
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
