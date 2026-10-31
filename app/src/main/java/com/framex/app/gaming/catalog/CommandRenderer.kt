package com.framex.app.gaming.catalog

/**
 * Turns entries into shell text. Values are validated here, once, because they can contain a
 * package name or a number taken from the running game.
 */
object CommandRenderer {

    private val BARE_VALUE = Regex("[A-Za-z0-9_.:-]+")
    private val QUOTABLE_VALUE = Regex("[A-Za-z0-9_.:,@#-]+")

    /** Null when the value cannot be written safely. */
    fun apply(entry: CommandEntry, context: ResolveContext): String? {
        val value = entry.value(context)
        val bound = bindValue(value) ?: return null
        return "content insert --uri content://settings/${entry.table.argument}" +
            " --bind name:s:${entry.key} --bind value:s:$bound"
    }

    /**
     * Null when the captured value cannot be written back safely. Such a key is reported as a
     * failed restore instead of being deleted, because deleting would lose the person's value.
     */
    fun restore(entry: CommandEntry, baseline: String?): String? {
        if (entry.restore == Restore.DELETE || baseline == null) return delete(entry)
        val bound = bindValue(baseline) ?: return null
        return "content insert --uri content://settings/${entry.table.argument}" +
            " --bind name:s:${entry.key} --bind value:s:$bound"
    }

    fun delete(entry: CommandEntry): String =
        "content delete --uri content://settings/${entry.table.argument} --where \"name='${entry.key}'\""

    /** Prints one `FXB|id|value` line; the value is `null` when the key does not exist. */
    fun captureLine(entry: CommandEntry): String =
        "echo \"FXB|${entry.id}|\$(settings get ${entry.table.argument} ${entry.key} 2>/dev/null)\""

    private fun bindValue(value: String): String? = when {
        value.isEmpty() -> null
        BARE_VALUE.matches(value) -> value
        QUOTABLE_VALUE.matches(value) -> "\"$value\""
        else -> null
    }
}
