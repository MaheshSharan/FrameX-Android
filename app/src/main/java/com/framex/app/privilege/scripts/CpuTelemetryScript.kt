package com.framex.app.privilege.scripts

/**
 * Shell version of `CommandRunnerService.readCpuTelemetry`, for backends that cannot run
 * code inside the privileged process. It prints exactly the same line formats
 * (`STAT:`, `POLICY:id:related:max:cur`, `CORE:i:max:cur`) so the parsers need no changes.
 * Only shell builtins are used inside the loops to avoid forking on every sample.
 */
object CpuTelemetryScript {

    val COMMAND: String = """
        while read -r l; do
          case "${'$'}l" in
            cpu*) echo "STAT:${'$'}l" ;;
            *) break ;;
          esac
        done < /proc/stat
        set -- /sys/devices/system/cpu/cpufreq/policy*
        if [ -d "${'$'}1" ]; then
          for d in "${'$'}@"; do
            id=${'$'}{d##*policy}
            rel=''; max=''; cur=''
            read -r rel 2>/dev/null < "${'$'}d/related_cpus"
            [ -z "${'$'}rel" ] && read -r rel 2>/dev/null < "${'$'}d/affected_cpus"
            [ -z "${'$'}rel" ] && rel=${'$'}id
            read -r max 2>/dev/null < "${'$'}d/cpuinfo_max_freq"
            [ -z "${'$'}max" ] && read -r max 2>/dev/null < "${'$'}d/scaling_max_freq"
            read -r cur 2>/dev/null < "${'$'}d/scaling_cur_freq"
            [ -z "${'$'}cur" ] && read -r cur 2>/dev/null < "${'$'}d/cpuinfo_cur_freq"
            if [ -n "${'$'}max${'$'}cur" ]; then echo "POLICY:${'$'}id:${'$'}rel:${'$'}{max:-0}:${'$'}{cur:-0}"; fi
          done
        else
          i=0
          while [ ${'$'}i -le 15 ]; do
            f=/sys/devices/system/cpu/cpu${'$'}i/cpufreq
            if [ -d "${'$'}f" ]; then
              max=''; cur=''
              read -r max 2>/dev/null < "${'$'}f/cpuinfo_max_freq"
              [ -z "${'$'}max" ] && read -r max 2>/dev/null < "${'$'}f/scaling_max_freq"
              read -r cur 2>/dev/null < "${'$'}f/scaling_cur_freq"
              if [ -n "${'$'}max${'$'}cur" ]; then echo "CORE:${'$'}i:${'$'}{max:-0}:${'$'}{cur:-0}"; fi
            fi
            i=${'$'}((i + 1))
          done
        fi
    """.trimIndent()
}
