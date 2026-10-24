package com.framex.app.ui.screens.hardware

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.framex.app.hardware.CpuClusterPolicy
import com.framex.app.hardware.GpuTuningState
import com.framex.app.privilege.PrivilegeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareTuningScreen(
    onNavigateBack: () -> Unit,
    viewModel: HardwareTuningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val privilegeMode by viewModel.privilegeManager.currentMode.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware Tuning", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshHardware() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Mode Badge
                item {
                    PrivilegeBanner(mode = privilegeMode)
                }

                // GPU Section
                item {
                    Text(
                        text = "GPU Acceleration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    GpuControlCard(
                        gpu = uiState.gpu,
                        onMaxFreqSelected = { viewModel.setGpuMaxFreq(it) },
                        onGovernorSelected = { viewModel.setGpuGovernor(it) }
                    )
                }

                // CPU Clusters Section
                item {
                    Text(
                        text = "CPU Dynamic Clusters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(uiState.cpuClusters, key = { it.policyId }) { cluster ->
                    CpuClusterCard(
                        cluster = cluster,
                        onMaxFreqSelected = { viewModel.setCpuMaxFreq(cluster.policyId, it) },
                        onGovernorSelected = { viewModel.setCpuGovernor(cluster.policyId, it) },
                        onResetStock = {
                            viewModel.resetCpuCluster(cluster.policyId)
                            Toast.makeText(context, "Cluster ${cluster.policyId} reset to stock", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivilegeBanner(mode: PrivilegeMode) {
    val isRoot = mode == PrivilegeMode.ROOT
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRoot) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (isRoot) "Root Privilege Mode Active" else "Shizuku ADB Mode Active",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isRoot) "Full sysfs clamp & governor write unlocked" else "Direct kernel node writes may require root access",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpuControlCard(
    gpu: GpuTuningState,
    onMaxFreqSelected: (Long) -> Unit,
    onGovernorSelected: (String) -> Unit
) {
    var freqExpanded by remember { mutableStateOf(false) }
    var govExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = gpu.model, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (!gpu.isSupported) {
                Text(
                    text = "Kernel node not exposed or unsupported GPU interface",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = "Current: ${gpu.curFreqKHz / 1000} MHz | Max: ${gpu.maxFreqKHz / 1000} MHz | Gov: ${gpu.currentGovernor}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Freq Picker
                    ExposedDropdownMenuBox(
                        expanded = freqExpanded,
                        onExpandedChange = { freqExpanded = !freqExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = "${gpu.maxFreqKHz / 1000} MHz",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Max Clock") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = freqExpanded,
                            onDismissRequest = { freqExpanded = false }
                        ) {
                            gpu.availableFrequenciesKHz.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text("${freq / 1000} MHz") },
                                    onClick = {
                                        onMaxFreqSelected(freq)
                                        freqExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Gov Picker
                    ExposedDropdownMenuBox(
                        expanded = govExpanded,
                        onExpandedChange = { govExpanded = !govExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = gpu.currentGovernor.ifEmpty { "Default" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Governor") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = govExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = govExpanded,
                            onDismissRequest = { govExpanded = false }
                        ) {
                            gpu.availableGovernors.forEach { gov ->
                                DropdownMenuItem(
                                    text = { Text(gov) },
                                    onClick = {
                                        onGovernorSelected(gov)
                                        govExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CpuClusterCard(
    cluster: CpuClusterPolicy,
    onMaxFreqSelected: (Long) -> Unit,
    onGovernorSelected: (String) -> Unit,
    onResetStock: () -> Unit
) {
    var freqExpanded by remember { mutableStateOf(false) }
    var govExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Policy ${cluster.policyId} (Cores ${cluster.affectedCpus.joinToString(", ")})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(onClick = onResetStock) {
                    Text("Stock", fontSize = 12.sp)
                }
            }

            Text(
                text = "Current: ${cluster.curFreqKHz / 1000} MHz | Max Cap: ${cluster.maxFreqKHz / 1000} MHz (Stock: ${cluster.hardwareMaxKHz / 1000} MHz)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Max Freq Dropdown
                ExposedDropdownMenuBox(
                    expanded = freqExpanded,
                    onExpandedChange = { freqExpanded = !freqExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = "${cluster.maxFreqKHz / 1000} MHz",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Max Freq") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = freqExpanded,
                        onDismissRequest = { freqExpanded = false }
                    ) {
                        cluster.availableFrequenciesKHz.forEach { freq ->
                            DropdownMenuItem(
                                text = { Text("${freq / 1000} MHz") },
                                onClick = {
                                    onMaxFreqSelected(freq)
                                    freqExpanded = false
                                }
                            )
                        }
                    }
                }

                // Governor Dropdown
                ExposedDropdownMenuBox(
                    expanded = govExpanded,
                    onExpandedChange = { govExpanded = !govExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = cluster.currentGovernor,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Governor") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = govExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = govExpanded,
                        onDismissRequest = { govExpanded = false }
                    ) {
                        cluster.availableGovernors.forEach { gov ->
                            DropdownMenuItem(
                                text = { Text(gov) },
                                onClick = {
                                    onGovernorSelected(gov)
                                    govExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
