package com.framex.app.repository.migration

/** Raw contents of every store the migration may read, keyed by preference name. */
internal data class LegacySources(
    /** `framex_settings` plus `framex_feature_discovery`, the files written by the old build. */
    val legacy: Map<String, Any?>,
    /** `framex_prefs` plus `framex_feature_discovery_prefs`, the mirror of the intermediate build. */
    val intermediate: Map<String, Any?>,
    /** Values already present in the DataStore. */
    val current: Map<String, Any>,
    /** Old default for `enabled_module_icons`, which was every default module key. */
    val oldDefaultModuleIcons: Set<String>
)

/**
 * Decides which values to write into the DataStore. Pure so it can be unit tested.
 *
 * Precedence per key: value already in the DataStore, then the intermediate mirror, then the
 * old build's file, then the old default (upgraders only). A key present in the DataStore is
 * never overwritten.
 */
internal object LegacyMigrationPlanner {

    private const val TEXT_SIZE_KEY = "overlay_text_size"
    private const val DEFAULT_TEXT_SIZE_INDEX = 1
    private val TEXT_SIZE_RANGE = 0..2
    private const val AFFECTED_PACKAGES_KEY = "gaming_affected_packages"
    private const val PRIVILEGE_MODE_KEY = "privilege_mode"
    private const val SHIZUKU_MODE = "shizuku"

    fun plan(sources: LegacySources): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        fun isSet(key: String) = key in sources.current || key in result

        for ((key, value) in sources.intermediate) {
            if (value != null && key !in sources.current) result[key] = value
        }
        for ((oldKey, rawValue) in sources.legacy) {
            val value = rawValue ?: continue
            val newKey = translate(oldKey, value) ?: continue
            if (!isSet(newKey)) result[newKey] = value
        }
        mergeAffectedPackages(sources, result)
        if (sources.legacy.isNotEmpty()) materializeOldDefaults(sources, result, ::isSet)
        sanitizeTextSize(sources, result)
        seedPrivilegeMode(sources, result, ::isSet)
        return result
    }

    /** Returns the new key for [oldKey], or null when the value must not be migrated. */
    private fun translate(oldKey: String, value: Any): String? {
        if (oldKey in LegacyKeyMap.dropped) return null
        if (oldKey in LegacyKeyMap.positionKeys && value == -1) return null
        return LegacyKeyMap.renamed[oldKey] ?: oldKey
    }

    /**
     * Suspended-package lists are merged instead of replaced: restoring a package that is
     * not suspended is harmless, while forgetting one leaves an app frozen.
     */
    private fun mergeAffectedPackages(sources: LegacySources, result: MutableMap<String, Any>) {
        val legacy = sources.legacy["gaming_affected_pkgs"] as? Set<*> ?: return
        val existing = (sources.current[AFFECTED_PACKAGES_KEY] ?: result[AFFECTED_PACKAGES_KEY]) as? Set<*>
        val merged = (legacy + (existing ?: emptySet<Any>())).filterIsInstance<String>().toSet()
        if (merged.isNotEmpty()) result[AFFECTED_PACKAGES_KEY] = merged
    }

    /**
     * An absent key in the old file meant the old default was in effect, so upgraders keep
     * that behaviour instead of silently switching to the refactor's new defaults.
     */
    private fun materializeOldDefaults(
        sources: LegacySources,
        result: MutableMap<String, Any>,
        isSet: (String) -> Boolean
    ) {
        if (!isSet("enabled_modules")) result["enabled_modules"] = setOf("fps")
        if (!isSet("enabled_module_icons")) result["enabled_module_icons"] = sources.oldDefaultModuleIcons
        if (!isSet("overlay_opacity")) result["overlay_opacity"] = 0.75f
    }

    /** The intermediate build briefly stored a pixel-like size (11) where an index is expected. */
    private fun sanitizeTextSize(sources: LegacySources, result: MutableMap<String, Any>) {
        val value = (result[TEXT_SIZE_KEY] ?: sources.current[TEXT_SIZE_KEY]) as? Int ?: return
        if (value !in TEXT_SIZE_RANGE) result[TEXT_SIZE_KEY] = DEFAULT_TEXT_SIZE_INDEX
    }

    /** Existing users relied on Shizuku before a mode existed; do not strand them on NONE. */
    private fun seedPrivilegeMode(
        sources: LegacySources,
        result: MutableMap<String, Any>,
        isSet: (String) -> Boolean
    ) {
        if (isSet(PRIVILEGE_MODE_KEY)) return
        val onboarded = (sources.current["onboarding_completed"] ?: result["onboarding_completed"]) == true
        if (onboarded) result[PRIVILEGE_MODE_KEY] = SHIZUKU_MODE
    }
}
