package com.framex.app.gaming.catalog

/** Every vendor settings command FrameX can write, grouped by the platform that owns it. */
object GamingCommandCatalog {

    val entries: List<CommandEntry> = VIVO_COMMANDS + IQOO_COMMANDS

    fun forPlatform(platform: Platform): List<CommandEntry> =
        entries.filter { platform in it.platforms }
}
