package com.framex.app.ui.featurediscovery

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureDiscoveryRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isGloballyDismissed = MutableStateFlow(prefs.getBoolean(KEY_GLOBALLY_DISMISSED, false))
    val isGloballyDismissed: StateFlow<Boolean> = _isGloballyDismissed.asStateFlow()

    fun isGloballyDismissed(): Boolean = _isGloballyDismissed.value

    fun isScreenCompleted(screenId: DiscoveryScreenId): Boolean {
        return prefs.getBoolean(screenCompletedKey(screenId), false)
    }

    fun shouldAutoShow(screenId: DiscoveryScreenId): Boolean {
        if (isGloballyDismissed()) return false
        return !isScreenCompleted(screenId)
    }

    fun markScreenCompleted(screenId: DiscoveryScreenId) {
        prefs.edit()
            .putBoolean(screenCompletedKey(screenId), true)
            .apply()
    }

    fun markGloballyDismissed() {
        prefs.edit().putBoolean(KEY_GLOBALLY_DISMISSED, true).apply()
        _isGloballyDismissed.value = true
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        _isGloballyDismissed.value = false
    }

    private fun screenCompletedKey(screenId: DiscoveryScreenId): String =
        "completed_${screenId.name.lowercase()}"

    companion object {
        private const val PREFS_NAME = "framex_feature_discovery"
        private const val KEY_GLOBALLY_DISMISSED = "globally_dismissed"
    }
}
