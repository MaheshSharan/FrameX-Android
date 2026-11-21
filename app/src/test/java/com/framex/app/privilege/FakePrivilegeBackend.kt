package com.framex.app.privilege

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scriptable backend for JVM tests. Commands only succeed while the fake is connected. */
internal class FakePrivilegeBackend(
    override val mode: PrivilegeMode,
    private val connectsOnStart: Boolean = true
) : PrivilegeBackend {

    private val _state = MutableStateFlow<BackendState>(BackendState.Idle)
    override val state: StateFlow<BackendState> = _state

    var startCalls = 0
    var stopCalls = 0
    var execCalls = 0

    /** Overrides the result for a command while connected. Telemetry uses the key [CPU_KEY]. */
    var handler: ((String) -> PrivilegedOutcome)? = null

    override fun start() {
        startCalls++
        _state.value = if (connectsOnStart) {
            BackendState.Connected
        } else {
            BackendState.NeedsUserAction(ActionReason.NO_ROOT_GRANT)
        }
    }

    override fun stop() {
        stopCalls++
        _state.value = BackendState.Idle
    }

    override fun refresh() = Unit

    override suspend fun requestAccess() = Unit

    override suspend fun exec(command: String, timeoutMs: Long): PrivilegedOutcome {
        execCalls++
        if (_state.value !is BackendState.Connected) return PrivilegedOutcome.PermissionDenied("not connected")
        return handler?.invoke(command) ?: PrivilegedOutcome.Success("")
    }

    override suspend fun readCpuTelemetry(): PrivilegedOutcome = exec(CPU_KEY, PrivilegeBackend.DEFAULT_TIMEOUT_MS)

    override suspend fun readThermalTemperatures(): PrivilegedOutcome = exec(THERMAL_KEY, PrivilegeBackend.DEFAULT_TIMEOUT_MS)

    override suspend fun suspendPackages(packages: List<String>, suspended: Boolean): SuspendOutcome? = null

    override suspend fun setAppOpMode(packages: List<String>, opCode: Int, mode: Int): Int = 0

    companion object {
        const val CPU_KEY = "__cpu__"
        const val THERMAL_KEY = "__thermal__"
    }
}
