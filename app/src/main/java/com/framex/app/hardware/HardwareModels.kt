package com.framex.app.hardware

import androidx.compose.runtime.Immutable

@Immutable
data class CpuClusterPolicy(
    val policyId: Int,
    val affectedCpus: List<Int>,
    val curFreqKHz: Long,
    val minFreqKHz: Long,
    val maxFreqKHz: Long,
    val hardwareMaxKHz: Long,
    val availableFrequenciesKHz: List<Long>,
    val availableGovernors: List<String>,
    val currentGovernor: String
)

@Immutable
data class GpuTuningState(
    val model: String,
    val isSupported: Boolean,
    val curFreqKHz: Long,
    val minFreqKHz: Long,
    val maxFreqKHz: Long,
    val availableFrequenciesKHz: List<Long>,
    val availableGovernors: List<String>,
    val currentGovernor: String
)

@Immutable
data class HardwareTuningSnapshot(
    val cpuClusters: List<CpuClusterPolicy> = emptyList(),
    val gpu: GpuTuningState = GpuTuningState(
        model = "Unknown",
        isSupported = false,
        curFreqKHz = 0L,
        minFreqKHz = 0L,
        maxFreqKHz = 0L,
        availableFrequenciesKHz = emptyList(),
        availableGovernors = emptyList(),
        currentGovernor = ""
    ),
    val isLoading: Boolean = false,
    val lastError: String? = null,
    /** False when the active backend cannot write sysfs; the screen is then read-only. */
    val canTune: Boolean = false,
    val hasBaseline: Boolean = false
)
