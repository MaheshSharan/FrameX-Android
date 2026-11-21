package com.framex.app.gaming.catalog

/**
 * Turns entries into shell text. Values are validated here, once, because they can contain a
 * package name or a number taken from the running game.
 */
object CommandRenderer {

    private val BARE_VALUE = Regex("[A-Za-z0-9_.:-]+")
    private val QUOTABLE_VALUE = Regex("[A-Za-z0-9_.:,@#-]+")

    /** Null when the value cannot be written safely, or a [CommandTarget.ServiceCommand] opts out. */
    fun apply(entry: CommandEntry, context: ResolveContext): String? = when (val t = entry.target) {
        is CommandTarget.SettingsKey -> settingsPut(t, entry.value(context))
        is CommandTarget.ServiceCommand -> t.activate(context)
    }

    /**
     * Null when the captured value cannot be written back safely, the entry owns nothing to
     * restore ([Restore.NONE]), or a [CommandTarget.ServiceCommand] has neither a capture-based
     * nor a fixed revert. Such a settings key is reported as a failed restore instead of being
     * deleted, because deleting would lose the person's value.
     */
    fun restore(entry: CommandEntry, baseline: String?, context: ResolveContext): String? {
        if (entry.restore == Restore.NONE) return null
        return when (val t = entry.target) {
            is CommandTarget.SettingsKey -> {
                if (entry.restore == Restore.DELETE) return delete(t)
                if (baseline == null) return delete(t)
                settingsPut(t, baseline)
            }
            is CommandTarget.ServiceCommand -> when {
                t.restoreFromCapture != null -> t.restoreFromCapture.invoke(context, baseline)
                t.fixedRevert != null -> t.fixedRevert.invoke(context)
                else -> null
            }
        }
    }

    /**
     * `settings delete`, not `content delete --uri content://settings/...`: the content-provider
     * route requires permissions that a Shizuku (non-root) shell does not reliably hold for the
     * secure/global tables on every OEM, while `settings` is the same CLI the capability probe
     * already validated as writable and [captureLine] already reads with. Using a different
     * mechanism than the one actually probed was the root cause of settings restores silently
     * failing on Shizuku-only devices even though the probe reported them as writable.
     */
    fun delete(target: CommandTarget.SettingsKey): String =
        "settings delete ${target.table.argument} ${target.key}"

    /**
     * One `FXB|id|value` line for [CommandTarget.SettingsKey] (the value is `null` when the key
     * does not exist), or for a [CommandTarget.ServiceCommand] with a non-null [CommandTarget
     * .ServiceCommand.capture]. Null when there is nothing meaningful to capture — the restorer
     * must treat that as "nothing to read", not a failure.
     */
    fun captureLine(entry: CommandEntry, context: ResolveContext = ResolveContext(null)): String? =
        when (val t = entry.target) {
            is CommandTarget.SettingsKey ->
                "echo \"FXB|${entry.id}|\$(settings get ${t.table.argument} ${t.key} 2>/dev/null)\""
            is CommandTarget.ServiceCommand ->
                t.capture?.let { capture -> "echo \"FXB|${entry.id}|\$(${capture(context)})\"" }
        }

    private fun settingsPut(target: CommandTarget.SettingsKey, value: String): String? {
        val bound = bindValue(value) ?: return null
        return "settings put ${target.table.argument} ${target.key} $bound"
    }

    private fun bindValue(value: String): String? = when {
        value.isEmpty() -> null
        BARE_VALUE.matches(value) -> value
        QUOTABLE_VALUE.matches(value) -> "\"$value\""
        else -> null
    }
}
