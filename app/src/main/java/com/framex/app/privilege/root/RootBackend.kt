package com.framex.app.privilege.root

import com.framex.app.privilege.ActionReason
import com.framex.app.privilege.BackendState
import com.framex.app.privilege.PrivilegeBackend
import com.framex.app.privilege.PrivilegeMode
import com.framex.app.privilege.PrivilegedOutcome
import com.framex.app.privilege.SuspendOutcome
import com.framex.app.privilege.scripts.CpuTelemetryScript
import com.framex.app.privilege.scripts.PackageCommands
import com.framex.app.privilege.scripts.ThermalScripts
import com.framex.app.utils.FrameXLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs privileged work through one persistent root shell. Nothing here touches Shizuku.
 *
 * The scope is process-wide because the shell must outlive any screen; it is shut down by
 * [stop], which closes the shell, so no root process is left behind when root mode is left.
 * Magisk and KernelSU accept a mount-master flag so the shell sees the global mount
 * namespace; plain `su` is the last candidate for other root managers.
 */
@Singleton
class RootBackend @Inject constructor() : PrivilegeBackend {

    override val mode: PrivilegeMode = PrivilegeMode.ROOT

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val shell = RootShell(SU_CANDIDATES, scope)
    private val _state = MutableStateFlow<BackendState>(BackendState.Idle)
    override val state: StateFlow<BackendState> = _state.asStateFlow()

    private var connectJob: Job? = null

    @Volatile
    private var thermalStrategy: ThermalStrategy? = null

    override fun start() {
        if (_state.value is BackendState.Connecting || _state.value is BackendState.Connected) return
        _state.value = BackendState.Connecting
        connectJob = scope.launch {
            _state.value = if (shell.open()) {
                BackendState.Connected
            } else {
                BackendState.NeedsUserAction(ActionReason.NO_ROOT_GRANT)
            }
        }
    }

    override fun stop() {
        connectJob?.cancel()
        connectJob = null
        _state.value = BackendState.Idle
        scope.launch { shell.close() }
    }

    override fun refresh() {
        if (_state.value is BackendState.NeedsUserAction) start()
    }

    override suspend fun requestAccess() {
        start()
        connectJob?.join()
    }

    override suspend fun exec(command: String, timeoutMs: Long): PrivilegedOutcome {
        if (_state.value !is BackendState.Connected) {
            return PrivilegedOutcome.PermissionDenied("Root is not connected")
        }
        val outcome = shell.exec(command, timeoutMs)
        if (outcome === PrivilegedOutcome.BinderDisconnected) {
            FrameXLog.w("Root shell unavailable; waiting for a manual retry", tag = TAG)
            _state.value = BackendState.NeedsUserAction(ActionReason.NO_ROOT_GRANT)
        }
        return outcome
    }

    override suspend fun readCpuTelemetry(): PrivilegedOutcome =
        exec(CpuTelemetryScript.COMMAND, TELEMETRY_TIMEOUT_MS)

    override suspend fun readThermalTemperatures(): PrivilegedOutcome {
        val body = readThermalBody() ?: return PrivilegedOutcome.NonZeroExit(PrivilegedOutcome.EXIT_UNAVAILABLE, "")
        val status = exec(ThermalScripts.STATUS_COMMAND, TELEMETRY_TIMEOUT_MS).output
        return PrivilegedOutcome.Success(if (status.isBlank()) body else "$body\n$status\n")
    }

    override suspend fun suspendPackages(packages: List<String>, suspended: Boolean): SuspendOutcome? {
        if (_state.value !is BackendState.Connected) return null
        return PackageCommands.applySuspend(packages, suspended) { command ->
            val outcome = exec(command, BATCH_TIMEOUT_MS)
            outcome.exitCode to outcome.output
        }
    }

    override suspend fun setAppOpMode(packages: List<String>, opCode: Int, mode: Int): Int {
        val valid = PackageCommands.validPackages(packages)
        if (valid.isEmpty()) return 0
        val outcome = exec(PackageCommands.appOpBatch(valid, opCode, mode), BATCH_TIMEOUT_MS)
        return PackageCommands.countAppOpSuccesses(outcome.output)
    }

    /** Uses the strategy that worked last time and re-discovers when it stops yielding data. */
    private suspend fun readThermalBody(): String? {
        thermalStrategy?.let { cached ->
            readStrategy(cached)?.let { return it }
            thermalStrategy = null
        }
        for (strategy in ThermalStrategy.values()) {
            val body = readStrategy(strategy) ?: continue
            thermalStrategy = strategy
            return body
        }
        return null
    }

    private suspend fun readStrategy(strategy: ThermalStrategy): String? = when (strategy) {
        ThermalStrategy.DUMPSYS -> exec(ThermalScripts.DUMPSYS_COMMAND, TELEMETRY_TIMEOUT_MS)
            .output.takeIf { ThermalScripts.dumpHasSensorData(it) }
        ThermalStrategy.SYSFS -> exec(ThermalScripts.SYSFS_COMMAND, TELEMETRY_TIMEOUT_MS)
            .output.takeIf { ThermalScripts.sysfsHasSensorData(it) }
    }

    private enum class ThermalStrategy { DUMPSYS, SYSFS }

    private companion object {
        const val TAG = "RootBackend"
        const val TELEMETRY_TIMEOUT_MS = 6_000L
        const val BATCH_TIMEOUT_MS = 25_000L
        val SU_CANDIDATES = listOf(listOf("su", "-M"), listOf("su", "-mm"), listOf("su"))
    }
}
