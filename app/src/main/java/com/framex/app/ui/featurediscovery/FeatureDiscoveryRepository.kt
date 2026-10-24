package com.framex.app.ui.featurediscovery

import com.framex.app.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistence and runtime trigger requests for the in-app Feature Discovery Coach-Mark tour
 * via [AppPreferencesRepository].
 */
@Singleton
class FeatureDiscoveryRepository @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository
) {
    private val _replayTrigger = MutableStateFlow(0L)
    val replayTrigger: StateFlow<Long> = _replayTrigger.asStateFlow()

    fun shouldShowTour(): Boolean {
        val completed = appPreferencesRepository.isFeatureDiscoveryCompleted(TOUR_VERSION)
        val skipped = appPreferencesRepository.isFeatureDiscoverySkipped(TOUR_VERSION)
        return !completed && !skipped
    }

    fun markCompleted() {
        appPreferencesRepository.setFeatureDiscoveryCompleted(TOUR_VERSION, true)
    }

    fun markSkipped() {
        appPreferencesRepository.setFeatureDiscoverySkipped(TOUR_VERSION, true)
    }

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

    fun resetAll() {
        appPreferencesRepository.resetAllFeatureDiscovery()
    }

    companion object {
        private const val TOUR_VERSION = 1
    }
}
