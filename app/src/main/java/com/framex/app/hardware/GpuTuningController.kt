package com.framex.app.hardware

import com.framex.app.privilege.PrivilegeManager
import com.framex.app.utils.FrameXLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuTuningController @Inject constructor(
    private val privilegeManager: PrivilegeManager
) {
    companion object {
        private val GOVERNOR_NAME = Regex("[A-Za-z0-9_.-]{1,32}")
        private const val BATCH_GPU_SCRIPT =
            "if [ -d /sys/class/kgsl/kgsl-3d0 ]; then " +
            "echo \"TYPE:KGSL\"; " +
            "echo -n \"cur:\"; cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null; " +
            "echo -n \"max:\"; cat /sys/class/kgsl/kgsl-3d0/max_gpuclk 2>/dev/null; " +
            "echo -n \"min:\"; cat /sys/class/kgsl/kgsl-3d0/min_clock_mhz 2>/dev/null; " +
            "echo -n \"avail_freq:\"; cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies 2>/dev/null; " +
            "echo -n \"gov:\"; cat /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null; " +
            "echo -n \"avail_gov:\"; cat /sys/class/kgsl/kgsl-3d0/devfreq/available_governors 2>/dev/null; " +
            "else " +
            "mali_dir=\$(ls -d /sys/class/misc/mali0/device/devfreq/*.mali 2>/dev/null | head -n 1); " +
            "if [ -z \"\$mali_dir\" ]; then mali_dir=\$(ls -d /sys/class/devfreq/*gpu* 2>/dev/null | head -n 1); fi; " +
            "if [ -n \"\$mali_dir\" ]; then " +
            "echo \"TYPE:MALI\"; " +
            "echo \"PATH:\$mali_dir\"; " +
            "echo -n \"model:\"; cat /sys/class/misc/mali0/device/gpuinfo 2>/dev/null; " +
            "echo -n \"cur:\"; cat \"\$mali_dir/cur_freq\" 2>/dev/null; " +
            "echo -n \"max:\"; cat \"\$mali_dir/max_freq\" 2>/dev/null; " +
            "echo -n \"min:\"; cat \"\$mali_dir/min_freq\" 2>/dev/null; " +
            "echo -n \"avail_freq:\"; cat \"\$mali_dir/available_frequencies\" 2>/dev/null; " +
            "echo -n \"gov:\"; cat \"\$mali_dir/governor\" 2>/dev/null; " +
            "echo -n \"avail_gov:\"; cat \"\$mali_dir/available_governors\" 2>/dev/null; " +
            "else echo \"TYPE:NONE\"; fi; " +
            "fi"
    }

    private var detectedBasePath: String = ""
    private var isMali: Boolean = false
    private var isKgsl: Boolean = false

    suspend fun probeGpu(): GpuTuningState = withContext(Dispatchers.IO) {
        val result = privilegeManager.exec(BATCH_GPU_SCRIPT, 3000L)
        if (!result.isSuccess || result.output.isBlank()) return@withContext defaultUnsupported()

        val lines = result.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val typeLine = lines.firstOrNull { it.startsWith("TYPE:") } ?: return@withContext defaultUnsupported()

        when (typeLine) {
            "TYPE:KGSL" -> {
                isKgsl = true
                isMali = false
                detectedBasePath = "/sys/class/kgsl/kgsl-3d0"
                parseKgsl(lines)
            }
            "TYPE:MALI" -> {
                isMali = true
                isKgsl = false
                val pathLine = lines.firstOrNull { it.startsWith("PATH:") }
                detectedBasePath = pathLine?.substringAfter("PATH:")?.trim() ?: ""
                parseMali(lines)
            }
            else -> defaultUnsupported()
        }
    }

    private fun parseKgsl(lines: List<String>): GpuTuningState {
        var curFreq = 0L
        var maxFreq = 0L
        var minFreq = 0L
        var availFreqs = emptyList<Long>()
        var curGov = ""
        var availGovs = emptyList<String>()

        for (line in lines) {
            when {
                line.startsWith("cur:") -> curFreq = (line.substringAfter("cur:").trim().toLongOrNull() ?: 0L) / 1000
                line.startsWith("max:") -> maxFreq = (line.substringAfter("max:").trim().toLongOrNull() ?: 0L) / 1000
                line.startsWith("min:") -> minFreq = (line.substringAfter("min:").trim().toLongOrNull() ?: 0L) * 1000
                line.startsWith("avail_freq:") -> {
                    availFreqs = line.substringAfter("avail_freq:").trim()
                        .split(" ", "\t").mapNotNull { it.trim().toLongOrNull() }.map { it / 1000 }.sorted()
                }
                line.startsWith("gov:") -> curGov = line.substringAfter("gov:").trim()
                line.startsWith("avail_gov:") -> {
                    availGovs = line.substringAfter("avail_gov:").trim()
                        .split(" ", "\t").map { it.trim() }.filter { it.isNotBlank() }
                }
            }
        }

        return GpuTuningState(
            model = "Qualcomm Adreno",
            isSupported = true,
            curFreqKHz = curFreq,
            minFreqKHz = minFreq,
            maxFreqKHz = maxFreq,
            availableFrequenciesKHz = availFreqs,
            availableGovernors = availGovs,
            currentGovernor = curGov
        )
    }

    private fun parseMali(lines: List<String>): GpuTuningState {
        var model = "ARM Mali GPU"
        var curFreq = 0L
        var maxFreq = 0L
        var minFreq = 0L
        var availFreqs = emptyList<Long>()
        var curGov = ""
        var availGovs = emptyList<String>()

        for (line in lines) {
            when {
                line.startsWith("model:") -> {
                    val m = line.substringAfter("model:").trim()
                    if (m.isNotEmpty()) model = m
                }
                line.startsWith("cur:") -> curFreq = (line.substringAfter("cur:").trim().toLongOrNull() ?: 0L) / 1000
                line.startsWith("max:") -> maxFreq = (line.substringAfter("max:").trim().toLongOrNull() ?: 0L) / 1000
                line.startsWith("min:") -> minFreq = (line.substringAfter("min:").trim().toLongOrNull() ?: 0L) / 1000
                line.startsWith("avail_freq:") -> {
                    availFreqs = line.substringAfter("avail_freq:").trim()
                        .split(" ", "\t").mapNotNull { it.trim().toLongOrNull() }.map { it / 1000 }.sorted()
                }
                line.startsWith("gov:") -> curGov = line.substringAfter("gov:").trim()
                line.startsWith("avail_gov:") -> {
                    availGovs = line.substringAfter("avail_gov:").trim()
                        .split(" ", "\t").map { it.trim() }.filter { it.isNotBlank() }
                }
            }
        }

        return GpuTuningState(
            model = model,
            isSupported = true,
            curFreqKHz = curFreq,
            minFreqKHz = minFreq,
            maxFreqKHz = maxFreq,
            availableFrequenciesKHz = availFreqs,
            availableGovernors = availGovs,
            currentGovernor = curGov
        )
    }

    private fun defaultUnsupported() = GpuTuningState(
        model = "Unsupported GPU driver",
        isSupported = false,
        curFreqKHz = 0L,
        minFreqKHz = 0L,
        maxFreqKHz = 0L,
        availableFrequenciesKHz = emptyList(),
        availableGovernors = emptyList(),
        currentGovernor = ""
    )

    private suspend fun writeAndVerify(path: String, value: String): Boolean {
        val res = privilegeManager.exec("echo '$value' > '$path' && cat '$path'")
        return res.isSuccess && res.output.trim() == value
    }

    suspend fun setGpuMaxFreq(maxFreqKHz: Long): Boolean = withContext(Dispatchers.IO) {
        if (detectedBasePath.isEmpty() || maxFreqKHz <= 0L) return@withContext false
        val path = when {
            isMali -> "$detectedBasePath/max_freq"
            isKgsl -> "$detectedBasePath/max_gpuclk"
            else -> return@withContext false
        }
        val ok = writeAndVerify(path, (maxFreqKHz * 1000).toString())
        FrameXLog.i("GpuTuningController", "Set GPU max freq to ${maxFreqKHz}KHz -> verified: $ok")
        ok
    }

    suspend fun setGpuGovernor(governor: String): Boolean = withContext(Dispatchers.IO) {
        if (detectedBasePath.isEmpty() || !GOVERNOR_NAME.matches(governor)) return@withContext false
        val govPath = when {
            isMali -> "$detectedBasePath/governor"
            isKgsl -> "$detectedBasePath/devfreq/governor"
            else -> return@withContext false
        }
        val ok = writeAndVerify(govPath, governor)
        FrameXLog.i("GpuTuningController", "Set GPU governor to $governor -> verified: $ok")
        ok
    }

}
