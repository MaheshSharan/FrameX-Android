package com.framex.app.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.framex.app.gaming.GamingOptimizationSnapshot
import com.framex.app.gaming.GamingPlatformPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamingSettingsRepository @Inject constructor(
    private val store: FrameXPreferenceStore
) {
    private val _gamingModeWhitelist = MutableStateFlow(
        store.getStringSet("gaming_mode_whitelist", emptySet())
    )
    val gamingModeWhitelist: StateFlow<Set<String>> = _gamingModeWhitelist.asStateFlow()

    private val _launcherGames = MutableStateFlow(
        store.getStringSet("launcher_games", emptySet())
    )
    val launcherGames: StateFlow<Set<String>> = _launcherGames.asStateFlow()

    private val _deepFreezeEnabled = MutableStateFlow(
        store.getBoolean("deep_freeze_enabled", false)
    )
    val deepFreezeEnabled: StateFlow<Boolean> = _deepFreezeEnabled.asStateFlow()

    private val _hasSeenDeepFreezeNotice = MutableStateFlow(
        store.getBoolean("has_seen_deep_freeze_notice", false)
    )
    val hasSeenDeepFreezeNotice: StateFlow<Boolean> = _hasSeenDeepFreezeNotice.asStateFlow()

    private val _vivoOptEnabled = MutableStateFlow(
        store.getBoolean("vivo_opt_enabled", true)
    )
    val vivoOptEnabled: StateFlow<Boolean> = _vivoOptEnabled.asStateFlow()

    private val _gamingPlatformPath = MutableStateFlow(
        store.getString("gaming_platform_path", null)?.let {
            runCatching { GamingPlatformPath.valueOf(it) }.getOrNull()
        } ?: GamingPlatformPath.NONE
    )
    val gamingPlatformPath: StateFlow<GamingPlatformPath> = _gamingPlatformPath.asStateFlow()

    private val _isGamingModeActiveFlow = MutableStateFlow(
        store.getBoolean("is_gaming_mode_active", false)
    )
    val isGamingModeActiveFlow: StateFlow<Boolean> = _isGamingModeActiveFlow.asStateFlow()

    init {
        store.observeKey("gaming_settings") { prefs ->
            prefs[stringSetPreferencesKey("gaming_mode_whitelist")]?.let { _gamingModeWhitelist.value = it }
            prefs[stringSetPreferencesKey("launcher_games")]?.let { _launcherGames.value = it }
            prefs[booleanPreferencesKey("deep_freeze_enabled")]?.let { _deepFreezeEnabled.value = it }
            prefs[booleanPreferencesKey("has_seen_deep_freeze_notice")]?.let { _hasSeenDeepFreezeNotice.value = it }
            prefs[booleanPreferencesKey("vivo_opt_enabled")]?.let { _vivoOptEnabled.value = it }
            prefs[stringPreferencesKey("gaming_platform_path")]?.let { raw ->
                _gamingPlatformPath.value = runCatching { GamingPlatformPath.valueOf(raw) }.getOrDefault(GamingPlatformPath.NONE)
            }
            prefs[booleanPreferencesKey("is_gaming_mode_active")]?.let { _isGamingModeActiveFlow.value = it }
        }
    }

    fun toggleGamingWhitelistApp(packageName: String) {
        val current = _gamingModeWhitelist.value.toMutableSet()
        if (packageName in current) current.remove(packageName) else current.add(packageName)
        val copy = current.toSet()
        _gamingModeWhitelist.value = copy
        store.putStringSet("gaming_mode_whitelist", copy)
    }

    fun setLauncherGames(games: Set<String>) {
        val copy = games.toSet()
        _launcherGames.value = copy
        store.putStringSet("launcher_games", copy)
    }

    fun toggleLauncherGame(packageName: String) {
        val current = _launcherGames.value.toMutableSet()
        if (packageName in current) current.remove(packageName) else current.add(packageName)
        setLauncherGames(current)
    }

    fun setDeepFreezeEnabled(enabled: Boolean) {
        _deepFreezeEnabled.value = enabled
        store.putBoolean("deep_freeze_enabled", enabled)
    }

    fun setHasSeenDeepFreezeNotice(seen: Boolean) {
        _hasSeenDeepFreezeNotice.value = seen
        store.putBoolean("has_seen_deep_freeze_notice", seen)
    }

    fun setVivoOptEnabled(enabled: Boolean) {
        _vivoOptEnabled.value = enabled
        store.putBoolean("vivo_opt_enabled", enabled)
    }

    /** Null when no session path was recorded; crash recovery then decides from the hardware. */
    fun getGamingPlatformPath(): GamingPlatformPath? {
        return store.getString("gaming_platform_path", null)?.let {
            runCatching { GamingPlatformPath.valueOf(it) }.getOrNull()
        }
    }

    fun setGamingPlatformPath(path: GamingPlatformPath?) {
        val resolved = path ?: GamingPlatformPath.NONE
        _gamingPlatformPath.value = resolved
        if (path == null) {
            store.remove("gaming_platform_path")
        } else {
            store.putString("gaming_platform_path", path.name)
        }
    }

    /**
     * Suspends until every preference write issued so far is on disk. Call it after writing a
     * recovery record and before the system mutation that the record protects (AGENTS 2.8).
     */
    suspend fun flushWrites() = store.flush()

    fun isGamingModeActive(): Boolean = store.getBoolean("is_gaming_mode_active", false)

    fun setGamingModeActive(active: Boolean) {
        _isGamingModeActiveFlow.value = active
        store.putBoolean("is_gaming_mode_active", active)
    }

    /** Backend that applied the current Gaming Mode session; recovery must undo through the same one. */
    fun getAppliedVia(): com.framex.app.privilege.PrivilegeMode =
        com.framex.app.privilege.PrivilegeMode.fromKey(store.getString("gaming_applied_via", null))

    fun setAppliedVia(mode: com.framex.app.privilege.PrivilegeMode?) {
        if (mode == null || mode == com.framex.app.privilege.PrivilegeMode.NONE) {
            store.remove("gaming_applied_via")
        } else {
            store.putString("gaming_applied_via", mode.key)
        }
    }

    fun getGamingAffectedPackages(): Set<String> {
        return store.getStringSet("gaming_affected_packages", emptySet())
    }

    fun setGamingAffectedPackages(packages: Set<String>) {
        if (packages.isEmpty()) {
            store.remove("gaming_affected_packages")
        } else {
            store.putStringSet("gaming_affected_packages", packages)
        }
    }

    fun saveGamingOptimizationSnapshot(snapshot: GamingOptimizationSnapshot) {
        store.putString(KEY_GAMING_SNAPSHOT_JSON, snapshot.toJson())
    }

    fun loadGamingOptimizationSnapshot(): GamingOptimizationSnapshot? {
        val raw = store.getString(KEY_GAMING_SNAPSHOT_JSON, null) ?: return null
        return GamingOptimizationSnapshot.fromJson(raw)
    }

    fun clearGamingOptimizationSnapshot() {
        store.remove(KEY_GAMING_SNAPSHOT_JSON)
    }

    fun hasActiveGamingSnapshot(): Boolean {
        return store.getString(KEY_GAMING_SNAPSHOT_JSON, null) != null
    }

    fun needsThermalOverrideRecovery(): Boolean {
        return store.getBoolean("needs_thermal_override_recovery", false)
    }

    fun setNeedsThermalOverrideActive(active: Boolean) {
        store.putBoolean("needs_thermal_override_recovery", active)
    }

    fun markThermalOverrideRecoveryComplete() {
        store.remove("needs_thermal_override_recovery")
    }

    fun needsLegacySettingsCleanup(): Boolean {
        return !store.getBoolean("legacy_settings_cleanup_v2_done", false)
    }

    fun markLegacySettingsCleanupComplete() {
        store.putBoolean("legacy_settings_cleanup_v2_done", true)
    }

    fun cleanupLegacyPreferences() {
        val legacyKeys = listOf(
            "vivo_monster_mode_legacy",
            "esports_mode_enabled",
            "legacy_refresh_override",
            "legacy_thermal_override"
        )
        for (key in legacyKeys) {
            if (store.contains(key)) {
                store.remove(key)
            }
        }
    }

    fun getGameConfigBoostRam(pkg: String): Boolean =
        store.getBoolean("game_config_${pkg}_boost_ram", true)

    fun setGameConfigBoostRam(pkg: String, enabled: Boolean) {
        store.putBoolean("game_config_${pkg}_boost_ram", enabled)
    }

    companion object {
        private const val KEY_GAMING_SNAPSHOT_JSON = "gaming_optimization_snapshot_json"
    }
}
