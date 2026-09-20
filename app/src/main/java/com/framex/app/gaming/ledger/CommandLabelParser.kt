package com.framex.app.gaming.ledger

data class ParsedCommand(
    val key: String,
    val value: String
)

object CommandLabelParser {

    private val CONTENT_INSERT_REGEX = Regex(
        """--uri\s+content://settings/([^\s]+)\s+--bind\s+name:s:([^\s]+)\s+--bind\s+value:\w+:(.*)"""
    )

    private val SETTINGS_PUT_REGEX = Regex(
        """settings\s+put\s+([^\s]+)\s+([^\s]+)\s+(.*)"""
    )

    private val DEVICE_CONFIG_REGEX = Regex(
        """cmd\s+device_config\s+put\s+([^\s]+)\s+([^\s]+)\s+(.*)"""
    )

    fun parse(command: String): ParsedCommand {
        val trimmed = command.trim()

        // 1. content insert ... --uri content://settings/TABLE --bind name:s:K --bind value:s:V
        CONTENT_INSERT_REGEX.find(trimmed)?.let { match ->
            val table = cleanValue(match.groupValues[1])
            val name = cleanValue(match.groupValues[2])
            val rawVal = match.groupValues[3].trim()
            val key = if (table.isNotEmpty()) "$table:$name" else name
            return ParsedCommand(key, cleanValue(rawVal))
        }

        // 2. settings put <table/ns> K V
        SETTINGS_PUT_REGEX.find(trimmed)?.let { match ->
            val table = cleanValue(match.groupValues[1])
            val name = cleanValue(match.groupValues[2])
            val rawVal = match.groupValues[3].trim()
            val key = if (table.isNotEmpty()) "$table:$name" else name
            return ParsedCommand(key, cleanValue(rawVal))
        }

        // 3. cmd device_config put <ns> K V
        DEVICE_CONFIG_REGEX.find(trimmed)?.let { match ->
            val ns = cleanValue(match.groupValues[1])
            val name = cleanValue(match.groupValues[2])
            val rawVal = match.groupValues[3].trim()
            val key = if (ns.isNotEmpty()) "$ns:$name" else name
            return ParsedCommand(key, cleanValue(rawVal))
        }

        // 4. Action verbs for pm / am / cmd
        val tokens = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isNotEmpty()) {
            return when {
                tokens[0] in listOf("pm", "am") && tokens.size == 2 -> {
                    ParsedCommand(key = tokens[1], value = "")
                }
                tokens[0] in listOf("pm", "am") && tokens.size >= 3 -> {
                    ParsedCommand(key = tokens[1], value = cleanValue(tokens.drop(2).joinToString(" ")))
                }
                tokens[0] == "cmd" && tokens.size >= 3 -> {
                    ParsedCommand(key = "${tokens[1]}:${tokens[2]}", value = cleanValue(tokens.drop(3).joinToString(" ")))
                }
                else -> {
                    val key = tokens.firstOrNull() ?: "cmd"
                    val value = tokens.drop(1).take(2).joinToString(" ")
                    ParsedCommand(key, cleanValue(value))
                }
            }
        }

        return ParsedCommand("cmd", "")
    }

    fun cleanValue(value: String): String {
        var res = value.trim()
        // Strip outer escaped quotes: \"val\" or "val" or 'val'
        if (res.startsWith("\\\"") && res.endsWith("\\\"") && res.length >= 4) {
            res = res.substring(2, res.length - 2)
        } else if (res.startsWith("\"") && res.endsWith("\"") && res.length >= 2) {
            res = res.substring(1, res.length - 1)
        } else if (res.startsWith("'") && res.endsWith("'") && res.length >= 2) {
            res = res.substring(1, res.length - 1)
        }
        return res.trim()
    }

    fun formatLabel(key: String, displayValue: String): String {
        return if (displayValue.isBlank() || displayValue.equals("active", ignoreCase = true)) {
            key
        } else {
            "$key = $displayValue"
        }
    }

    fun formatMiddleEllipsis(text: String, maxLength: Int = 24): String {
        if (text.length <= maxLength) return text
        val keep = (maxLength - 3) / 2
        return text.take(keep) + "..." + text.takeLast(keep)
    }
}
