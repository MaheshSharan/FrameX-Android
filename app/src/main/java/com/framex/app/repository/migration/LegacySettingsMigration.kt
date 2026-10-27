package com.framex.app.repository.migration

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.framex.app.metrics.DEFAULT_METRIC_MODULE_ORDER
import com.framex.app.utils.FrameXLog

/**
 * One-time import of settings written by the pre-refactor build.
 *
 * Runs inside the DataStore's own initialisation, so the imported values and the completion
 * flag are persisted in one atomic write and no reader can observe a half-migrated store.
 * The old files are kept for one release as a rollback source; only the intermediate
 * build's mirrors, which only duplicate the DataStore, are removed afterwards.
 */
class LegacySettingsMigration(private val context: Context) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[booleanPreferencesKey(LegacyKeyMap.MIGRATION_DONE_KEY)] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val sources = LegacySources(
            legacy = readAll(OLD_SETTINGS_FILE) + readAll(OLD_DISCOVERY_FILE),
            intermediate = readAll(MIRROR_SETTINGS_FILE) + readAll(MIRROR_DISCOVERY_FILE),
            current = currentData.asMap().mapKeys { it.key.name },
            oldDefaultModuleIcons = DEFAULT_METRIC_MODULE_ORDER.map { it.storageKey }.toSet()
        )
        val planned = LegacyMigrationPlanner.plan(sources)
        val updated = currentData.toMutablePreferences()
        for ((key, value) in planned) write(updated, key, value)
        updated[booleanPreferencesKey(LegacyKeyMap.MIGRATION_DONE_KEY)] = true
        FrameXLog.i("Legacy settings migration v3 wrote ${planned.size} keys", tag = TAG)
        return updated
    }

    override suspend fun cleanUp() {
        context.deleteSharedPreferences(MIRROR_SETTINGS_FILE)
        context.deleteSharedPreferences(MIRROR_DISCOVERY_FILE)
    }

    private fun readAll(name: String): Map<String, Any?> = runCatching {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all
    }.onFailure { FrameXLog.w("Could not read legacy preferences $name", it, tag = TAG) }
        .getOrDefault(emptyMap())

    private fun write(prefs: MutablePreferences, key: String, value: Any) {
        when (value) {
            is Boolean -> prefs[booleanPreferencesKey(key)] = value
            is Int -> prefs[intPreferencesKey(key)] = value
            is Long -> prefs[longPreferencesKey(key)] = value
            is Float -> prefs[floatPreferencesKey(key)] = value
            is String -> prefs[stringPreferencesKey(key)] = value
            is Set<*> -> prefs[stringSetPreferencesKey(key)] = value.filterIsInstance<String>().toSet()
            else -> FrameXLog.w("Skipping $key: unsupported type ${value::class.java.simpleName}", tag = TAG)
        }
    }

    private companion object {
        const val TAG = "LegacyMigration"
        const val OLD_SETTINGS_FILE = "framex_settings"
        const val OLD_DISCOVERY_FILE = "framex_feature_discovery"
        const val MIRROR_SETTINGS_FILE = "framex_prefs"
        const val MIRROR_DISCOVERY_FILE = "framex_feature_discovery_prefs"
    }
}
