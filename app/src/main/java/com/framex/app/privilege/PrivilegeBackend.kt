package com.framex.app.privilege

import kotlinx.coroutines.flow.StateFlow

/**
 * One way of running commands with elevated rights. Exactly one backend is active at a time;
 * [PrivilegeManager] never falls back from one to the other.
 */
interface PrivilegeBackend {
    val mode: PrivilegeMode
    val state: StateFlow<BackendState>

    /** Starts observing or connecting. Safe to call repeatedly. */
    fun start()

    /** Releases the connection and any process owned by the backend. */
    fun stop()

    /** Re-evaluates the connection, for example after returning from a permission dialog. */
    fun refresh()

    /** Asks the person for the grant this backend needs, when it has not been given yet. */
    suspend fun requestAccess()

    suspend fun exec(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): PrivilegedOutcome

    /** CPU lines in the `STAT:`, `POLICY:` and `CORE:` format read by `CpuTopologyParser`. */
    suspend fun readCpuTelemetry(): PrivilegedOutcome

    suspend fun readThermalTemperatures(): PrivilegedOutcome

    suspend fun suspendPackages(packages: List<String>, suspended: Boolean): SuspendOutcome?

    /** Returns how many packages were changed. */
    suspend fun setAppOpMode(packages: List<String>, opCode: Int, mode: Int): Int

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
