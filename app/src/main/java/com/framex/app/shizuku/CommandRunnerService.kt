package com.framex.app.shizuku

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class CommandRunnerService(private val context: Context) : ICommandRunner.Stub() {

    override fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            "Error executing command: ${e.message}"
        }
    }

    override fun getThermalTemperatures(): String {
        return try {
            val binderClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = binderClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "thermalservice") as? android.os.IBinder
            if (binder != null) {
                val stubClass = Class.forName("android.os.IThermalService\$Stub")
                val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                val service = asInterfaceMethod.invoke(null, binder)
                if (service != null) {
                    val getCurrentTempsMethod = service.javaClass.getMethod(
                        "getCurrentTemperatures",
                        Boolean::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                    val temps = getCurrentTempsMethod.invoke(service, false, 0) as? Array<*>
                    if (temps != null && temps.isNotEmpty()) {
                        val sb = StringBuilder()
                        for (temp in temps) {
                            if (temp != null) {
                                val fields = temp.javaClass.declaredFields
                                var name = ""
                                var value = 0f
                                var type = 0
                                for (f in fields) {
                                    f.isAccessible = true
                                    when (f.name.lowercase()) {
                                        "mname", "name" -> name = f.get(temp) as? String ?: ""
                                        "mvalue", "value" -> value = (f.get(temp) as? Number)?.toFloat() ?: 0f
                                        "mtype", "type" -> type = (f.get(temp) as? Number)?.toInt() ?: 0
                                    }
                                }
                                sb.append("Temperature{mValue=").append(value)
                                  .append(", mType=").append(type)
                                  .append(", mName=").append(name).append("}\n")
                            }
                        }
                        if (sb.isNotEmpty()) return sb.toString()
                    }
                }
            }
            executeCommand("dumpsys thermalservice")
        } catch (e: Exception) {
            executeCommand("dumpsys thermalservice")
        }
    }

    override fun suspendPackages(packageNames: Array<out String>?, suspended: Boolean): Int {
        if (packageNames.isNullOrEmpty()) return 0
        var successCount = 0
        try {
            val pm = context.packageManager
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
}
