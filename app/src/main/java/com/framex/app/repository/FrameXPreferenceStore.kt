package com.framex.app.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.framex.app.utils.FrameXLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            migrations = listOf(
                SharedPreferencesMigration(context, "framex_prefs"),
                SharedPreferencesMigration(context, "framex_feature_discovery_prefs")
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("framex_datastore") }
        )
    }
}

/**
 * Reactive, non-blocking preference store backed by a singleton [DataStore] in production
 * with synchronous in-memory cache and legacy [SharedPreferences] mirror for zero-blocking
 * startup reads, broadcast receivers, and JVM unit tests.
 */
@Singleton
class FrameXPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
    private val dataStore: DataStore<Preferences>?
) {
    constructor(context: Context) : this(context, null)

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val legacyPrefs: SharedPreferences? = runCatching {
        context.getSharedPreferences("framex_prefs", Context.MODE_PRIVATE)
    }.getOrNull()

    private val discoveryPrefs: SharedPreferences? = runCatching {
        context.getSharedPreferences("framex_feature_discovery_prefs", Context.MODE_PRIVATE)
    }.getOrNull()

    private val memoryCache = ConcurrentHashMap<String, Any>()
    private val listeners = ConcurrentHashMap<String, (Preferences) -> Unit>()

    init {
        if (dataStore != null) {
            ioScope.launch {
                runCatching {
                    dataStore.data.collectLatest { prefs ->
                        for ((_, callback) in listeners) {
                            callback(prefs)
                        }
                    }
                }.onFailure { e ->
                    FrameXLog.w("DataStore observation failed", e)
                }
            }
        }
    }

    fun observeKey(key: String, onChanged: (Preferences) -> Unit) {
        listeners[key] = onChanged
    }

    fun getBoolean(key: String, default: Boolean, useDiscoveryStore: Boolean = false): Boolean {
        (memoryCache[key] as? Boolean)?.let { return it }
        val prefs = if (useDiscoveryStore) discoveryPrefs else legacyPrefs
        return prefs?.getBoolean(key, default) ?: default
    }

    fun getInt(key: String, default: Int): Int {
        (memoryCache[key] as? Int)?.let { return it }
        return legacyPrefs?.getInt(key, default) ?: default
    }

    fun getLong(key: String, default: Long): Long {
        (memoryCache[key] as? Long)?.let { return it }
        return legacyPrefs?.getLong(key, default) ?: default
    }

    fun getFloat(key: String, default: Float): Float {
        (memoryCache[key] as? Float)?.let { return it }
        return legacyPrefs?.getFloat(key, default) ?: default
    }

    fun getString(key: String, default: String?): String? {
        if (memoryCache.containsKey(key)) {
            return memoryCache[key] as? String
        }
        return legacyPrefs?.getString(key, default) ?: default
    }

    fun getStringSet(key: String, default: Set<String>): Set<String> {
        @Suppress("UNCHECKED_CAST")
        (memoryCache[key] as? Set<String>)?.let { return it }
        return legacyPrefs?.getStringSet(key, default)?.toSet() ?: default
    }

    fun contains(key: String): Boolean {
        return memoryCache.containsKey(key) || (legacyPrefs?.contains(key) == true)
    }

    fun putBoolean(
        key: String,
        value: Boolean,
        synchronousMirror: Boolean = false,
        useDiscoveryStore: Boolean = false
    ) {
        memoryCache[key] = value
        val targetPrefs = if (useDiscoveryStore) discoveryPrefs else legacyPrefs
        targetPrefs?.edit()?.apply {
            putBoolean(key, value)
            if (synchronousMirror) commit() else apply()
        }
        editDataStore { it[booleanPreferencesKey(key)] = value }
    }

    fun putInt(key: String, value: Int) {
        memoryCache[key] = value
        legacyPrefs?.edit()?.putInt(key, value)?.apply()
        editDataStore { it[intPreferencesKey(key)] = value }
    }

    fun putLong(key: String, value: Long) {
        memoryCache[key] = value
        legacyPrefs?.edit()?.putLong(key, value)?.apply()
        editDataStore { it[longPreferencesKey(key)] = value }
    }

    fun putFloat(key: String, value: Float) {
        memoryCache[key] = value
        legacyPrefs?.edit()?.putFloat(key, value)?.apply()
        editDataStore { it[floatPreferencesKey(key)] = value }
    }

    fun putString(key: String, value: String, synchronousMirror: Boolean = false) {
        memoryCache[key] = value
        legacyPrefs?.edit()?.apply {
            putString(key, value)
            if (synchronousMirror) commit() else apply()
        }
        editDataStore { it[stringPreferencesKey(key)] = value }
    }

    fun putStringSet(key: String, value: Set<String>) {
        val copy = value.toSet()
        memoryCache[key] = copy
        legacyPrefs?.edit()?.putStringSet(key, copy)?.apply()
        editDataStore { it[stringSetPreferencesKey(key)] = copy }
    }

    fun remove(key: String, synchronousMirror: Boolean = false) {
        memoryCache.remove(key)
        legacyPrefs?.edit()?.apply {
            remove(key)
            if (synchronousMirror) commit() else apply()
        }
        editDataStore { prefs ->
            prefs.remove(stringPreferencesKey(key))
            prefs.remove(booleanPreferencesKey(key))
            prefs.remove(intPreferencesKey(key))
            prefs.remove(longPreferencesKey(key))
            prefs.remove(floatPreferencesKey(key))
            prefs.remove(stringSetPreferencesKey(key))
        }
    }

    fun batchUpdate(
        legacyBlock: (SharedPreferences.Editor.() -> Unit)? = null,
        dataStoreBlock: (MutablePreferences) -> Unit
    ) {
        if (legacyBlock != null) {
            legacyPrefs?.edit()?.apply(legacyBlock)?.apply()
        }
        editDataStore(dataStoreBlock)
    }

    fun updateMemoryCache(key: String, value: Any) {
        memoryCache[key] = value
    }

    private fun editDataStore(block: (MutablePreferences) -> Unit) {
        val ds = dataStore ?: return
        ioScope.launch {
            runCatching {
                ds.edit(block)
            }.onFailure { e ->
                FrameXLog.w("DataStore write failed", e)
            }
        }
    }
}
