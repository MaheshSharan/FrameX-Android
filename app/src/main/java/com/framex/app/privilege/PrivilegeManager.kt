package com.framex.app.privilege

import com.framex.app.privilege.root.RootBackend
import com.framex.app.repository.SettingsRepository
import com.framex.app.utils.FrameXLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets the owner of applied system changes (Gaming Mode) undo them before the backend is
 * switched. Returns true only when nothing is left applied.
 */
fun interface PrivilegeSwitchGuard {
    suspend fun revertAppliedChanges(): Boolean
}

/** Result of asking to change the privilege mode. */
enum class SwitchResult { SWITCHED, BLOCKED_GAMING_ACTIVE, BLOCKED_RECOVERY_PENDING }

/**
 * The only entry point for privileged work. The selected mode is authoritative: a command
 * goes to that backend or fails, it is never retried through the other one.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PrivilegeManager internal constructor(
    private val settings: SettingsRepository,
    private val shizuku: PrivilegeBackend,
    private val root: PrivilegeBackend,
    private val scope: CoroutineScope
) {
    @Inject
    constructor(
        settings: SettingsRepository,
        shizuku: ShizukuBackend,
        root: RootBackend
    ) : this(settings, shizuku, root, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    @Volatile
    private var switchGuard: PrivilegeSwitchGuard? = null
    private val switchLock = Mutex()

    private val _currentMode = MutableStateFlow(PrivilegeMode.NONE)
    val currentMode: StateFlow<PrivilegeMode> = _currentMode.asStateFlow()

    private val _capabilities = MutableStateFlow<PrivilegeCapabilities?>(null)
    val capabilities: StateFlow<PrivilegeCapabilities?> = _capabilities.asStateFlow()

    val backendState: StateFlow<BackendState> = _currentMode
        .flatMapLatest { backendFor(it)?.state ?: MutableStateFlow(BackendState.Idle) }
        .stateIn(scope, SharingStarted.Eagerly, BackendState.Idle)

    val isReady: StateFlow<Boolean> = backendState
        .map { it is BackendState.Connected }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** True when the selected backend exists on the device, even if access is not granted yet. */
    val isAvailable: StateFlow<Boolean> = backendState
        .map {
            it is BackendState.Connected || it is BackendState.Connecting ||
                (it is BackendState.NeedsUserAction && it.reason != ActionReason.SHIZUKU_NOT_RUNNING)
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Loads the saved mode and starts only that backend. Call once from Application. */
    fun start() {
        scope.launch {
            settings.awaitSettingsLoaded()
            activate(settings.appPrefs.getPrivilegeMode())
        }
    }

    fun registerSwitchGuard(guard: PrivilegeSwitchGuard) {
        switchGuard = guard
    }

    suspend fun switchMode(target: PrivilegeMode): SwitchResult = switchLock.withLock {
        if (target == _currentMode.value) return SwitchResult.SWITCHED
        settings.awaitSettingsLoaded()
        if (settings.isGamingModeActive()) {
            // Undo on the backend that applied the changes; refuse if that does not finish.
            val reverted = switchGuard?.revertAppliedChanges() == true
            if (!reverted || settings.isGamingModeActive()) return SwitchResult.BLOCKED_GAMING_ACTIVE
        }
        if (settings.needsThermalOverrideRecovery()) return SwitchResult.BLOCKED_RECOVERY_PENDING
        settings.appPrefs.setPrivilegeMode(target)
        activate(target)
        SwitchResult.SWITCHED
    }

    /**
     * Runs [block] with [mode] active, then restores the selected mode. Used only for crash
     * recovery, which must undo changes through the backend that made them. Returns null when
     * that backend does not become ready in time.
     */
    suspend fun <T> runWithBackend(
        mode: PrivilegeMode,
        timeoutMs: Long = RECOVERY_CONNECT_TIMEOUT_MS,
        block: suspend () -> T
    ): T? = switchLock.withLock {
        val selected = _currentMode.value
        if (mode == selected) return@withLock block()
        activate(mode)
        try {
            val backend = backendFor(mode)
            val ready = withTimeoutOrNull(timeoutMs) {
                backend?.state?.first { it is BackendState.Connected }
                // isReady follows the backend state asynchronously; wait until it agrees.
                isReady.first { it }
            }
            if (ready == null) null else block()
        } finally {
            activate(selected)
        }
    }

    /** Waits briefly for the selected backend, e.g. while the root shell restarts after a timeout. */
    suspend fun awaitReady(timeoutMs: Long): Boolean =
        isReady.value || kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { isReady.first { it } } == true

    fun refresh() {
        backendFor(_currentMode.value)?.refresh()
    }

    suspend fun requestAccess() {
        backendFor(_currentMode.value)?.requestAccess()
        if (isReady.value) probeCapabilities()
    }

    suspend fun exec(command: String, timeoutMs: Long = PrivilegeBackend.DEFAULT_TIMEOUT_MS): PrivilegedOutcome =
        backendFor(_currentMode.value)?.exec(command, timeoutMs)
            ?: PrivilegedOutcome.PermissionDenied("No privilege mode selected")

    /** Command output only; empty when the command could not run. */
    suspend fun execText(command: String, timeoutMs: Long = PrivilegeBackend.DEFAULT_TIMEOUT_MS): String =
        exec(command, timeoutMs).output

    /** Exit status; negative values mean the command did not run to completion. */
    suspend fun execExit(command: String, timeoutMs: Long = PrivilegeBackend.DEFAULT_TIMEOUT_MS): Int =
        exec(command, timeoutMs).exitCode

    /** Reads a file as the privileged user; empty when it cannot be read. */
    suspend fun readSystemFile(path: String): String {
        val outcome = exec("cat '${path.replace("'", "'\\''")}'", READ_TIMEOUT_MS)
        return if (outcome.isSuccess) outcome.output.trim() else ""
    }

    suspend fun readCpuTelemetry(): PrivilegedOutcome =
        backendFor(_currentMode.value)?.readCpuTelemetry()
            ?: PrivilegedOutcome.PermissionDenied("No privilege mode selected")

    suspend fun readThermalTemperatures(): PrivilegedOutcome =
        backendFor(_currentMode.value)?.readThermalTemperatures()
            ?: PrivilegedOutcome.PermissionDenied("No privilege mode selected")

    suspend fun suspendPackages(packages: List<String>, suspended: Boolean): SuspendOutcome? =
        backendFor(_currentMode.value)?.suspendPackages(packages, suspended)

    suspend fun setAppOpMode(packages: List<String>, opCode: Int, mode: Int): Int =
        backendFor(_currentMode.value)?.setAppOpMode(packages, opCode, mode) ?: 0

    private suspend fun probeCapabilities() {
        val backend = backendFor(_currentMode.value) ?: return
        _capabilities.value = runCatching { CapabilityProbe.run(backend) }
            .onFailure { FrameXLog.w("Capability probe failed: ${it.message}") }
            .getOrNull()
    }

    private fun activate(target: PrivilegeMode) {
        val previous = backendFor(_currentMode.value)
        val next = backendFor(target)
        if (previous !== next) previous?.stop()
        _capabilities.value = null
        _currentMode.value = target
        next?.start()
        if (next != null) {
            scope.launch {
                next.state.collect { if (it is BackendState.Connected) { probeCapabilities(); return@collect } }
            }
        }
    }

    private fun backendFor(mode: PrivilegeMode): PrivilegeBackend? = when (mode) {
        PrivilegeMode.SHIZUKU -> shizuku
        PrivilegeMode.ROOT -> root
        PrivilegeMode.NONE -> null
    }

    private companion object {
        const val READ_TIMEOUT_MS = 3_000L
        const val RECOVERY_CONNECT_TIMEOUT_MS = 35_000L
    }
}
