package com.framex.app.privilege.scripts

/**
 * Shell strategies for reading device temperatures without access to the hidden thermal
 * service binder. The output formats match what the Shizuku runner returns for the same
 * strategies, so `ThermalMonitor` parses both identically.
 */
object ThermalScripts {

    const val DUMPSYS_COMMAND = "dumpsys thermalservice"

    /** `type:temp` lines for every zone followed by `cooling:` lines for cooling devices. */
    val SYSFS_COMMAND: String = """
        for z in /sys/class/thermal/thermal_zone*; do
          echo "${'$'}(cat ${'$'}z/type 2>/dev/null):${'$'}(cat ${'$'}z/temp 2>/dev/null)"
        done
        for d in /sys/class/thermal/cooling_device*; do
          echo "cooling: ${'$'}(cat ${'$'}d/type 2>/dev/null) ${'$'}(cat ${'$'}d/cur_state 2>/dev/null)"
        done
    """.trimIndent()

    /** Prints the numeric thermal status property, or nothing when it is unset. */
    val STATUS_COMMAND: String = """
        p=${'$'}(getprop sys.thermal.status)
        [ -z "${'$'}p" ] && p=${'$'}(getprop vendor.sys.thermal.status)
        case "${'$'}p" in ''|*[!0-9]*) ;; *) echo "sys.thermal.status: ${'$'}p" ;; esac
    """.trimIndent()

    fun dumpHasSensorData(dump: String): Boolean =
        dump.contains("Temperature{") || dump.contains("mValue=")

    fun sysfsHasSensorData(dump: String): Boolean = dump.lines().any {
        it.contains(":") && it.substringBefore(":").isNotBlank() && it.substringAfter(":").isNotBlank()
    }
}
