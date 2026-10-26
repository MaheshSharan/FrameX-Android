package com.framex.app.hardware

import com.framex.app.privilege.PrivilegeManager
import com.framex.app.utils.FrameXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CpuTuningController @Inject constructor(
    private val privilegeManager: PrivilegeManager
) {
    companion object {
        private const val BATCH_CPU_SCRIPT =
            "for p in /sys/devices/system/cpu/cpufreq/policy*; do " +
            "echo \"[POLICY]:\$p\"; " +
            "echo -n \"affected:\"; cat \"\$p/affected_cpus\" 2>/dev/null; " +
            "echo -n \"cur:\"; cat \"\$p/scaling_cur_freq\" 2>/dev/null; " +
            "echo -n \"min:\"; cat \"\$p/scaling_min_freq\" 2>/dev/null; " +
            "echo -n \"max:\"; cat \"\$p/scaling_max_freq\" 2>/dev/null; " +
            "echo -n \"hwmax:\"; cat \"\$p/cpuinfo_max_freq\" 2>/dev/null; " +
            "echo -n \"avail_freq:\"; cat \"\$p/scaling_available_frequencies\" 2>/dev/null; " +
            "echo -n \"avail_gov:\"; cat \"\$p/scaling_available_governors\" 2>/dev/null; " +
            "echo -n \"gov:\"; cat \"\$p/scaling_governor\" 2>/dev/null; " +
            "done"
    }

    suspend fun probeCpuClusters(): List<CpuClusterPolicy> = withContext(Dispatchers.IO) {
        val result = privilegeManager.exec(BATCH_CPU_SCRIPT, 3000L)
        if (!result.isSuccess || result.output.isBlank()) return@withContext emptyList()

        val policies = mutableListOf<CpuClusterPolicy>()
        val blocks = result.output.split("[POLICY]:").filter { it.isNotBlank() }

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            val path = lines[0]
            val policyId = path.substringAfter("policy").toIntOrNull() ?: continue

            var affected = emptyList<Int>()
            var curFreq = 0L
            var minFreq = 0L
            var maxFreq = 0L
            var hwMax = 0L
            var availFreqs = emptyList<Long>()
            var availGovs = emptyList<String>()
            var curGov = ""

            for (line in lines.drop(1)) {
                when {
                    line.startsWith("affected:") -> {
                        affected = line.substringAfter("affected:").trim()
                            .split(" ", "\t").mapNotNull { it.trim().toIntOrNull() }
                    }
                    line.startsWith("cur:") -> {
                        curFreq = line.substringAfter("cur:").trim().toLongOrNull() ?: 0L
                    }
                    line.startsWith("min:") -> {
                        minFreq = line.substringAfter("min:").trim().toLongOrNull() ?: 0L
                    }
                    line.startsWith("max:") -> {
                        maxFreq = line.substringAfter("max:").trim().toLongOrNull() ?: 0L
                    }
                    line.startsWith("hwmax:") -> {
                        hwMax = line.substringAfter("hwmax:").trim().toLongOrNull() ?: maxFreq
                    }
                    line.startsWith("avail_freq:") -> {
                        availFreqs = line.substringAfter("avail_freq:").trim()
                            .split(" ", "\t").mapNotNull { it.trim().toLongOrNull() }.sorted()
                    }
                    line.startsWith("avail_gov:") -> {
                        availGovs = line.substringAfter("avail_gov:").trim()
                            .split(" ", "\t").map { it.trim() }.filter { it.isNotBlank() }
                    }
                    line.startsWith("gov:") -> {
                        curGov = line.substringAfter("gov:").trim()
                    }
                }
            }

            if (hwMax == 0L) hwMax = maxFreq

            policies.add(
                CpuClusterPolicy(
                    policyId = policyId,
                    affectedCpus = affected,
                    curFreqKHz = curFreq,
                    minFreqKHz = minFreq,
                    maxFreqKHz = maxFreq,
                    hardwareMaxKHz = hwMax,
                    availableFrequenciesKHz = availFreqs,
                    availableGovernors = availGovs,
                    currentGovernor = curGov
                )
            )
        }
        policies.sortedBy { it.policyId }
    }

    suspend fun setClusterMaxFreq(policyId: Int, maxFreqKHz: Long): Boolean = withContext(Dispatchers.IO) {
        val path = "/sys/devices/system/cpu/cpufreq/policy$policyId/scaling_max_freq"
        val cmd = "chmod 664 $path 2>/dev/null; echo $maxFreqKHz > $path"
        val res = privilegeManager.exec(cmd)
        FrameXLog.i("CpuTuningController", "Set policy$policyId maxFreq=$maxFreqKHz -> success: ${res.isSuccess}")
        res.isSuccess
    }

    suspend fun setClusterGovernor(policyId: Int, governor: String): Boolean = withContext(Dispatchers.IO) {
        val path = "/sys/devices/system/cpu/cpufreq/policy$policyId/scaling_governor"
        val cmd = "chmod 664 $path 2>/dev/null; echo '$governor' > $path"
        val res = privilegeManager.exec(cmd)
        FrameXLog.i("CpuTuningController", "Set policy$policyId governor=$governor -> success: ${res.isSuccess}")
        res.isSuccess
    }

    suspend fun resetClusterToStock(policyId: Int): Boolean = withContext(Dispatchers.IO) {
        val path = "/sys/devices/system/cpu/cpufreq/policy$policyId"
        val hwMax = privilegeManager.readSystemFile("$path/cpuinfo_max_freq").trim().toLongOrNull()
        if (hwMax != null && hwMax > 0) {
            setClusterMaxFreq(policyId, hwMax)
        } else {
            false
        }
    }
}
