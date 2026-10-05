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
    IQOO,
    GENERIC,
    NONE
}

/**
 * Pure function mapping hardware identity and user toggle state to a platform routing path.
 *
 * Truth table:
 * - iQOO hardware + toggle ON  -> IQOO
 * - Vivo hardware + toggle ON  -> VIVO
 * - Either hardware + toggle OFF -> NONE
 * - Non-Vivo/iQOO hardware (any) -> GENERIC
 */
fun resolvePlatformPath(
    isVivoHardware: Boolean,
    isIqooHardware: Boolean,
    toggleOn: Boolean
): GamingPlatformPath {
    return when {
        isIqooHardware && toggleOn -> GamingPlatformPath.IQOO
        isVivoHardware && toggleOn -> GamingPlatformPath.VIVO
        (isVivoHardware || isIqooHardware) && !toggleOn -> GamingPlatformPath.NONE
        else -> GamingPlatformPath.GENERIC
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
        get() = deviceDiagnosticManager.isVivoOnly()

    val isIqooHardware: Boolean
        get() = deviceDiagnosticManager.isIqooOnly()

    val isVivoSuiteEnabled: Boolean
        get() = (isVivoHardware || isIqooHardware) && settingsRepository.vivoOptEnabled.value

    val isVivoSuiteEnabledFlow: StateFlow<Boolean> = if (deviceDiagnosticManager.isVivoOrIqoo()) {
        settingsRepository.vivoOptEnabled
    } else {
        MutableStateFlow(false)
    }

    fun resolveCurrentPlatformPath(): GamingPlatformPath =
        resolvePlatformPath(isVivoHardware, isIqooHardware, settingsRepository.vivoOptEnabled.value)
}
