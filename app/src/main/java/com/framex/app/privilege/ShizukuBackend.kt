package com.framex.app.privilege

import com.framex.app.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs privileged work through the Shizuku user service. The per-command limit inside that
 * service is fixed by the AIDL contract (10 s); [exec] only widens how long a caller waits.
 */
@Singleton
class ShizukuBackend @Inject constructor(
    private val manager: ShizukuManager
) : PrivilegeBackend {

    override val mode: PrivilegeMode = PrivilegeMode.SHIZUKU

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = MutableStateFlow(false)

    override val state: StateFlow<BackendState> = combine(
        started,
        manager.isShizukuAvailable,
        manager.hasPermission
    ) { isStarted, available, permitted ->
        when {
            !isStarted -> BackendState.Idle
            !available -> BackendState.NeedsUserAction(ActionReason.SHIZUKU_NOT_RUNNING)
            !permitted -> BackendState.NeedsUserAction(ActionReason.SHIZUKU_PERMISSION_DENIED)
            else -> BackendState.Connected
        }
    }.stateIn(scope, SharingStarted.Eagerly, BackendState.Idle)

    override fun start() {
        if (started.value) return
        manager.init()
        started.value = true
    }

    override fun stop() {
        if (!started.value) return
        started.value = false
        manager.destroy()
    }

    override fun refresh() {
        if (started.value) manager.refreshState()
    }

    override suspend fun requestAccess() {
        manager.requestPermission()
    }

    override suspend fun exec(command: String, timeoutMs: Long): PrivilegedOutcome {
        val result = manager.executeCommandWithResult(command, timeoutMs + QUEUE_SLACK_MS)
            ?: return PrivilegedOutcome.BinderDisconnected
        return PrivilegedOutcome.fromExit(result.exitCode, result.output, timeoutMs)
    }

    override suspend fun readCpuTelemetry(): PrivilegedOutcome =
        telemetryOutcome(manager.readCpuTelemetry())

    override suspend fun readThermalTemperatures(): PrivilegedOutcome =
        telemetryOutcome(manager.getThermalTemperatures())

    override suspend fun suspendPackages(packages: List<String>, suspended: Boolean): SuspendOutcome? {
        val result = manager.suspendPackages(packages, suspended) ?: return null
        return SuspendOutcome(result.failedPackages.orEmpty().toList(), result.successCount)
    }

    override suspend fun setAppOpMode(packages: List<String>, opCode: Int, mode: Int): Int =
        manager.setAppOpMode(packages, opCode, mode)

    /** The user service returns an empty string when a read failed or timed out. */
    private fun telemetryOutcome(output: String): PrivilegedOutcome =
        if (output.isBlank()) {
            PrivilegedOutcome.NonZeroExit(PrivilegedOutcome.EXIT_UNAVAILABLE, "")
        } else {
            PrivilegedOutcome.Success(output)
        }

    private companion object {
        const val QUEUE_SLACK_MS = 5_000L
    }
}
