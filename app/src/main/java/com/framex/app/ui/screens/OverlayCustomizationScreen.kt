package com.framex.app.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MetricModule(
    val id: String,
    val name: String,
    val valueMock: String,
    val icon: ImageVector,
    val enabled: Boolean
)

@HiltViewModel
class OverlayCustomizationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val savedMode = settingsRepository.overlayMode
    val savedModules = settingsRepository.enabledModules
    val opacity = settingsRepository.overlayOpacity
    val textSize = settingsRepository.overlayTextSize
    val useMonospace = settingsRepository.overlayUseMonospace
    val colorIndex = settingsRepository.overlayColorIndex
    
    fun saveSettings(mode: String, modules: List<MetricModule>) {
        settingsRepository.setOverlayMode(mode)
        settingsRepository.setEnabledModules(modules.filter { it.enabled }.map { it.id }.toSet())
    }
}

@Composable
fun OverlayCustomizationScreen(
    onNavigateBack: () -> Unit,
    viewModel: OverlayCustomizationViewModel = hiltViewModel()
) {
    val savedMode by viewModel.savedMode.collectAsState()
    val savedModules by viewModel.savedModules.collectAsState()
    val opacity by viewModel.opacity.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val useMonospace by viewModel.useMonospace.collectAsState()
    val colorIndex by viewModel.colorIndex.collectAsState()
    val context = LocalContext.current

    var selectedMode by remember(savedMode) { mutableStateOf(savedMode) }
    val modes = listOf("Minimal", "Compact", "Expanded")
    
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF60A5FA),
        Color(0xFF34D399),
        Color(0xFF2DD4BF),
        Color(0xFFA78BFA),
        Color(0xFFFBBF24)
    )
    val accentColor = colors[colorIndex]
    val fontFamily = if (useMonospace) androidx.compose.ui.text.font.FontFamily.Monospace else MaterialTheme.typography.bodyMedium.fontFamily
    val textScale = when(textSize) { 0 -> 0.8f; 2 -> 1.2f; else -> 1.0f }

    var modules by remember(savedModules) {
        mutableStateOf(
            listOf(
                MetricModule("fps", "Frames Per Second", "120", Icons.Default.Speed, savedModules.contains("fps")),
                MetricModule("cpu", "CPU Usage", "34%", Icons.Default.Memory, savedModules.contains("cpu")),
                MetricModule("ram", "RAM Usage", "4.2 GB", Icons.Default.DeveloperBoard, savedModules.contains("ram")),
                MetricModule("temp", "Battery Temp", "38°C", Icons.Default.DeviceThermostat, savedModules.contains("temp")),
                MetricModule("net", "Network Speed", "1.2 MB", Icons.Default.NetworkCheck, savedModules.contains("net"))
            )
        )
    }

    val hasChanges = selectedMode != savedMode || modules.filter { it.enabled }.map { it.id }.toSet() != savedModules

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                Text("Overlay Config", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    // Mode Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp)
                    ) {
                        modes.forEach { mode ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (selectedMode == mode) accentColor else Color.Transparent)
                                    .clickable { selectedMode = mode }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode,
                                    color = if (selectedMode == mode) Color.White else Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    // Preview Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary.copy(0.2f)).border(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Preview", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("layout_v2.json", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                            
                            // Dynamic Preview Builder
                            val enabledMods = modules.filter { it.enabled }
                            
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(opacity))
                                        .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                                        .padding(if (selectedMode == "Minimal") (4 * textScale).dp else (8 * textScale).dp)
                                ) {
                                    if (selectedMode == "Expanded") {
                                        Column(verticalArrangement = Arrangement.spacedBy((8 * textScale).dp)) {
                                            enabledMods.forEach { mod ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(mod.icon, contentDescription = null, tint = accentColor, modifier = Modifier.size((16 * textScale).dp))
                                                    Spacer(modifier = Modifier.width((8 * textScale).dp))
                                                    Text(mod.name, color = Color.Gray, fontSize = (10 * textScale).sp, fontFamily = fontFamily, modifier = Modifier.weight(1f))
                                                    Text(mod.valueMock, color = Color.White, fontFamily = fontFamily, style = MaterialTheme.typography.labelSmall.copy(fontSize = (12 * textScale).sp, fontWeight = FontWeight.Bold))
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy((12 * textScale).dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = (4 * textScale).dp)
                                        ) {
                                            enabledMods.forEachIndexed { index, mod ->
                                                if (selectedMode == "Minimal") {
                                                    Text(mod.valueMock, color = Color.White, fontFamily = fontFamily, style = MaterialTheme.typography.labelSmall.copy(fontSize = (14 * textScale).sp, fontWeight = FontWeight.Bold))
                                                } else {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(mod.id.uppercase(), color = Color.Gray, fontFamily = fontFamily, fontSize = (10 * textScale).sp, fontWeight = FontWeight.Bold)
                                                        Text(mod.valueMock, color = if (index == 0) accentColor else Color.White, fontFamily = fontFamily, style = MaterialTheme.typography.labelSmall.copy(fontSize = (16 * textScale).sp, fontWeight = FontWeight.Bold))
                                                    }
                                                }
                                                if (index < enabledMods.size - 1) {
                                                    Box(modifier = Modifier.width(1.dp).height(if (selectedMode == "Minimal") (12 * textScale).dp else (24 * textScale).dp).background(Color.DarkGray))
                                                }
                                            }
                                            if (enabledMods.isEmpty()) {
                                                Text("No modules selected", color = Color.Gray, fontFamily = fontFamily, fontSize = (12 * textScale).sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Active Modules", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Drag to reorder. Toggle to show in overlay.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                items(modules) { module ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                            Icon(module.icon, contentDescription = null, tint = if (module.enabled) accentColor else Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(module.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if(module.enabled) accentColor.copy(0.1f) else MaterialTheme.colorScheme.background).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(module.valueMock, color = if(module.enabled) accentColor else Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Switch(
                            checked = module.enabled,
                            onCheckedChange = { isChecked ->
                                modules = modules.map { if (it.id == module.id) it.copy(enabled = isChecked) else it }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(Icons.Default.DragIndicator, contentDescription = "Drag", tint = Color.Gray)
                    }
                }
            }
        }
        
        // Bottom Action
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(0.95f))
                .padding(24.dp)
        ) {
            Button(
                onClick = { 
                    if (hasChanges) {
                        viewModel.saveSettings(selectedMode, modules)
                        Toast.makeText(context, "Overlay configuration saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = hasChanges,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor, 
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
