package com.framex.app.gaming.catalog

/**
 * Builds one shell script that runs independent commands in parallel groups and reports each
 * command's failure and duration. Every `content` call starts its own JVM, so running a long
 * list one after another is slow; the commands here write different keys and do not depend on
 * each other.
 */
object ParallelShell {

    const val GROUP_SIZE = 8

    data class Report(val failedIndexes: List<Int>, val durationsMs: Map<Int, Long>)

    fun script(commands: List<String>): String =
        commands.withIndex().chunked(GROUP_SIZE).joinToString("\n") { group ->
            group.joinToString("\n") { (index, command) ->
                "{ t=\$(date +%s%N); { $command; } || echo FRAMEX_FAIL:$index; " +
                    "echo FRAMEX_T:$index:\$(( (\$(date +%s%N) - t) / 1000000 )); } &"
            } + "\nwait"
        }

    fun parse(output: String): Report {
        val failed = mutableListOf<Int>()
        val durations = mutableMapOf<Int, Long>()
        for (line in output.lineSequence()) {
            when {
                line.startsWith(FAIL_PREFIX) ->
                    line.removePrefix(FAIL_PREFIX).trim().toIntOrNull()?.let(failed::add)
                line.startsWith(TIME_PREFIX) -> parseDuration(line)?.let { durations[it.first] = it.second }
            }
        }
        return Report(failed.sorted(), durations)
    }

    private fun parseDuration(line: String): Pair<Int, Long>? {
        val parts = line.removePrefix(TIME_PREFIX).split(':')
        val index = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val millis = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: return null
        return index to millis
    }

    private const val FAIL_PREFIX = "FRAMEX_FAIL:"
    private const val TIME_PREFIX = "FRAMEX_T:"
}
