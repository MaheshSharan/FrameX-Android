package com.framex.app.ui.screens.hardware

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.hilt.navigation.compose.hiltViewModel
import com.framex.app.R
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
    val draft by viewModel.draft.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it.resId) }

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!draft.isEmpty && !uiState.locked) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::discardDraft,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tuning_discard")
                        ) { Text(stringResource(R.string.tuning_discard)) }
                        Button(
                            onClick = viewModel::applyDraft,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tuning_apply")
                        ) { Text(stringResource(R.string.tuning_apply)) }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tuning_screen_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshHardware() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
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
                    PrivilegeBanner(mode = privilegeMode, canTune = uiState.canTune)
                }

                uiState.lastError?.let { error ->
                    item { Text(text = error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                }

                if (privilegeMode == PrivilegeMode.ROOT && uiState.locked) {
                    item {
                        Text(stringResource(R.string.tuning_locked_note), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Button(
                            onClick = viewModel::unlockFrequencyNodes,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tuning_unlock_nodes")
                        ) { Text(stringResource(R.string.tuning_unlock_nodes)) }
                    }
                }

                if (uiState.hasBaseline) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::restoreDefaults,
                            enabled = uiState.canTune,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tuning_restore_defaults")
                        ) { Text(stringResource(R.string.tuning_restore_defaults)) }
                    }
                }

                // GPU Section
                item {
                    Text(
                        text = stringResource(R.string.tuning_gpu_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    GpuControlCard(
                        gpu = uiState.gpu,
                        enabled = uiState.canTune && !uiState.locked,
                        draftFreqKHz = draft.gpuFreqKHz,
                        draftGovernor = draft.gpuGovernor,
                        onMaxFreqSelected = { viewModel.selectGpuMaxFreq(it) },
                        onGovernorSelected = { viewModel.selectGpuGovernor(it) }
                    )
                }

                // CPU Clusters Section
                item {
                    Text(
                        text = stringResource(R.string.tuning_cpu_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(uiState.cpuClusters, key = { it.policyId }) { cluster ->
                    CpuClusterCard(
                        cluster = cluster,
                        enabled = uiState.canTune && !uiState.locked,
                        draftFreqKHz = draft.cpuFreqKHz[cluster.policyId],
                        draftGovernor = draft.cpuGovernor[cluster.policyId],
                        onMaxFreqSelected = { viewModel.selectCpuMaxFreq(cluster.policyId, it) },
                        onGovernorSelected = { viewModel.selectCpuGovernor(cluster.policyId, it) },
                        onResetStock = { viewModel.resetCpuCluster(cluster.policyId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivilegeBanner(mode: PrivilegeMode, canTune: Boolean) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (canTune) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth().testTag("tuning_banner")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    when (mode) {
                        PrivilegeMode.ROOT -> R.string.tuning_banner_root
                        PrivilegeMode.SHIZUKU -> R.string.tuning_banner_shizuku
                        PrivilegeMode.NONE -> R.string.tuning_banner_none
                    }
                ),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = stringResource(if (canTune) R.string.tuning_banner_can_tune else R.string.tuning_banner_read_only),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpuControlCard(
    gpu: GpuTuningState,
    enabled: Boolean,
    draftFreqKHz: Long?,
    draftGovernor: String?,
    onMaxFreqSelected: (Long) -> Unit,
    onGovernorSelected: (String) -> Unit
) {
    var freqExpanded by remember { mutableStateOf(false) }
    var govExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = gpu.model, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            if (!gpu.isSupported) {
                Text(
                    text = stringResource(R.string.tuning_gpu_unsupported),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = stringResource(R.string.tuning_gpu_status, gpu.curFreqKHz / 1000, gpu.maxFreqKHz / 1000, gpu.currentGovernor),
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
                        onExpandedChange = { if (enabled) freqExpanded = !freqExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = "${(draftFreqKHz ?: gpu.maxFreqKHz) / 1000} MHz",
                            onValueChange = {},
                            readOnly = true,
                            enabled = enabled,
                            label = { Text(stringResource(R.string.tuning_label_max_clock)) },
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
                        onExpandedChange = { if (enabled) govExpanded = !govExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = (draftGovernor ?: gpu.currentGovernor).ifEmpty { stringResource(R.string.tuning_default_governor) },
                            onValueChange = {},
                            readOnly = true,
                            enabled = enabled,
                            label = { Text(stringResource(R.string.tuning_label_governor)) },
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
    enabled: Boolean,
    draftFreqKHz: Long?,
    draftGovernor: String?,
    onMaxFreqSelected: (Long) -> Unit,
    onGovernorSelected: (String) -> Unit,
    onResetStock: () -> Unit
) {
    var freqExpanded by remember { mutableStateOf(false) }
    var govExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tuning_policy_title, cluster.policyId, cluster.affectedCpus.joinToString(", ")),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(onClick = onResetStock, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.tuning_stock), fontSize = 12.sp)
                }
            }

            Text(
                text = stringResource(R.string.tuning_cpu_status, cluster.curFreqKHz / 1000, cluster.maxFreqKHz / 1000, cluster.hardwareMaxKHz / 1000),
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
                    onExpandedChange = { if (enabled) freqExpanded = !freqExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = "${(draftFreqKHz ?: cluster.maxFreqKHz) / 1000} MHz",
                        onValueChange = {},
                        readOnly = true,
                            enabled = enabled,
                        label = { Text(stringResource(R.string.tuning_label_max_freq)) },
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
                    onExpandedChange = { if (enabled) govExpanded = !govExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = draftGovernor ?: cluster.currentGovernor,
                        onValueChange = {},
                        readOnly = true,
                            enabled = enabled,
                        label = { Text(stringResource(R.string.tuning_label_governor)) },
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
