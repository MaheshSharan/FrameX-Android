package com.framex.app.metrics

/**
 * Pure parser for the busiest process line in `dumpsys cpuinfo` output.
 * Same package-level style as [CpuStatParser] — no Android deps.
 */
internal object CpuInfoTopParser {

    data class TopProcess(val name: String, val cpuPercent: Float)

    // e.g. "  23% 1234/com.example.app: 15% user + 8% kernel"
    private val lineRegex = Regex("""^\s*([0-9.]+)%\s+\d+/([^:]+):""")

    fun parseTop(output: String): TopProcess? {
        if (output.isBlank()) return null
        for (line in output.lineSequence()) {
            val match = lineRegex.find(line) ?: continue
            val pct = match.groupValues[1].toFloatOrNull() ?: continue
            val name = match.groupValues[2].trim()
            if (name.equals("TOTAL", ignoreCase = true)) continue
            return TopProcess(name, pct)
        }
        return null
    }
}
