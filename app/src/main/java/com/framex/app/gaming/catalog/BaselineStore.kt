package com.framex.app.gaming.catalog

import com.framex.app.repository.SettingsRepository

/**
 * Values that existed before a session, kept on disk so a crash or a restart can still put
 * them back. A baseline of `null` means the key did not exist and must be deleted on restore.
 */
interface BaselineStore {
    /** Ids of every entry whose baseline is recorded. */
    fun capturedIds(): Set<String>

    fun baselineOf(id: String): String?

    /** Records all baselines atomically, before the first mutation. */
    suspend fun save(baselines: Map<String, String?>)

    suspend fun clear(ids: Set<String>)
}

/** [BaselineStore] backed by the settings store, so it shares its ordered, durable writes. */
class SettingsBaselineStore(private val settings: SettingsRepository) : BaselineStore {

    override fun capturedIds(): Set<String> = settings.getGamingBaselines().keys

    override fun baselineOf(id: String): String? =
        settings.getGamingBaselines()[id]?.takeIf { it != ABSENT }

    override suspend fun save(baselines: Map<String, String?>) {
        val merged = settings.getGamingBaselines() + baselines.mapValues { it.value ?: ABSENT }
        settings.setGamingBaselines(merged)
        settings.flushRecoveryWrites()
    }

    override suspend fun clear(ids: Set<String>) {
        settings.setGamingBaselines(settings.getGamingBaselines() - ids)
        settings.flushRecoveryWrites()
    }

    private companion object {
        /** Marks a key that did not exist; real values are never this long marker. */
        const val ABSENT = "\u0000absent"
    }
}
