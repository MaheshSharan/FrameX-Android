package com.framex.app.hardware

import com.framex.app.privilege.PrivilegeManager
import com.framex.app.privilege.PrivilegeMode
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
        private const val CPUFREQ_ROOT = "/sys/devices/system/cpu/cpufreq"
        private val GOVERNOR_NAME = Regex("[A-Za-z0-9_.-]{1,32}")
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

    /**
     * Writes the value and reads it back; success means the kernel now reports exactly that
     * value. A write that is accepted and later overridden by a system service is outside what
     * this call can see.
     */
    private suspend fun writeOnce(path: String, value: String): Boolean {
        val res = privilegeManager.exec("echo '$value' > '$path' && cat '$path'")
        return res.isSuccess && res.output.trim() == value
    }

    /**
     * Writes and reads back. Under Root the node is first made writable (a previous lock or an
     * OEM script may have set it read-only); [lock] then makes it read-only again so system
     * services cannot lower it. The shell user cannot change modes, so Shizuku never tries.
     */
    private suspend fun writeAndVerify(path: String, value: String, lock: Boolean = false): Boolean {
        val root = privilegeManager.currentMode.value == PrivilegeMode.ROOT
        if (root) privilegeManager.exec("chmod 664 '$path'")
        val ok = writeOnce(path, value)
        if (root) privilegeManager.exec(if (ok && lock) "chmod 444 '$path'" else "chmod 664 '$path'")
        return ok
    }

    private val lockTargets =
        "$CPUFREQ_ROOT/policy*/scaling_max_freq $CPUFREQ_ROOT/policy*/scaling_governor"

    /** Root only: makes frequency and governor nodes read-only so system services cannot change them. */
    suspend fun lockAll(): Boolean = withContext(Dispatchers.IO) {
        privilegeManager.currentMode.value == PrivilegeMode.ROOT &&
            privilegeManager.exec("chmod 444 $lockTargets").isSuccess
    }

    /** Root only: makes the nodes writable again. */
    suspend fun unlockAll(): Boolean = withContext(Dispatchers.IO) {
        privilegeManager.currentMode.value == PrivilegeMode.ROOT &&
            privilegeManager.exec("chmod 664 $lockTargets").isSuccess
    }

    /** True when every frequency and governor node has no owner write bit. */
    suspend fun isLocked(): Boolean = withContext(Dispatchers.IO) {
        val modes = privilegeManager.exec("stat -c %a $lockTargets 2>/dev/null")
        modes.isSuccess && modes.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .let { l -> l.isNotEmpty() && l.all { it.length >= 3 && (it[it.length - 3] - '0') < 6 } }
    }

    suspend fun setClusterMaxFreq(policyId: Int, maxFreqKHz: Long, lock: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (policyId < 0 || maxFreqKHz <= 0L) return@withContext false
        val path = "$CPUFREQ_ROOT/policy$policyId/scaling_max_freq"
        val ok = writeAndVerify(path, maxFreqKHz.toString(), lock)
        FrameXLog.i("CpuTuningController", "Set policy$policyId maxFreq=$maxFreqKHz -> verified: $ok")
        ok
    }

    suspend fun setClusterGovernor(policyId: Int, governor: String): Boolean = withContext(Dispatchers.IO) {
        if (policyId < 0 || !GOVERNOR_NAME.matches(governor)) return@withContext false
        val path = "$CPUFREQ_ROOT/policy$policyId/scaling_governor"
        val ok = writeAndVerify(path, governor)
        FrameXLog.i("CpuTuningController", "Set policy$policyId governor=$governor -> verified: $ok")
        ok
    }

    suspend fun resetClusterToStock(policyId: Int): Boolean = withContext(Dispatchers.IO) {
        val path = "$CPUFREQ_ROOT/policy$policyId"
        val hwMax = privilegeManager.readSystemFile("$path/cpuinfo_max_freq").trim().toLongOrNull()
        if (hwMax != null && hwMax > 0) {
            setClusterMaxFreq(policyId, hwMax, lock = false)
        } else {
            false
        }
    }
}
