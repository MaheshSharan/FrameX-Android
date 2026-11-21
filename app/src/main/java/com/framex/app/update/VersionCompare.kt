package com.framex.app.update

/** Compares release versions such as `1.6.0` and `1.6.0-beta`. */
internal object VersionCompare {

    /**
     * True when [remote] is newer than [current]. Numbers decide first; when they are equal, a
     * pre-release build (`-beta`) is older than the same number without a suffix.
     */
    fun isHigher(remote: String, current: String): Boolean {
        val remoteParts = numbers(remote)
        val currentParts = numbers(current)
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return current.contains('-') && !remote.contains('-')
    }

    private fun numbers(version: String): List<Int> =
        version.substringBefore('-').split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
}
