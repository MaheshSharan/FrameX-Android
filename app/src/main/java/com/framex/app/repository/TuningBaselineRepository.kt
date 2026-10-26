package com.framex.app.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The values hardware had before FrameX changed them, so Restore defaults can put them back
 * even after the process dies. A baseline is written once, before the first change, and only
 * removed after a successful restore.
 */
@Singleton
class TuningBaselineRepository @Inject constructor(
    private val store: FrameXPreferenceStore
) {
    data class Baseline(val maxFreqKHz: Long, val governor: String)

    suspend fun captureCpuIfAbsent(policyId: Int, maxFreqKHz: Long, governor: String) =
        captureIfAbsent("cpu_$policyId", maxFreqKHz, governor)

    suspend fun captureGpuIfAbsent(maxFreqKHz: Long, governor: String) =
        captureIfAbsent("gpu", maxFreqKHz, governor)

    fun cpuBaseline(policyId: Int): Baseline? = read("cpu_$policyId")

    fun gpuBaseline(): Baseline? = read("gpu")

    fun clearCpu(policyId: Int) = clear("cpu_$policyId")

    fun clearGpu() = clear("gpu")

    fun hasAnyBaseline(policyIds: List<Int>): Boolean =
        read("gpu") != null || policyIds.any { read("cpu_$it") != null }

    private suspend fun captureIfAbsent(id: String, maxFreqKHz: Long, governor: String) {
        store.awaitLoaded()
        if (store.contains("tuning_orig_max_$id")) return
        store.putLong("tuning_orig_max_$id", maxFreqKHz)
        store.putString("tuning_orig_gov_$id", governor)
        store.flush()
    }

    private fun read(id: String): Baseline? {
        if (!store.contains("tuning_orig_max_$id")) return null
        return Baseline(
            maxFreqKHz = store.getLong("tuning_orig_max_$id", 0L),
            governor = store.getString("tuning_orig_gov_$id", "").orEmpty()
        )
    }

    private fun clear(id: String) {
        store.remove("tuning_orig_max_$id")
        store.remove("tuning_orig_gov_$id")
    }
}
