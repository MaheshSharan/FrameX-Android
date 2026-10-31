package com.framex.app.ui.screens.hardware

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framex.app.R
import com.framex.app.hardware.CpuTuningController
import com.framex.app.hardware.GpuTuningController
import com.framex.app.hardware.HardwareTuningSnapshot
import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.repository.TuningBaselineRepository
import com.framex.app.utils.FrameXLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Selections the person has made and not applied yet. */
data class TuningDraft(
    val cpuFreqKHz: Map<Int, Long> = emptyMap(),
    val cpuGovernor: Map<Int, String> = emptyMap(),
    val gpuFreqKHz: Long? = null,
    val gpuGovernor: String? = null
) {
    val isEmpty: Boolean
        get() = cpuFreqKHz.isEmpty() && cpuGovernor.isEmpty() && gpuFreqKHz == null && gpuGovernor == null
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

    private val _draft = MutableStateFlow(TuningDraft())
    val draft: StateFlow<TuningDraft> = _draft.asStateFlow()

    private val _message = MutableStateFlow<TuningMessage?>(null)
    val message: StateFlow<TuningMessage?> = _message.asStateFlow()

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
                val locked = privilegeManager.currentMode.value == PrivilegeMode.ROOT &&
                    cpuTuningController.isLocked() && gpuTuningController.isLocked()
                _uiState.update {
                    it.copy(
                        cpuClusters = cpuClusters,
                        gpu = gpu,
                        isLoading = false,
                        lastError = null,
                        locked = locked,
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

    private fun editable() = _uiState.value.canTune && !_uiState.value.locked

    fun selectCpuMaxFreq(policyId: Int, freqKHz: Long) {
        if (!editable()) return
        val cluster = _uiState.value.cpuClusters.firstOrNull { it.policyId == policyId } ?: return
        if (cluster.availableFrequenciesKHz.isNotEmpty() && freqKHz !in cluster.availableFrequenciesKHz) return
        _draft.update {
            it.copy(cpuFreqKHz = if (freqKHz == cluster.maxFreqKHz) it.cpuFreqKHz - policyId else it.cpuFreqKHz + (policyId to freqKHz))
        }
    }

    fun selectCpuGovernor(policyId: Int, governor: String) {
        if (!editable()) return
        val cluster = _uiState.value.cpuClusters.firstOrNull { it.policyId == policyId } ?: return
        if (cluster.availableGovernors.isNotEmpty() && governor !in cluster.availableGovernors) return
        _draft.update {
            it.copy(cpuGovernor = if (governor == cluster.currentGovernor) it.cpuGovernor - policyId else it.cpuGovernor + (policyId to governor))
        }
    }

    fun selectGpuMaxFreq(freqKHz: Long) {
        if (!editable()) return
        val gpu = _uiState.value.gpu
        if (gpu.availableFrequenciesKHz.isNotEmpty() && freqKHz !in gpu.availableFrequenciesKHz) return
        _draft.update { it.copy(gpuFreqKHz = freqKHz.takeIf { f -> f != gpu.maxFreqKHz }) }
    }

    fun selectGpuGovernor(governor: String) {
        if (!editable()) return
        val gpu = _uiState.value.gpu
        if (gpu.availableGovernors.isNotEmpty() && governor !in gpu.availableGovernors) return
        _draft.update { it.copy(gpuGovernor = governor.takeIf { g -> g != gpu.currentGovernor }) }
    }

    fun discardDraft() {
        _draft.value = TuningDraft()
    }

    /** Applies every selection, reads each back, then locks all nodes (Root) when all of them held. */
    fun applyDraft() {
        val draft = _draft.value
        if (draft.isEmpty || !editable()) return
        viewModelScope.launch {
            var allApplied = true
            for (policyId in draft.cpuFreqKHz.keys + draft.cpuGovernor.keys) {
                val cluster = _uiState.value.cpuClusters.firstOrNull { it.policyId == policyId } ?: continue
                baselines.captureCpuIfAbsent(policyId, cluster.maxFreqKHz, cluster.currentGovernor)
                draft.cpuFreqKHz[policyId]?.let { allApplied = cpuTuningController.setClusterMaxFreq(policyId, it) && allApplied }
                draft.cpuGovernor[policyId]?.let { allApplied = cpuTuningController.setClusterGovernor(policyId, it) && allApplied }
            }
            if (draft.gpuFreqKHz != null || draft.gpuGovernor != null) {
                val gpu = _uiState.value.gpu
                baselines.captureGpuIfAbsent(gpu.maxFreqKHz, gpu.currentGovernor)
                draft.gpuFreqKHz?.let { allApplied = gpuTuningController.setGpuMaxFreq(it) && allApplied }
                draft.gpuGovernor?.let { allApplied = gpuTuningController.setGpuGovernor(it) && allApplied }
            }
            _draft.value = TuningDraft()
            if (!allApplied) {
                _message.value = TuningMessage(R.string.tuning_err_verify_failed)
            } else if (privilegeManager.currentMode.value == PrivilegeMode.ROOT) {
                val locked = cpuTuningController.lockAll() && gpuTuningController.lockAll()
                _message.value = TuningMessage(
                    if (locked) R.string.tuning_msg_applied_locked else R.string.tuning_err_verify_failed
                )
            } else {
                _message.value = TuningMessage(R.string.tuning_msg_applied_unlocked)
            }
            refreshHardware()
        }
    }

    /** Puts every value that FrameX changed back to what it was. Baselines stay if anything fails. */
    fun unlockFrequencyNodes() {
        viewModelScope.launch {
            val ok = cpuTuningController.unlockAll() && gpuTuningController.unlockAll()
            _message.value = TuningMessage(
                if (ok) R.string.tuning_msg_unlocked else R.string.tuning_err_unlock_failed
            )
            refreshHardware()
        }
    }

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
