package com.framex.app.privilege

import android.os.Build
import com.framex.app.gaming.catalog.CapableLookup
import com.framex.app.gaming.catalog.CommandEntry
import com.framex.app.gaming.catalog.CommandTarget
import com.framex.app.gaming.catalog.GamingCommandCatalog
import com.framex.app.gaming.catalog.ParallelShell
import com.framex.app.utils.FrameXLog
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether a catalog entry's key/path/service exists on this device, and whether it is writable. */
data class CapabilityResult(
    val exists: Boolean,
    val writable: Boolean,
    val risky: Boolean = false,
    val probedAt: Instant = Instant.now()
)

/**
 * Generic, per-device replacement for brand-based gating (#117/#118/#120/#126): probes every
 * [GamingCommandCatalog] entry once per connect (or on a manual rescan) and caches a yes/no
 * verdict so the Execution Center only ever shows and runs what the device genuinely supports.
 *
 * All probes for one pass run as a single batched shell invocation via [ParallelShell], the same
 * mechanism the engine already uses for activation and restore, so a full device scan is one IPC
 * round trip.
 */
@Singleton
class DeviceCapabilityProbe @Inject constructor(
    private val privilegeManager: PrivilegeManager
) : CapableLookup {

    private val cache = ConcurrentHashMap<String, CapabilityResult>()
    private val probeLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            privilegeManager.isReady
                .filter { it }
                .collect { runProbe() }
        }
    }

    override fun isCapable(commandId: String): Boolean =
        cache[commandId]?.let { it.exists && it.writable } ?: false

    /** True when the device looks risky for an override that is not simply off/on elsewhere. */
    fun isRisky(commandId: String): Boolean = cache[commandId]?.risky ?: true

    fun result(commandId: String): CapabilityResult? = cache[commandId]

    /** Re-scans every catalog entry. Call once per connect, or from a manual "Rescan Device" action. */
    suspend fun runProbe() = probeLock.withLock {
        if (!privilegeManager.isReady.value) {
            FrameXLog.w("runProbe(): privilegeManager not ready, skipping", tag = TAG)
            return@withLock
        }
        val entries = GamingCommandCatalog.entries
        val probes = entries.map { oneLinerFor(it) }
        val script = ParallelShell.script(probes)
        FrameXLog.i("runProbe(): running script for ${entries.size} entries:\n$script", tag = TAG)
        val outcome = privilegeManager.exec(script, PROBE_TIMEOUT_MS)
        FrameXLog.i(
            "runProbe(): outcome=${outcome::class.simpleName} exit=${outcome.exitCode} " +
                "output=${outcome.output.take(3000)}",
            tag = TAG
        )
        if (!outcome.isSuccess) {
            FrameXLog.w(
                "Capability probe pass failed to run: outcome=${outcome::class.simpleName} " +
                    "exit=${outcome.exitCode} output=${outcome.output.take(500)}; cache left unchanged (was ${cache.size} entries)",
                tag = TAG
            )
            return@withLock
        }
        val parsed = parseResults(outcome.output)
        FrameXLog.i("runProbe(): parsed ${parsed.size} of ${entries.size} results", tag = TAG)
        entries.forEach { entry ->
            val result = parsed[entry.id] ?: CapabilityResult(exists = false, writable = false)
            if (entry.id !in parsed) {
                FrameXLog.w("runProbe(): no FXP line for ${entry.id}, treating as not-capable", tag = TAG)
            }
            cache[entry.id] = result
            FrameXLog.i("runProbe(): ${entry.id} -> exists=${result.exists} writable=${result.writable} risky=${result.risky}", tag = TAG)
        }
        FrameXLog.i("Capability probe: ${cache.values.count { it.exists && it.writable }}/${entries.size} capable", tag = TAG)
    }

    private fun parseResults(output: String): Map<String, CapabilityResult> =
        output.lineSequence()
            .filter { it.startsWith(RESULT_PREFIX) }
            .mapNotNull { line ->
                val parts = line.removePrefix(RESULT_PREFIX).split('|')
                if (parts.size < 3) return@mapNotNull null
                val id = parts[0]
                val exists = parts[1] == "1"
                val writable = parts[2] == "1"
                val risky = parts.getOrNull(3) == "1"
                id to CapabilityResult(exists, writable, risky)
            }
            .toMap()

    /**
     * One self-contained shell line per entry that prints `FXP|id|exists|writable|risky`. Each
     * entry's strategy is documented next to its definition in [GamingCommandCatalog]; this is
     * where that strategy is actually executed.
     */
    private fun oneLinerFor(entry: CommandEntry): String = when (val t = entry.target) {
        is CommandTarget.SettingsKey -> settingsProbe(entry.id, t)
        is CommandTarget.ServiceCommand -> serviceProbe(entry.id, t.stableId)
    }

    /** Decision 1: write-verify-restore — write the key's own current value back to itself. */
    private fun settingsProbe(id: String, target: CommandTarget.SettingsKey): String {
        val table = target.table.argument
        val key = target.key
        return "v=\$(settings get $table $key 2>/dev/null); " +
            "if [ -z \"\$v\" ] || [ \"\$v\" = \"null\" ]; then echo \"$RESULT_PREFIX$id|0|0\"; " +
            "else settings put $table $key \"\$v\" >/dev/null 2>&1; " +
            "v2=\$(settings get $table $key 2>/dev/null); " +
            "if [ \"\$v2\" = \"\$v\" ]; then echo \"$RESULT_PREFIX$id|1|1\"; else echo \"$RESULT_PREFIX$id|1|0\"; fi; fi"
    }

    /**
     * No settings row to stat, so existence/writability come from whether the service responds
     * without a not-found/permission-denied error. Prefers a read-only dry run where one exists
     * (thermal reset, netpolicy/deviceidle list/get); an SDK gate is applied explicitly for the
     * phantom-process override, since the probe alone cannot tell "not supported" from
     * "supported but unreadable now" on that API (#118 guidance extended to #120/#126 scope).
     */
    private fun serviceProbe(id: String, stableId: String): String {
        if (stableId == "phantom_proc_max_override" && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return "echo \"$RESULT_PREFIX$id|0|0\""
        }
        val riskyCheck = if (stableId == "fixed_performance_mode") {
            "; rout=\$(cmd power is-fixed-performance-mode-enabled 2>&1); " +
                "if echo \"\$rout\" | grep -qiE 'not found|unknown command|exception'; then risky=1; else risky=0; fi"
        } else {
            "; risky=0"
        }
        val dry = DRY_RUN_COMMANDS[stableId] ?: return "echo \"$RESULT_PREFIX$id|0|0\""
        return "out=\$($dry 2>&1)$riskyCheck; " +
            "if echo \"\$out\" | grep -qiE 'not found|unknown command|no such|permission denied|securityexception'; " +
            "then echo \"$RESULT_PREFIX$id|0|0|\$risky\"; else echo \"$RESULT_PREFIX$id|1|1|\$risky\"; fi"
    }

    private companion object {
        const val TAG = "DeviceCapabilityProbe"
        const val RESULT_PREFIX = "FXP|"
        const val PROBE_TIMEOUT_MS = 20_000L

        /** Read-only or idempotent-safe dry run per [CommandTarget.ServiceCommand.stableId]. */
        val DRY_RUN_COMMANDS: Map<String, String> = mapOf(
            "ram_trim_caches" to "pm trim-caches 1",
            "ram_pinner_repin" to "cmd pinner repin /system/framework/framework.jar",
            "thermal_override" to "cmd thermalservice reset",
            "cpu_priority_bg_restriction" to "cmd activity get-bg-restriction-level --user 0 android",
            "cpu_priority_standby_bucket" to "am get-standby-bucket android",
            "network_restrict_bg_whitelist" to "cmd netpolicy list restrict-background-whitelist",
            "network_deviceidle_whitelist" to "cmd deviceidle whitelist",
            "network_deviceidle_force_idle" to "cmd deviceidle get deep",
            "phantom_proc_max_override" to "device_config get activity_manager max_phantom_processes",
            "fixed_performance_mode" to "cmd power is-fixed-performance-mode-enabled"
        )
    }
}
