package com.framex.app.ui.featurediscovery

import com.framex.app.repository.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists which Feature Discovery coach marks the person has completed or dismissed, via
 * [AppPreferencesRepository].
 */
@Singleton
class FeatureDiscoveryRepository @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository
) {
    fun isGloballyDismissed(): Boolean = appPreferencesRepository.isFeatureDiscoveryGloballyDismissed()

    fun isScreenCompleted(screenId: DiscoveryScreenId): Boolean =
        appPreferencesRepository.isFeatureDiscoveryScreenCompleted(screenId.name)

    fun shouldAutoShow(screenId: DiscoveryScreenId): Boolean {
        if (isGloballyDismissed()) return false
        return !isScreenCompleted(screenId)
    }

    fun markScreenCompleted(screenId: DiscoveryScreenId) {
        appPreferencesRepository.setFeatureDiscoveryScreenCompleted(screenId.name, true)
    }

    fun markGloballyDismissed() {
        appPreferencesRepository.setFeatureDiscoveryGloballyDismissed(true)
    }
}
