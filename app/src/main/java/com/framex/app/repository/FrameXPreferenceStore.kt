package com.framex.app.repository

import android.content.Context
import androidx.datastore.core.DataStore
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
import com.framex.app.repository.migration.LegacySettingsMigration
import com.framex.app.utils.FrameXLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
            migrations = listOf(LegacySettingsMigration(context)),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("framex_datastore") }
        )
    }
}

/**
 * Preference store whose only durable backing is a singleton [DataStore].
 *
 * The in-memory cache exists so that services, receivers and repositories can read values
 * without suspending. It is hydrated from the DataStore once and kept in step with every
 * write, so it never holds anything the DataStore will not eventually contain. Reads made
 * before the first hydration return the caller's default; cold paths (boot receiver,
 * service start, crash recovery) must call [awaitLoaded] first.
 *
 * Recovery-critical writes must be followed by [flush], because the individual setters
 * return before the disk commit completes.
 */
@Singleton
class FrameXPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>?
) {
    /** In-memory store without a DataStore, used by JVM unit tests. */
    constructor() : this(null)

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache = ConcurrentHashMap<String, Any>()
    private val listeners = ConcurrentHashMap<String, (Preferences) -> Unit>()
    private val writeQueue = Channel<WriteRequest>(Channel.UNLIMITED)
    private val pendingWrites = AtomicInteger(0)
    private val loaded = CompletableDeferred<Unit>()

    @Volatile
    private var lastSnapshot: Preferences? = null

    init {
        if (dataStore == null) {
            loaded.complete(Unit)
        } else {
            ioScope.launch { observeDataStore(dataStore) }
            ioScope.launch { drainWrites(dataStore) }
        }
    }

    /** Suspends until the DataStore has been read at least once. */
    suspend fun awaitLoaded() = loaded.await()

    /**
     * Suspends until every write issued so far has been committed to disk. Call this
     * between writing a recovery record and the system mutation that depends on it.
     */
    suspend fun flush() {
        if (dataStore == null) return
        val marker = CompletableDeferred<Unit>()
        pendingWrites.incrementAndGet()
        writeQueue.trySend(WriteRequest(null, marker))
        marker.await()
    }

    /**
     * Registers a hydration callback. If the DataStore has already been read, the callback is
     * invoked immediately with the latest snapshot so late registrants are not left on defaults.
     */
    fun observeKey(key: String, onChanged: (Preferences) -> Unit) {
        listeners[key] = onChanged
        lastSnapshot?.let(onChanged)
    }

    fun getBoolean(key: String, default: Boolean): Boolean = cache[key] as? Boolean ?: default

    fun getInt(key: String, default: Int): Int = cache[key] as? Int ?: default

    fun getLong(key: String, default: Long): Long = cache[key] as? Long ?: default

    fun getFloat(key: String, default: Float): Float = cache[key] as? Float ?: default

    fun getString(key: String, default: String?): String? {
        if (cache.containsKey(key)) return cache[key] as? String
        return default
    }

    fun getStringSet(key: String, default: Set<String>): Set<String> {
        @Suppress("UNCHECKED_CAST")
        return (cache[key] as? Set<String>) ?: default
    }

    fun contains(key: String): Boolean = cache.containsKey(key)

    fun putBoolean(key: String, value: Boolean) {
        cache[key] = value
        editDataStore { it[booleanPreferencesKey(key)] = value }
    }

    fun putInt(key: String, value: Int) {
        cache[key] = value
        editDataStore { it[intPreferencesKey(key)] = value }
    }

    fun putLong(key: String, value: Long) {
        cache[key] = value
        editDataStore { it[longPreferencesKey(key)] = value }
    }

    fun putFloat(key: String, value: Float) {
        cache[key] = value
        editDataStore { it[floatPreferencesKey(key)] = value }
    }

    fun putString(key: String, value: String) {
        cache[key] = value
        editDataStore { it[stringPreferencesKey(key)] = value }
    }

    fun putStringSet(key: String, value: Set<String>) {
        val copy = value.toSet()
        cache[key] = copy
        editDataStore { it[stringSetPreferencesKey(key)] = copy }
    }

    fun remove(key: String) {
        cache.remove(key)
        editDataStore { prefs ->
            prefs.remove(stringPreferencesKey(key))
            prefs.remove(booleanPreferencesKey(key))
            prefs.remove(intPreferencesKey(key))
            prefs.remove(longPreferencesKey(key))
            prefs.remove(floatPreferencesKey(key))
            prefs.remove(stringSetPreferencesKey(key))
        }
    }

    /** Applies several related writes in one DataStore transaction. */
    fun batchUpdate(dataStoreBlock: (MutablePreferences) -> Unit) = editDataStore(dataStoreBlock)

    /** Mirrors a value already written through [batchUpdate] into the read cache. */
    fun updateMemoryCache(key: String, value: Any) {
        cache[key] = value
    }

    private suspend fun observeDataStore(store: DataStore<Preferences>) {
        runCatching {
            store.data.collect { prefs ->
                hydrate(prefs)
                lastSnapshot = prefs
                loaded.complete(Unit)
                for (callback in listeners.values) callback(prefs)
            }
        }.onFailure { e ->
            FrameXLog.e("DataStore observation failed", e)
            loaded.complete(Unit)
        }
    }

    /**
     * Mirrors the DataStore into the cache. While a local write is in flight the cache is
     * already ahead of the disk, so an older emission must not overwrite it.
     */
    private fun hydrate(prefs: Preferences) {
        if (pendingWrites.get() > 0) return
        val persisted = prefs.asMap().mapKeys { it.key.name }
        cache.keys.retainAll(persisted.keys)
        for ((name, value) in persisted) cache[name] = value
    }

    /** Writes are applied strictly in submission order so a later value never loses to an older one. */
    private fun editDataStore(block: (MutablePreferences) -> Unit) {
        if (dataStore == null) return
        pendingWrites.incrementAndGet()
        writeQueue.trySend(WriteRequest(block, CompletableDeferred()))
    }

    private suspend fun drainWrites(store: DataStore<Preferences>) {
        for (request in writeQueue) {
            try {
                request.block?.let { store.edit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FrameXLog.e("DataStore write failed", e)
            } finally {
                pendingWrites.decrementAndGet()
                request.done.complete(Unit)
            }
        }
    }

    private class WriteRequest(
        val block: ((MutablePreferences) -> Unit)?,
        val done: CompletableDeferred<Unit>
    )
}
