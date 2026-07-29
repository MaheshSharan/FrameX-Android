package com.framex.app.shizuku

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
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

    override fun executeCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText().trim()
            }
            process.waitFor()
            output
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("Error executing command: $command", e)
            "Error executing command: ${e.message}"
        }
    }

    override fun executeCommandWithExitCode(command: String): Int {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }
            process.waitFor()
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.e("Error executing command: $command", e)
            COMMAND_EXECUTION_FAILED
        }
    }

    override fun getThermalTemperatures(): String {
        resolvedThermalStrategy?.let { cached ->
            val cachedResult = readUsingStrategy(cached)
            if (cachedResult != null) return cachedResult
            // Cached strategy stopped working (hardware state changed at runtime) --
            // clear it and fall through to full re-discovery below.
            resolvedThermalStrategy = null
        }

        readViaReflection()?.let {
            resolvedThermalStrategy = ThermalReadStrategy.REFLECTION
            return it
        }
        readViaDumpsys()?.let {
            resolvedThermalStrategy = ThermalReadStrategy.DUMPSYS
            return it
        }
        readViaSysfs()?.let {
            resolvedThermalStrategy = ThermalReadStrategy.SYSFS
            return it
        }

        // All strategies failed validation: still return the raw dumpsys dump so callers
        // (ThermalMonitor's parser) can surface a ParseFailed status rather than getting
        // an empty string, matching prior behavior.
        return readViaDumpsys(requireValid = false).orEmpty()
    }

    private fun readUsingStrategy(strategy: ThermalReadStrategy): String? = when (strategy) {
        ThermalReadStrategy.REFLECTION -> readViaReflection()
        ThermalReadStrategy.DUMPSYS -> readViaDumpsys()
        ThermalReadStrategy.SYSFS -> readViaSysfs()
    }

    /**
     * Reads sensor temperatures via reflection into the hidden android.os.IThermalService
     * Binder, matching whichever getCurrentTemperatures* overload the device's Android
     * version exposes. Returns null (rather than an empty/invalid string) when no method
     * yields usable sensor data, so callers can fall through to the next strategy.
     */
    private fun readViaReflection(): String? {
        return try {
            val binderClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = binderClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "thermalservice") as? android.os.IBinder
                ?: return null
            val stubClass = Class.forName("android.os.IThermalService\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
            val service = asInterfaceMethod.invoke(null, binder) ?: return null

            val temps = invokeTemperatureGetter(service) ?: return null
            parseTemperatureObjects(temps)
        } catch (e: Exception) {
            com.framex.app.utils.FrameXLog.w("readViaReflection failed: ${e.message}", null, "CmdRunner")
            null
        }
    }

    /**
     * Tries every IThermalService method whose name contains "Temperature" with each of
     * the parameter-count overloads seen across Android versions (0, 1, and 2 args),
     * since the exact signature (getCurrentTemperatures, getCurrentTemperaturesWithType,
     * etc.) varies by OEM and API level.
     */
    private fun invokeTemperatureGetter(service: Any): Array<*>? {
        val candidateMethods = service.javaClass.methods.filter {
            it.name.contains("Temperature", ignoreCase = true)
        }
        for (method in candidateMethods) {
            val result = runCatching { invokeWithBestGuessArgs(method, service) }.getOrNull()
            when {
                result is Array<*> && result.isNotEmpty() -> return result
                result is List<*> && result.isNotEmpty() -> return result.toTypedArray()
            }
        }
        return null
    }

    private fun invokeWithBestGuessArgs(method: java.lang.reflect.Method, service: Any): Any? {
        val paramTypes = method.parameterTypes
        return when (paramTypes.size) {
            0 -> method.invoke(service)
            1 -> {
                val isBoolean = paramTypes[0] == Boolean::class.javaPrimitiveType || paramTypes[0] == Boolean::class.java
                if (isBoolean) method.invoke(service, false) else method.invoke(service, 0)
            }
            2 -> {
                val (p0, p1) = paramTypes[0] to paramTypes[1]
                when {
                    p0 == Boolean::class.javaPrimitiveType && p1 == Int::class.javaPrimitiveType -> method.invoke(service, false, 0)
                    p0 == Int::class.javaPrimitiveType && p1 == Boolean::class.javaPrimitiveType -> method.invoke(service, 0, false)
                    else -> method.invoke(service, false, 0)
                }
            }
            else -> null
        }
    }

    /**
     * Converts the array of opaque android.os.Temperature parcelables returned by
     * IThermalService into the "Temperature{mValue=.., mType=.., mName=..}" line format
     * ThermalMonitor's parser already understands (matching dumpsys's own output shape).
     * Tries public getters first, falling back to declared-field reflection for OEM
     * builds whose Temperature class lacks them.
     */
    private fun parseTemperatureObjects(temps: Array<*>): String? {
        val builder = StringBuilder()
        var hasValidData = false
        for (temp in temps) {
            if (temp == null) continue
            var name = runCatching { temp.javaClass.getMethod("getName").invoke(temp) as? String }.getOrNull() ?: ""
            var value = runCatching { (temp.javaClass.getMethod("getValue").invoke(temp) as? Number)?.toFloat() }.getOrNull() ?: 0f
            var type = runCatching { (temp.javaClass.getMethod("getType").invoke(temp) as? Number)?.toInt() }.getOrNull() ?: 0

            if (value == 0f && name.isBlank()) {
                for (field in temp.javaClass.declaredFields) {
                    field.isAccessible = true
                    when (field.name.lowercase()) {
                        "mname", "name" -> name = field.get(temp) as? String ?: ""
                        "mvalue", "value" -> value = (field.get(temp) as? Number)?.toFloat() ?: 0f
                        "mtype", "type" -> type = (field.get(temp) as? Number)?.toInt() ?: 0
                    }
                }
            }

            if (value > 0f || name.isNotBlank()) hasValidData = true
            builder.append("Temperature{mValue=").append(value)
                .append(", mType=").append(type)
                .append(", mName=").append(name).append("}\n")
        }
        return if (hasValidData && builder.isNotEmpty()) builder.toString() else null
    }

    /**
     * Reads via `dumpsys thermalservice`. Returns null when the dump contains no
     * recognizable sensor data, so callers can fall through to the sysfs strategy.
     * [requireValid] = false bypasses that check for the final "give the caller something
     * to parse-fail on" fallback in [getThermalTemperatures].
     */
    private fun readViaDumpsys(requireValid: Boolean = true): String? {
        val dump = executeCommand("dumpsys thermalservice")
        if (!requireValid) return dump
        return if (dump.contains("Temperature{") || dump.contains("mValue=")) dump else null
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

        val sysfsDump = executeCommand(
            "sh -c 'for z in /sys/class/thermal/thermal_zone*; do echo \"\$(cat \$z/type 2>/dev/null):\$(cat \$z/temp 2>/dev/null)\"; done'"
        )
        val validLines = sysfsDump.lines().filter {
            it.contains(":") && it.substringBefore(":").isNotBlank() && it.substringAfter(":").isNotBlank()
        }

        if (sysfsDump.isNotBlank() && validLines.isNotEmpty()) {
            val discoveredPaths = mutableListOf<CachedThermalPath>()
            val thermalDir = java.io.File("/sys/class/thermal")
            val zoneDirs = thermalDir.listFiles { dir, name -> dir.isDirectory && name.startsWith("thermal_zone") } ?: emptyArray()
            for (dir in zoneDirs) {
                val typeFile = java.io.File(dir, "type")
                val tempFile = java.io.File(dir, "temp")
                if (typeFile.exists() && tempFile.exists()) {
                    discoveredPaths.add(CachedThermalPath(typeFile, tempFile))
                }
            }
            if (discoveredPaths.isNotEmpty()) {
                cachedThermalPaths = discoveredPaths
            }
            return sysfsDump
        }
        return null
    }

    override fun suspendPackages(packageNames: Array<out String>?, suspended: Boolean): Int {
        if (packageNames.isNullOrEmpty()) return 0
        var successCount = 0
        try {
            val pm = context?.packageManager ?: throw IllegalStateException("UserService context unavailable")
            val method = pm.javaClass.getMethod(
                "setPackagesSuspended",
                Array<String>::class.java,
                Boolean::class.javaPrimitiveType,
                android.os.PersistableBundle::class.java,
                android.os.PersistableBundle::class.java,
                String::class.java
            )
            val unfailed = method.invoke(pm, packageNames, suspended, null, null, "com.framex.app") as? Array<*>
            successCount = packageNames.size - (unfailed?.size ?: 0)
        } catch (e: Exception) {
            for (pkg in packageNames) {
                val cmd = if (suspended) "pm suspend --user 0 $pkg" else "pm unsuspend --user 0 $pkg"
                executeCommand(cmd)
                successCount++
            }
        }
        return successCount
    }

    override fun setAppOpMode(packageNames: Array<out String>?, opCode: Int, mode: Int): Int {
        if (packageNames.isNullOrEmpty()) return 0
        var count = 0
        val modeStr = when (mode) {
            1 -> "ignore"
            2 -> "deny"
            else -> "allow"
        }
        for (pkg in packageNames) {
            executeCommand("cmd appops set $pkg $opCode $modeStr")
            count++
        }
        return count
    }

    override fun destroy() {
        exitProcess(0)
    }

    private companion object {
        const val COMMAND_EXECUTION_FAILED = -1
    }
}
