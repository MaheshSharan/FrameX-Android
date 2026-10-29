package com.framex.app.shizuku

import com.framex.app.utils.FrameXLog

/**
 * Shizuku-only thermal strategy. Runs inside the Shizuku user service, where the hidden
 * `IThermalService` binder is reachable; a plain root shell cannot use it and relies on
 * dumpsys and sysfs instead.
 */
internal object ReflectionThermalReader {

    /**
     * Reads sensor temperatures via reflection into the hidden android.os.IThermalService
     * Binder, matching whichever getCurrentTemperatures* overload the device's Android
     * version exposes. Returns null (rather than an empty/invalid string) when no method
     * yields usable sensor data, so callers can fall through to the next strategy.
     */
    fun read(): String? {
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
            FrameXLog.w("reflection read failed: ${e.message}", null, "CmdRunner")
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
}
