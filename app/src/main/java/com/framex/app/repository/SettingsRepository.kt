package com.framex.app.repository

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade delegating preference operations to the four domain-scoped DataStore repositories:
 * [OverlaySettingsRepository], [GamingSettingsRepository],
 * [ExecutionCenterSettingsRepository], and [AppPreferencesRepository].
 */
@Singleton
class SettingsRepository @Inject constructor(
    val overlay: OverlaySettingsRepository,
    val gaming: GamingSettingsRepository,
    val executionCenter: ExecutionCenterSettingsRepository,
    val appPrefs: AppPreferencesRepository
) {
    private constructor(store: FrameXPreferenceStore) : this(
        overlay = OverlaySettingsRepository(store),
        gaming = GamingSettingsRepository(store),
        executionCenter = ExecutionCenterSettingsRepository(store),
        appPrefs = AppPreferencesRepository(store)
    )

    // --- Overlay domain ---
    val overlayMode: StateFlow<String> get() = overlay.overlayMode
    val enabledModules: StateFlow<Set<String>> get() = overlay.enabledModules
    val enabledModuleIcons: StateFlow<Set<String>> get() = overlay.enabledModuleIcons
    val cpuHotWarningEnabled: StateFlow<Boolean> get() = overlay.cpuHotWarningEnabled
    val moduleOrder: StateFlow<List<String>> get() = overlay.moduleOrder
    val overlayOpacity: StateFlow<Float> get() = overlay.overlayOpacity
    val overlayTextSize: StateFlow<Int> get() = overlay.overlayTextSize
    val overlayScale: StateFlow<Float> get() = overlay.overlayScale
    val overlayUseMonospace: StateFlow<Boolean> get() = overlay.overlayUseMonospace
    val overlayColorIndex: StateFlow<Int> get() = overlay.overlayColorIndex
    val overlayBgColorIndex: StateFlow<Int> get() = overlay.overlayBgColorIndex
    val overlayBorderColorIndex: StateFlow<Int> get() = overlay.overlayBorderColorIndex
    val overlayTextColorIndex: StateFlow<Int> get() = overlay.overlayTextColorIndex

    fun setOverlayMode(mode: String) = overlay.setOverlayMode(mode)
    fun setEnabledModules(modules: Set<String>) = overlay.setEnabledModules(modules)
    fun toggleModule(module: String, enabled: Boolean) = overlay.toggleModule(module, enabled)
    fun setEnabledModuleIcons(icons: Set<String>) = overlay.setEnabledModuleIcons(icons)
    fun toggleModuleIcon(module: String, enabled: Boolean) = overlay.toggleModuleIcon(module, enabled)
    fun setCpuHotWarningEnabled(enabled: Boolean) = overlay.setCpuHotWarningEnabled(enabled)
    fun setModuleOrder(order: List<String>) = overlay.setModuleOrder(order)
    fun setOverlayOpacity(opacity: Float) = overlay.setOverlayOpacity(opacity)
    fun setOverlayTextSize(size: Int) = overlay.setOverlayTextSize(size)
    fun setOverlayScale(scale: Float) = overlay.setOverlayScale(scale)
    fun setOverlayUseMonospace(useMonospace: Boolean) = overlay.setOverlayUseMonospace(useMonospace)
    fun setOverlayColorIndex(index: Int) = overlay.setOverlayColorIndex(index)
    fun setOverlayBgColorIndex(index: Int) = overlay.setOverlayBgColorIndex(index)
    fun setOverlayBorderColorIndex(index: Int) = overlay.setOverlayBorderColorIndex(index)
    fun setOverlayTextColorIndex(index: Int) = overlay.setOverlayTextColorIndex(index)
    fun resetOverlayToDefaults() = overlay.resetOverlayToDefaults()
    fun getOverlayPosition(isLandscape: Boolean): Pair<Int, Int> = overlay.getOverlayPosition(isLandscape)
    fun setOverlayPosition(isLandscape: Boolean, x: Int, y: Int) = overlay.setOverlayPosition(isLandscape, x, y)
    fun wasOverlayRunning(): Boolean = overlay.wasOverlayRunning()
    fun setOverlayWasRunning(running: Boolean) = overlay.setOverlayWasRunning(running)

    // --- Gaming domain ---
    val gamingModeWhitelist: StateFlow<Set<String>> get() = gaming.gamingModeWhitelist
    val launcherGames: StateFlow<Set<String>> get() = gaming.launcherGames
    val deepFreezeEnabled: StateFlow<Boolean> get() = gaming.deepFreezeEnabled
    val hasSeenDeepFreezeNotice: StateFlow<Boolean> get() = gaming.hasSeenDeepFreezeNotice
    val isGamingModeActiveFlow: StateFlow<Boolean> get() = gaming.isGamingModeActiveFlow

    fun toggleGamingWhitelistApp(packageName: String) = gaming.toggleGamingWhitelistApp(packageName)
    fun setLauncherGames(games: Set<String>) = gaming.setLauncherGames(games)
    fun toggleLauncherGame(packageName: String) = gaming.toggleLauncherGame(packageName)
    fun setDeepFreezeEnabled(enabled: Boolean) = gaming.setDeepFreezeEnabled(enabled)
    fun setHasSeenDeepFreezeNotice(seen: Boolean) = gaming.setHasSeenDeepFreezeNotice(seen)
    fun isGamingModeActive(): Boolean = gaming.isGamingModeActive()
    fun setGamingModeActive(active: Boolean) = gaming.setGamingModeActive(active)
    fun getGamingAppliedVia() = gaming.getAppliedVia()
    fun setGamingAppliedVia(mode: com.framex.app.privilege.PrivilegeMode?) = gaming.setAppliedVia(mode)
    fun getGamingAffectedPackages(): Set<String> = gaming.getGamingAffectedPackages()
    fun setGamingAffectedPackages(packages: Set<String>) = gaming.setGamingAffectedPackages(packages)
    fun getGamingBaselines(): Map<String, String> = gaming.getGamingBaselines()
    fun setGamingBaselines(baselines: Map<String, String>) = gaming.setGamingBaselines(baselines)
    fun getGamingActiveGamePackage(): String? = gaming.getGamingActiveGamePackage()
    fun getGamingActiveGameUid(): Int? = gaming.getGamingActiveGameUid()
    fun setGamingActiveGame(packageName: String?, uid: Int?) = gaming.setGamingActiveGame(packageName, uid)
    fun needsThermalOverrideRecovery(): Boolean = gaming.needsThermalOverrideRecovery()
    fun setNeedsThermalOverrideActive(active: Boolean) = gaming.setNeedsThermalOverrideActive(active)
    fun markThermalOverrideRecoveryComplete() = gaming.markThermalOverrideRecoveryComplete()
    fun needsLegacySettingsCleanup(): Boolean = gaming.needsLegacySettingsCleanup()
    fun markLegacySettingsCleanupComplete() = gaming.markLegacySettingsCleanupComplete()
    fun cleanupLegacyPreferences() = gaming.cleanupLegacyPreferences()
    fun getGameConfigBoostRam(pkg: String): Boolean = gaming.getGameConfigBoostRam(pkg)
    fun setGameConfigBoostRam(pkg: String, enabled: Boolean) = gaming.setGameConfigBoostRam(pkg, enabled)

    // --- Execution Center domain ---
    val disableThermalThrottling: StateFlow<Boolean> get() = executionCenter.disableThermalThrottling
    val ramCachePreTrimEnabled: StateFlow<Boolean> get() = executionCenter.ramCachePreTrimEnabled
    val disablePhantomProcKiller: StateFlow<Boolean> get() = executionCenter.disablePhantomProcKiller
    val cpuPriorityLock: StateFlow<Boolean> get() = executionCenter.cpuPriorityLock
    val fixedPerformanceMode: StateFlow<Boolean> get() = executionCenter.fixedPerformanceMode
    val networkFirewall: StateFlow<Boolean> get() = executionCenter.networkFirewall
    val refreshRateLock: StateFlow<Boolean> get() = executionCenter.refreshRateLock
    val touchBoost: StateFlow<Boolean> get() = executionCenter.touchBoost
    val auditLoggingEnabled: StateFlow<Boolean> get() = executionCenter.auditLoggingEnabled

    fun setDisableThermalThrottling(enabled: Boolean) = executionCenter.setDisableThermalThrottling(enabled)
    fun setRamCachePreTrimEnabled(enabled: Boolean) = executionCenter.setRamCachePreTrimEnabled(enabled)
    fun setDisablePhantomProcKiller(enabled: Boolean) = executionCenter.setDisablePhantomProcKiller(enabled)
    fun setCpuPriorityLock(enabled: Boolean) = executionCenter.setCpuPriorityLock(enabled)
    fun setFixedPerformanceMode(enabled: Boolean) = executionCenter.setFixedPerformanceMode(enabled)
    fun setNetworkFirewall(enabled: Boolean) = executionCenter.setNetworkFirewall(enabled)
    fun setRefreshRateLock(enabled: Boolean) = executionCenter.setRefreshRateLock(enabled)
    fun setTouchBoost(enabled: Boolean) = executionCenter.setTouchBoost(enabled)
    fun setAuditLoggingEnabled(enabled: Boolean) = executionCenter.setAuditLoggingEnabled(enabled)

    // --- App Preferences domain ---
    val isOnboardingCompleted: StateFlow<Boolean> get() = appPrefs.isOnboardingCompleted
    val autoUpdateCheckEnabled: StateFlow<Boolean> get() = appPrefs.autoUpdateCheckEnabled

    fun setOnboardingCompleted(completed: Boolean) = appPrefs.setOnboardingCompleted(completed)
    fun setAutoUpdateCheckEnabled(enabled: Boolean) = appPrefs.setAutoUpdateCheckEnabled(enabled)
    fun getLastSeenVersionCode(): Long = appPrefs.getLastSeenVersionCode()
    fun updateLastSeenVersionCode(versionCode: Long) = appPrefs.updateLastSeenVersionCode(versionCode)

    suspend fun awaitSettingsLoaded() = appPrefs.awaitLoaded()
    suspend fun flushRecoveryWrites() = gaming.flushWrites()

    companion object {
        /** Settings backed only by memory, for JVM unit tests that have no DataStore. */
        fun inMemory(): SettingsRepository = SettingsRepository(FrameXPreferenceStore())
    }
}
