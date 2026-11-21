package com.framex.app.metrics

/**
 * Pure parser for the busiest process line in `dumpsys cpuinfo` output.
 * Pure parser with no Android dependencies.
 */
object CpuInfoTopParser {

    data class TopProcess(val name: String, val cpuPercent: Float)

    // e.g. "  23% 1234/com.example.app: 15% user + 8% kernel"
    private val lineRegex = Regex("""^\s*([0-9.]+)%\s+\d+/([^:]+):""")

    fun parseTop(output: String): TopProcess? {
        return parseTopProcesses(output, limit = 1).firstOrNull()
    }

    fun parseTopProcesses(output: String, limit: Int = 5): List<TopProcess> {
        if (output.isBlank()) return emptyList()
        val results = mutableListOf<TopProcess>()
        for (line in output.lineSequence()) {
            val match = lineRegex.find(line) ?: continue
            val pct = match.groupValues[1].toFloatOrNull() ?: continue
            val name = match.groupValues[2].trim()
            if (name.equals("TOTAL", ignoreCase = true)) continue
            results.add(TopProcess(name, pct))
            if (results.size >= limit) break
        }
        return results
    }
}
