package com.framex.app.shizuku

import android.content.Context
import com.framex.app.privilege.scripts.PackageCommands
import com.framex.app.privilege.scripts.ThermalScripts
import com.framex.app.utils.FrameXLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class CommandRunnerService private constructor(
    private val context: Context?,
    @Suppress("UNUSED_PARAMETER") constructorMarker: Unit
) : ICommandRunner.Stub() {

    constructor() : this(null, Unit)

    constructor(context: Context) : this(context, Unit)

    /** Which mechanism successfully returned thermal sensor data. See [resolvedThermalStrategy]. */
    private enum class ThermalReadStrategy {
        REFLECTION,
        DUMPSYS,
        SYSFS
    }

    /**
     * Which read path successfully returned valid sensor data last time. Cached so that,
     * on devices where IThermalService reflection and dumpsys both fail (e.g. Qualcomm
     * legacy/Samsung Galaxy Tab A Lite hardware, see issue #57), we do not re-run the full
     * reflection scan and a doomed dumpsys attempt on every 1-second poll tick before
     * falling through to the sysfs read that actually works. Reset to null if the cached
     * strategy ever stops returning valid data, so behavior self-heals after an OTA or
     * permission change instead of getting stuck on a now-broken path.
     */
    @Volatile
    private var resolvedThermalStrategy: ThermalReadStrategy? = null

    /**
     * Executes a shell command with a strict wall-clock deadline and bounded output buffer.
     * Prevents wedged kernel nodes or hung subshells from permanently deadlocking the
     * single-connection Shizuku IPC mutex.
     */
    private fun executeBoundedProcess(
        command: String,
        timeoutMs: Long = COMMAND_TIMEOUT_MS
    ): Pair<Int, String> {
        var process: Process? = null
        return try {
            val proc = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            process = proc

            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        val buffer = CharArray(4096)
                        var read: Int
                        while (reader.read(buffer).also { read = it } != -1) {
                            val remaining = MAX_OUTPUT_CHARS - outputBuilder.length
                            if (remaining > 0) {
                                outputBuilder.append(buffer, 0, minOf(read, remaining))
                            } else {
                                // Drain remaining stream without growing heap allocation.
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Stream closed on process termination or timeout destroyForcibly.
                }
            }.apply {
                name = "CmdRunner-Reader"
                isDaemon = true
                start()
            }

            val completed = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                proc.destroyForcibly()
                readerThread.join(200L)
                FrameXLog.w("Command timed out after ${timeoutMs}ms: $command", tag = TAG)
                return COMMAND_TIMED_OUT to "Error executing command: Timed out after ${timeoutMs}ms"
            }

            readerThread.join(500L)
            val exitCode = proc.exitValue()
            exitCode to outputBuilder.toString().trim()
        } catch (e: Exception) {
            process?.destroyForcibly()
            FrameXLog.e("Error executing command: $command", e, tag = TAG)
            COMMAND_EXECUTION_FAILED to "Error executing command: ${e.message}"
        }
    }

    override fun executeCommand(command: String): String {
        val (_, output) = executeBoundedProcess(command)
        return output
    }

    override fun executeCommandWithExitCode(command: String): Int {
        val (exitCode, _) = executeBoundedProcess(command)
        return exitCode
    }

    override fun executeCommandWithResult(command: String): CommandResult {
        val (exitCode, output) = executeBoundedProcess(command)
        return CommandResult().apply {
            this.output = output
            this.exitCode = exitCode
        }
    }

    override fun readProcStat(): String {
        return try {
            java.io.File("/proc/stat").readText()
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.w("Direct /proc/stat read failed, falling back to shell", e)
            executeCommand("cat /proc/stat")
        }
    }

    /**
     * Single-IPC combined CPU telemetry collector.
     * Reads /proc/stat (total + per-core lines) and /sys/devices/system/cpu/cpufreq/policy*
     * (related_cpus, cpuinfo_max_freq, scaling_cur_freq) directly in-process via JVM File IO
     * inside the Shizuku shell UID (2000) runner without forking any shell processes.
     */
    override fun readCpuTelemetry(): String {
        val sb = StringBuilder(1024)
        try {
            val statFile = java.io.File("/proc/stat")
            if (statFile.exists() && statFile.canRead()) {
                BufferedReader(java.io.FileReader(statFile), 1024).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if (line.startsWith("cpu")) {
                            sb.append("STAT:").append(line.trim()).append('\n')
                        } else if (sb.isNotEmpty()) {
                            break
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.w("readCpuTelemetry /proc/stat read failed", e)
        }

        try {
            val cpufreqDir = java.io.File("/sys/devices/system/cpu/cpufreq")
            val policyDirs = cpufreqDir.listFiles { file ->
                file.isDirectory && file.name.startsWith("policy")
            }
            if (!policyDirs.isNullOrEmpty()) {
                for (policyDir in policyDirs) {
                    val policyId = policyDir.name.removePrefix("policy")
                    val related = readFirstLineFast(java.io.File(policyDir, "related_cpus"))
                        .ifEmpty { readFirstLineFast(java.io.File(policyDir, "affected_cpus")) }
                        .ifEmpty { policyId }
                    val maxFreq = readFirstLineFast(java.io.File(policyDir, "cpuinfo_max_freq"))
                        .ifEmpty { readFirstLineFast(java.io.File(policyDir, "scaling_max_freq")) }
                    val curFreq = readFirstLineFast(java.io.File(policyDir, "scaling_cur_freq"))
                        .ifEmpty { readFirstLineFast(java.io.File(policyDir, "cpuinfo_cur_freq")) }

                    if (maxFreq.isNotEmpty() || curFreq.isNotEmpty()) {
                        sb.append("POLICY:")
                            .append(policyId).append(':')
                            .append(related).append(':')
                            .append(maxFreq.ifEmpty { "0" }).append(':')
                            .append(curFreq.ifEmpty { "0" }).append('\n')
                    }
                }
            } else {
                val cpuRoot = java.io.File("/sys/devices/system/cpu")
                for (i in 0..15) {
                    val freqDir = java.io.File(cpuRoot, "cpu$i/cpufreq")
                    if (!freqDir.exists()) continue
                    val maxFreq = readFirstLineFast(java.io.File(freqDir, "cpuinfo_max_freq"))
                        .ifEmpty { readFirstLineFast(java.io.File(freqDir, "scaling_max_freq")) }
                    val curFreq = readFirstLineFast(java.io.File(freqDir, "scaling_cur_freq"))
                    if (maxFreq.isNotEmpty() || curFreq.isNotEmpty()) {
                        sb.append("CORE:")
                            .append(i).append(':')
                            .append(maxFreq.ifEmpty { "0" }).append(':')
                            .append(curFreq.ifEmpty { "0" }).append('\n')
                    }
                }
            }
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.w("readCpuTelemetry cpufreq read failed", e)
        }

        return sb.toString()
    }

    private fun readFirstLineFast(file: java.io.File): String {
        return try {
            if (file.exists() && file.canRead()) {
                BufferedReader(java.io.FileReader(file), 64).use { it.readLine()?.trim().orEmpty() }
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    override fun getThermalTemperatures(): String {
        resolvedThermalStrategy?.let { cached ->
            val cachedResult = readUsingStrategy(cached)
            if (cachedResult != null) return appendThermalStatusProperty(cachedResult)
            // Cached strategy stopped working (hardware state changed at runtime) --
            // clear it and fall through to full re-discovery below.
            resolvedThermalStrategy = null
        }

        readViaReflection()?.let {
            resolvedThermalStrategy = ThermalReadStrategy.REFLECTION
            return appendThermalStatusProperty(it)
        }
        readViaDumpsys()?.let {
            resolvedThermalStrategy = ThermalReadStrategy.DUMPSYS
            return appendThermalStatusProperty(it)
        }
        readViaSysfs()?.let {
            resolvedThermalStrategy = ThermalReadStrategy.SYSFS
            return appendThermalStatusProperty(it)
        }

        // All strategies failed validation: still return the raw dumpsys dump so callers
        // (ThermalMonitor's parser) can surface a ParseFailed status rather than getting
        // an empty string, matching prior behavior.
        return appendThermalStatusProperty(readViaDumpsys(requireValid = false).orEmpty())
    }

    @Volatile
    private var cachedSystemPropertiesGet: java.lang.reflect.Method? = null

    private fun readSystemPropertyFast(key: String): String {
        return try {
            val method = cachedSystemPropertiesGet ?: Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .also { cachedSystemPropertiesGet = it }
            (method.invoke(null, key) as? String)?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun appendThermalStatusProperty(payload: String): String {
        val prop = readSystemPropertyFast("sys.thermal.status")
            .ifEmpty { readSystemPropertyFast("vendor.sys.thermal.status") }
        return if (prop.isNotEmpty() && prop.all { it.isDigit() }) {
            "$payload\nsys.thermal.status: $prop\n"
        } else {
            payload
        }
    }

    private fun readUsingStrategy(strategy: ThermalReadStrategy): String? = when (strategy) {
        ThermalReadStrategy.REFLECTION -> ReflectionThermalReader.read()
        ThermalReadStrategy.DUMPSYS -> readViaDumpsys()
        ThermalReadStrategy.SYSFS -> readViaSysfs()
    }

    /**
     * Reads via `dumpsys thermalservice`. Returns null when the dump contains no
     * recognizable sensor data, so callers can fall through to the sysfs strategy.
     * [requireValid] = false bypasses that check for the final "give the caller something
     * to parse-fail on" fallback in [getThermalTemperatures].
     */
    private fun readViaDumpsys(requireValid: Boolean = true): String? {
        val dump = executeCommand(ThermalScripts.DUMPSYS_COMMAND)
        if (!requireValid) return dump
        return if (ThermalScripts.dumpHasSensorData(dump)) dump else null
    }

    private data class CachedThermalPath(val typeFile: java.io.File, val tempFile: java.io.File)
    private var cachedThermalPaths: List<CachedThermalPath>? = null

    /**
     * Reads raw zone temperatures directly from /sys/class/thermal on devices where
     * neither the IThermalService reflection path nor dumpsys thermalservice expose any
     * sensor data (HAL not ready / legacy Qualcomm builds).
     *
     * Optimizations applied:
     * 1. Direct JVM File.readText() — eliminates shell executions and process forks completely.
     * 2. Targeted fallback — if direct reading fails due to permissions, executes a single
     *    targeted cat command only for the cached paths rather than scanning 80+ zones.
     * 3. Dynamic path discovery & caching — discovers sensor paths once and caches them.
     */
    private fun readViaSysfs(): String? {
        val cached = cachedThermalPaths
        if (!cached.isNullOrEmpty()) {
            val sb = StringBuilder()
            var successCount = 0
            var permissionDeniedCount = 0
            for (item in cached) {
                try {
                    if (item.typeFile.canRead() && item.tempFile.canRead()) {
                        val type = item.typeFile.readText().trim()
                        val temp = item.tempFile.readText().trim()
                        if (type.isNotEmpty() && temp.isNotEmpty()) {
                            sb.append(type).append(":").append(temp).append("\n")
                            successCount++
                        }
                    } else {
                        permissionDeniedCount++
                    }
                } catch (e: Exception) {
                    permissionDeniedCount++
                }
            }

            if (successCount > 0) {
                return sb.toString()
            }

            if (permissionDeniedCount > 0) {
                val cmdSb = StringBuilder("sh -c '")
                for (item in cached) {
                    cmdSb.append("echo \"\$(cat ").append(item.typeFile.absolutePath)
                        .append(" 2>/dev/null):\$(cat ").append(item.tempFile.absolutePath)
                        .append(" 2>/dev/null)\"; ")
                }
                cmdSb.append("'")
                val shellResult = executeCommand(cmdSb.toString())
                if (shellResult.isNotBlank() && shellResult.lines().any { it.contains(":") }) {
                    return shellResult
                }
            }

            // Self-healing: if cached paths failed to yield data, reset cache to rediscover
            cachedThermalPaths = null
        }

        // Fast-path discovery: probe /sys/class/thermal directly via JVM File inside shell UID
        // before spawning a subshell loop.
        val thermalDir = java.io.File("/sys/class/thermal")
        val zoneDirs = thermalDir.listFiles { dir, name ->
            dir.isDirectory && name.startsWith("thermal_zone")
        }
        if (!zoneDirs.isNullOrEmpty()) {
            val discoveredPaths = mutableListOf<CachedThermalPath>()
            val directSb = StringBuilder()
            for (dir in zoneDirs) {
                val typeFile = java.io.File(dir, "type")
                val tempFile = java.io.File(dir, "temp")
                if (typeFile.exists() && tempFile.exists()) {
                    discoveredPaths.add(CachedThermalPath(typeFile, tempFile))
                    val type = readFirstLineFast(typeFile)
                    val temp = readFirstLineFast(tempFile)
                    if (type.isNotEmpty() && temp.isNotEmpty()) {
                        directSb.append(type).append(':').append(temp).append('\n')
                    }
                }
            }
            val coolingDirs = thermalDir.listFiles { dir, name ->
                dir.isDirectory && name.startsWith("cooling_device")
            }.orEmpty()
            for (cDir in coolingDirs) {
                val cType = readFirstLineFast(java.io.File(cDir, "type"))
                val cState = readFirstLineFast(java.io.File(cDir, "cur_state"))
                if (cType.isNotEmpty() && cState.isNotEmpty()) {
                    directSb.append("cooling: ").append(cType).append(' ').append(cState).append('\n')
                }
            }
            if (discoveredPaths.isNotEmpty()) {
                cachedThermalPaths = discoveredPaths
            }
            if (directSb.isNotEmpty()) {
                return directSb.toString()
            }
        }

        val sysfsDump = executeCommand(ThermalScripts.SYSFS_COMMAND)
        if (sysfsDump.isNotBlank() && ThermalScripts.sysfsHasSensorData(sysfsDump)) {
            return sysfsDump
        }
        return null
    }

    override fun suspendPackages(packageNames: Array<out String>?, suspended: Boolean): SuspendResult {
        val outcome = PackageCommands.applySuspend(packageNames.orEmpty().toList(), suspended) { command ->
            executeBoundedProcess(command)
        }
        return SuspendResult().apply {
            this.failedPackages = outcome.failedPackages.toTypedArray()
            this.successCount = outcome.successCount
        }
    }

    override fun setAppOpMode(packageNames: Array<out String>?, opCode: Int, mode: Int): Int {
        if (packageNames.isNullOrEmpty()) return 0
        val (_, output) = executeBoundedProcess(PackageCommands.appOpBatch(packageNames.toList(), opCode, mode))
        return PackageCommands.countAppOpSuccesses(output)
    }

    override fun destroy() {
        exitProcess(0)
    }

    private companion object {
        const val TAG = "CmdRunner"
        const val COMMAND_EXECUTION_FAILED = -1
        const val COMMAND_TIMED_OUT = -2
        const val COMMAND_TIMEOUT_MS = 10_000L
        const val MAX_OUTPUT_CHARS = 256 * 1024
    }
}
