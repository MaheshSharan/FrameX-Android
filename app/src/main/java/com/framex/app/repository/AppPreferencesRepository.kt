package com.framex.app.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val store: FrameXPreferenceStore
) {
    private val _isOnboardingCompleted = MutableStateFlow(
        store.getBoolean("onboarding_completed", false)
    )
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _autoUpdateCheckEnabled = MutableStateFlow(
        store.getBoolean("auto_update_check_enabled", true)
    )
    val autoUpdateCheckEnabled: StateFlow<Boolean> = _autoUpdateCheckEnabled.asStateFlow()

    init {
        store.observeKey("app_preferences") { prefs ->
            prefs[booleanPreferencesKey("onboarding_completed")]?.let {
                _isOnboardingCompleted.value = it
            }
            prefs[booleanPreferencesKey("auto_update_check_enabled")]?.let {
                _autoUpdateCheckEnabled.value = it
            }
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        _isOnboardingCompleted.value = completed
        store.putBoolean("onboarding_completed", completed)
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        _autoUpdateCheckEnabled.value = enabled
        store.putBoolean("auto_update_check_enabled", enabled)
    }

    fun getLastSeenVersionCode(): Long = store.getLong("last_seen_version_code", 0L)

    fun updateLastSeenVersionCode(versionCode: Long) {
        store.putLong("last_seen_version_code", versionCode)
    }

    fun isFeatureDiscoveryCompleted(version: Int): Boolean {
        return store.getBoolean(
            key = "feature_discovery_completed_v$version",
            default = false
        )
    }

    fun setFeatureDiscoveryCompleted(version: Int, completed: Boolean) {
        store.putBoolean(
            key = "feature_discovery_completed_v$version",
            value = completed
        )
    }

    fun isFeatureDiscoverySkipped(version: Int): Boolean {
        return store.getBoolean(
            key = "feature_discovery_skipped_v$version",
            default = false
        )
    }

    fun setFeatureDiscoverySkipped(version: Int, skipped: Boolean) {
        store.putBoolean(
            key = "feature_discovery_skipped_v$version",
            value = skipped
        )
    }

    fun resetFeatureDiscovery(version: Int) {
        setFeatureDiscoveryCompleted(version, false)
        setFeatureDiscoverySkipped(version, false)
    }

    fun isFeatureDiscoveryGloballyDismissed(): Boolean {
        return store.getBoolean("globally_dismissed", default = false)
    }

    fun setFeatureDiscoveryGloballyDismissed(dismissed: Boolean) {
        store.putBoolean("globally_dismissed", dismissed)
    }

    fun isFeatureDiscoveryScreenCompleted(screenIdName: String): Boolean {
        return store.getBoolean("completed_${screenIdName.lowercase()}", default = false)
    }

    fun setFeatureDiscoveryScreenCompleted(screenIdName: String, completed: Boolean) {
        store.putBoolean("completed_${screenIdName.lowercase()}", completed)
    }

    fun resetAllFeatureDiscovery() {
        setFeatureDiscoveryGloballyDismissed(false)
    }

    fun getPrivilegeMode(): com.framex.app.privilege.PrivilegeMode {
        val raw = store.getString("privilege_mode", com.framex.app.privilege.PrivilegeMode.NONE.key)
        return com.framex.app.privilege.PrivilegeMode.fromKey(raw)
    }

    fun setPrivilegeMode(mode: com.framex.app.privilege.PrivilegeMode) {
        store.putString("privilege_mode", mode.key)
    }

    /** Suspends until the DataStore has been read; cold start paths must call this first. */
    suspend fun awaitLoaded() = store.awaitLoaded()
}
