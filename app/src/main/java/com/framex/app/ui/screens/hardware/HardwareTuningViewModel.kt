package com.framex.app.ui.screens.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.hardware.CpuClusterPolicy
import com.framex.app.hardware.CpuTuningController
import com.framex.app.hardware.GpuTuningController
import com.framex.app.hardware.HardwareTuningSnapshot
import com.framex.app.privilege.PrivilegeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HardwareTuningViewModel @Inject constructor(
    private val cpuTuningController: CpuTuningController,
    private val gpuTuningController: GpuTuningController,
    val privilegeManager: PrivilegeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HardwareTuningSnapshot(isLoading = true))
    val uiState: StateFlow<HardwareTuningSnapshot> = _uiState.asStateFlow()

    init {
        refreshHardware()
    }

    fun refreshHardware() {
        viewModelScope.launch {
            if (_uiState.value.cpuClusters.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, lastError = null)
            }
            try {
                val cpuClusters = cpuTuningController.probeCpuClusters()
                val gpu = gpuTuningController.probeGpu()
                _uiState.value = HardwareTuningSnapshot(
                    cpuClusters = cpuClusters,
                    gpu = gpu,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastError = e.message ?: "Failed to probe hardware"
                )
            }
        }
    }

    fun setCpuMaxFreq(policyId: Int, freqKHz: Long) {
        val currentClusters = _uiState.value.cpuClusters
        _uiState.value = _uiState.value.copy(
            cpuClusters = currentClusters.map {
                if (it.policyId == policyId) it.copy(maxFreqKHz = freqKHz) else it
            }
        )
        viewModelScope.launch {
            val success = cpuTuningController.setClusterMaxFreq(policyId, freqKHz)
            if (!success) {
                refreshHardware()
            }
        }
    }

    fun setCpuGovernor(policyId: Int, governor: String) {
        val currentClusters = _uiState.value.cpuClusters
        _uiState.value = _uiState.value.copy(
            cpuClusters = currentClusters.map {
                if (it.policyId == policyId) it.copy(currentGovernor = governor) else it
            }
        )
        viewModelScope.launch {
            val success = cpuTuningController.setClusterGovernor(policyId, governor)
            if (!success) {
                refreshHardware()
            }
        }
    }

    fun resetCpuCluster(policyId: Int) {
        viewModelScope.launch {
            val success = cpuTuningController.resetClusterToStock(policyId)
            if (success) {
                refreshHardware()
            }
        }
    }

    fun setGpuMaxFreq(freqKHz: Long) {
        val currentGpu = _uiState.value.gpu
        _uiState.value = _uiState.value.copy(gpu = currentGpu.copy(maxFreqKHz = freqKHz))
        viewModelScope.launch {
            val success = gpuTuningController.setGpuMaxFreq(freqKHz)
            if (!success) {
                refreshHardware()
            }
        }
    }

    fun setGpuGovernor(governor: String) {
        val currentGpu = _uiState.value.gpu
        _uiState.value = _uiState.value.copy(gpu = currentGpu.copy(currentGovernor = governor))
        viewModelScope.launch {
            val success = gpuTuningController.setGpuGovernor(governor)
            if (!success) {
                refreshHardware()
            }
        }
    }
}
