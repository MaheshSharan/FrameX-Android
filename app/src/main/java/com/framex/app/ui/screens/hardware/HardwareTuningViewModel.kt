package com.framex.app.ui.screens.hardware

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.R
import com.framex.app.hardware.CpuTuningController
import com.framex.app.hardware.GpuTuningController
import com.framex.app.hardware.HardwareTuningSnapshot
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.repository.TuningBaselineRepository
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A change the person asked for and has not confirmed yet. */
sealed interface PendingTuneChange {
    data class CpuMaxFreq(val policyId: Int, val freqKHz: Long) : PendingTuneChange
    data class CpuGovernor(val policyId: Int, val governor: String) : PendingTuneChange
    data object GpuMaxFreq : PendingTuneChange
    data object GpuGovernor : PendingTuneChange
}

data class TuningMessage(@StringRes val resId: Int)

@HiltViewModel
class HardwareTuningViewModel @Inject constructor(
    private val cpuTuningController: CpuTuningController,
    private val gpuTuningController: GpuTuningController,
    private val baselines: TuningBaselineRepository,
    val privilegeManager: PrivilegeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HardwareTuningSnapshot(isLoading = true))
    val uiState: StateFlow<HardwareTuningSnapshot> = _uiState.asStateFlow()

    private val _pending = MutableStateFlow<PendingTuneChange?>(null)
    val pending: StateFlow<PendingTuneChange?> = _pending.asStateFlow()

    private val _message = MutableStateFlow<TuningMessage?>(null)
    val message: StateFlow<TuningMessage?> = _message.asStateFlow()

    private var pendingGpuFreq = 0L
    private var pendingGpuGovernor = ""

    init {
        refreshHardware()
        viewModelScope.launch {
            privilegeManager.capabilities.collect { caps ->
                _uiState.update { it.copy(canTune = caps?.canWriteSysfs == true) }
            }
        }
    }

    fun refreshHardware() {
        viewModelScope.launch {
            if (_uiState.value.cpuClusters.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, lastError = null) }
            }
            try {
                val cpuClusters = cpuTuningController.probeCpuClusters()
                val gpu = gpuTuningController.probeGpu()
                _uiState.update {
                    it.copy(
                        cpuClusters = cpuClusters,
                        gpu = gpu,
                        isLoading = false,
                        lastError = null,
                        hasBaseline = baselines.hasAnyBaseline(cpuClusters.map { c -> c.policyId })
                    )
                }
            } catch (e: Exception) {
                FrameXLog.e("Hardware probe failed", e)
                _uiState.update { it.copy(isLoading = false, lastError = e.message ?: "Failed to probe hardware") }
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    /** Validates against what the kernel advertises, then asks for confirmation. */
    fun requestCpuMaxFreq(policyId: Int, freqKHz: Long) {
        val cluster = _uiState.value.cpuClusters.firstOrNull { it.policyId == policyId } ?: return
        if (!_uiState.value.canTune) return reject(R.string.tuning_err_read_only)
        if (cluster.availableFrequenciesKHz.isNotEmpty() && freqKHz !in cluster.availableFrequenciesKHz) {
            return reject(R.string.tuning_err_invalid_value)
        }
        _pending.value = PendingTuneChange.CpuMaxFreq(policyId, freqKHz)
    }

    fun requestCpuGovernor(policyId: Int, governor: String) {
        val cluster = _uiState.value.cpuClusters.firstOrNull { it.policyId == policyId } ?: return
        if (!_uiState.value.canTune) return reject(R.string.tuning_err_read_only)
        if (cluster.availableGovernors.isNotEmpty() && governor !in cluster.availableGovernors) {
            return reject(R.string.tuning_err_invalid_value)
        }
        _pending.value = PendingTuneChange.CpuGovernor(policyId, governor)
    }

    fun requestGpuMaxFreq(freqKHz: Long) {
        val gpu = _uiState.value.gpu
        if (!_uiState.value.canTune) return reject(R.string.tuning_err_read_only)
        if (gpu.availableFrequenciesKHz.isNotEmpty() && freqKHz !in gpu.availableFrequenciesKHz) {
            return reject(R.string.tuning_err_invalid_value)
        }
        pendingGpuFreq = freqKHz
        _pending.value = PendingTuneChange.GpuMaxFreq
    }

    fun requestGpuGovernor(governor: String) {
        val gpu = _uiState.value.gpu
        if (!_uiState.value.canTune) return reject(R.string.tuning_err_read_only)
        if (gpu.availableGovernors.isNotEmpty() && governor !in gpu.availableGovernors) {
            return reject(R.string.tuning_err_invalid_value)
        }
        pendingGpuGovernor = governor
        _pending.value = PendingTuneChange.GpuGovernor
    }

    fun cancelPending() {
        _pending.value = null
    }

    fun confirmPending() {
        val change = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            val applied = when (change) {
                is PendingTuneChange.CpuMaxFreq -> applyCpu(change.policyId) {
                    cpuTuningController.setClusterMaxFreq(change.policyId, change.freqKHz)
                }
                is PendingTuneChange.CpuGovernor -> applyCpu(change.policyId) {
                    cpuTuningController.setClusterGovernor(change.policyId, change.governor)
                }
                PendingTuneChange.GpuMaxFreq -> applyGpu { gpuTuningController.setGpuMaxFreq(pendingGpuFreq) }
                PendingTuneChange.GpuGovernor -> applyGpu { gpuTuningController.setGpuGovernor(pendingGpuGovernor) }
            }
            if (!applied) _message.value = TuningMessage(R.string.tuning_err_verify_failed)
            refreshHardware()
        }
    }

    /** Records the original values durably before the first write, then writes. */
    private suspend fun applyCpu(policyId: Int, write: suspend () -> Boolean): Boolean {
        val cluster = _uiState.value.cpuClusters.firstOrNull { it.policyId == policyId } ?: return false
        baselines.captureCpuIfAbsent(policyId, cluster.maxFreqKHz, cluster.currentGovernor)
        return write()
    }

    private suspend fun applyGpu(write: suspend () -> Boolean): Boolean {
        val gpu = _uiState.value.gpu
        baselines.captureGpuIfAbsent(gpu.maxFreqKHz, gpu.currentGovernor)
        return write()
    }

    /** Puts every value that FrameX changed back to what it was. Baselines stay if anything fails. */
    fun restoreDefaults() {
        if (!_uiState.value.canTune) return reject(R.string.tuning_err_read_only)
        viewModelScope.launch {
            var allRestored = true
            for (cluster in _uiState.value.cpuClusters) {
                val baseline = baselines.cpuBaseline(cluster.policyId) ?: continue
                val freqOk = cpuTuningController.setClusterMaxFreq(cluster.policyId, baseline.maxFreqKHz)
                val govOk = baseline.governor.isBlank() ||
                    cpuTuningController.setClusterGovernor(cluster.policyId, baseline.governor)
                if (freqOk && govOk) baselines.clearCpu(cluster.policyId) else allRestored = false
            }
            baselines.gpuBaseline()?.let { baseline ->
                val freqOk = gpuTuningController.setGpuMaxFreq(baseline.maxFreqKHz)
                val govOk = baseline.governor.isBlank() || gpuTuningController.setGpuGovernor(baseline.governor)
                if (freqOk && govOk) baselines.clearGpu() else allRestored = false
            }
            _message.value = TuningMessage(
                if (allRestored) R.string.tuning_msg_restored else R.string.tuning_err_restore_partial
            )
            refreshHardware()
        }
    }

    fun resetCpuCluster(policyId: Int) {
        if (!_uiState.value.canTune) return reject(R.string.tuning_err_read_only)
        viewModelScope.launch {
            if (cpuTuningController.resetClusterToStock(policyId)) {
                baselines.clearCpu(policyId)
                _message.value = TuningMessage(R.string.tuning_msg_cluster_reset)
            } else {
                _message.value = TuningMessage(R.string.tuning_err_verify_failed)
            }
            refreshHardware()
        }
    }

    private fun reject(@StringRes res: Int) {
        _message.value = TuningMessage(res)
    }
}
