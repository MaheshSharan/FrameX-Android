package com.framex.app.privilege

/**
 * Measures what a backend can really do. Nothing here changes device state: the sysfs check
 * writes back the value it just read.
 */
internal object CapabilityProbe {

    /** Exit status 0 only when the first cpufreq policy accepted an unchanged write. */
    const val SYSFS_WRITE_PROBE: String = """
        p=${'$'}(ls -d /sys/devices/system/cpu/cpufreq/policy* 2>/dev/null | head -n 1)
        [ -n "${'$'}p" ] || exit 1
        v=${'$'}(cat "${'$'}p/scaling_max_freq") || exit 1
        echo "${'$'}v" > "${'$'}p/scaling_max_freq" || exit 1
        [ "${'$'}(cat "${'$'}p/scaling_max_freq")" = "${'$'}v" ]
    """

    suspend fun run(backend: PrivilegeBackend): PrivilegeCapabilities {
        val uid = backend.exec("id -u").takeIf { it.isSuccess }?.output?.trim()?.toIntOrNull() ?: UNKNOWN_UID
        val context = backend.exec("id -Z").takeIf { it.isSuccess }?.output?.trim()?.takeIf { it.isNotEmpty() }
        val procStat = backend.readCpuTelemetry().let { it.isSuccess && it.output.contains("STAT:") }
        val sysfs = backend.exec(SYSFS_WRITE_PROBE.trimIndent()).isSuccess
        return PrivilegeCapabilities(uid, context, procStat, sysfs)
    }

    private const val UNKNOWN_UID = -1
}
