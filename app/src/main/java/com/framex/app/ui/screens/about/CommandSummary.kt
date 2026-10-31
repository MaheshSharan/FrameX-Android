package com.framex.app.ui.screens.about

import com.framex.app.gaming.catalog.GamingCommandCatalog
import com.framex.app.gaming.catalog.Platform
import com.framex.app.gaming.catalog.ResolveContext
import com.framex.app.gaming.catalog.Toggle
import com.framex.app.gaming.ledger.OpPriority

/**
 * Builds the command list shown on a switch from the catalog, so the screen cannot describe a
 * command that the engine does not run. Only the main commands are listed.
 */
object CommandSummary {

    private val sampleA = ResolveContext("sample.one", 1, 60)
    private val sampleB = ResolveContext("sample.two", 2, 120)

    fun lines(platform: Platform, toggle: Toggle): List<String> =
        GamingCommandCatalog.forPlatform(platform)
            .filter { it.toggle == toggle && it.priority == OpPriority.PRIMARY }
            .map { entry ->
                val first = entry.value(sampleA)
                val shown = if (first == entry.value(sampleB)) " = $first" else ""
                "• Settings ${entry.table.argument.replaceFirstChar { it.uppercase() }}: ${entry.key}$shown"
            }

    fun text(platform: Platform, toggle: Toggle, detail: String?): String =
        (lines(platform, toggle) + listOfNotNull(detail?.let { "• $it" })).joinToString("\n")
}
