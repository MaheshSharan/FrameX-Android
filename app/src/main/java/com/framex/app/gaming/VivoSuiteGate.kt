package com.framex.app.gaming

import com.framex.app.device.DeviceDiagnosticManager
import com.framex.app.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Execution path selected for platform-specific gaming optimizations.
 */
enum class GamingPlatformPath {
    VIVO,
    GENERIC,
    NONE
}

/**
 * Pure function mapping hardware identity and user toggle state to a platform routing path.
 *
 * Truth table:
 * - Vivo hardware + toggle ON  -> VIVO
 * - Vivo hardware + toggle OFF -> NONE
 * - Non-Vivo hardware (any)    -> GENERIC
 */
fun resolvePlatformPath(isVivoHardware: Boolean, toggleOn: Boolean): GamingPlatformPath {
    return when {
        !isVivoHardware -> GamingPlatformPath.GENERIC
        toggleOn -> GamingPlatformPath.VIVO
        else -> GamingPlatformPath.NONE
    }
}

/**
 * Master gate for the Vivo / iQOO hardware optimization suite.
 * Enforces a single source of truth for hardware presence and user enablement.
 */
@Singleton
class VivoSuiteGate @Inject constructor(
    private val deviceDiagnosticManager: DeviceDiagnosticManager,
    private val settingsRepository: SettingsRepository
) {
    val isVivoHardware: Boolean
        get() = deviceDiagnosticManager.isVivoOrIqoo()

    val isVivoSuiteEnabled: Boolean
        get() = isVivoHardware && settingsRepository.vivoOptEnabled.value

    val isVivoSuiteEnabledFlow: StateFlow<Boolean> = if (deviceDiagnosticManager.isVivoOrIqoo()) {
        settingsRepository.vivoOptEnabled
    } else {
        MutableStateFlow(false)
    }

    fun resolveCurrentPlatformPath(): GamingPlatformPath =
        resolvePlatformPath(isVivoHardware, settingsRepository.vivoOptEnabled.value)
}
