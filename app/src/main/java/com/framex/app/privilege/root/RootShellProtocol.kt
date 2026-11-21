package com.framex.app.privilege.root

/** A marker line found in the shell output: text printed before it, and the exit code. */
internal data class MarkerHit(val textBefore: String, val exitCode: Int)

/**
 * Framing for commands sent to one long-lived `su` shell. Each command is wrapped so that it
 * cannot read the shell's stdin, cannot terminate the shell with `exit`, and is followed by a
 * line carrying a per-session secret marker and the exit code.
 */
internal object RootShellProtocol {

    /** The command runs in a subshell; the newlines keep a trailing comment from eating `)`. */
    fun payload(command: String, marker: String): String =
        "(\n$command\n) </dev/null 2>&1\necho \"$marker:\$?\"\n"

    /**
     * Returns the marker hit when [line] contains `<marker>:<code>`. The marker may follow
     * output that had no trailing newline, so text before it is preserved.
     */
    fun matchMarker(line: String, marker: String): MarkerHit? {
        val prefix = "$marker:"
        val index = line.indexOf(prefix)
        if (index < 0) return null
        val code = line.substring(index + prefix.length).trim().toIntOrNull() ?: return null
        return MarkerHit(line.substring(0, index), code)
    }
}
