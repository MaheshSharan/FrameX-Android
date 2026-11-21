package com.framex.app.gaming.catalog

import com.framex.app.gaming.ledger.OpPriority.DETAIL
import com.framex.app.gaming.ledger.OpPriority.PRIMARY
import com.framex.app.gaming.ledger.Stage

private fun fixed(value: String): (ResolveContext) -> String = { value }

/**
 * Every command FrameX can run during a gaming-mode session, migrated from the former
 * Vivo/iQOO vendor catalog and the bespoke `GenericGamingOptimizer` path onto one engine. Each
 * entry carries a comment documenting how [com.framex.app.privilege.DeviceCapabilityProbe]
 * decides whether the device actually supports it, since that mapping is inherently per-command.
 */
object GamingCommandCatalog {

    val entries: List<CommandEntry> = listOf(
        // --- RAM cache pre-trim (one-shot memory actions; nothing to revert). -----------------
        // Capability: `pm`/`cmd pinner` always exist on Android 8+; probe treats the service as
        // capable whenever it responds without a not-found error.
        // Note: `am compact` used to also run here as `am compact background`, but that is not a
        // valid invocation — `am compact` requires a target PID/process
        // (`am compact <pid> [some|full]`), there is no whole-system "background" target, and the
        // command always failed with "Error: unknown compact command 'background'". Removed
        // rather than fixed: FrameX has no single long-lived target process to compact here (the
        // active game's PID is not known at this stage), so there is no correct invocation to
        // replace it with.
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "ram_trim_caches",
                activate = { "pm trim-caches 4G" }
            ),
            stage = Stage.MEMORY,
            toggle = Toggle.RAM_CACHE_PRE_TRIM,
            priority = PRIMARY,
            restore = Restore.NONE
        ),
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "ram_pinner_repin",
                activate = { "cmd pinner repin /system/framework/framework.jar" }
            ),
            stage = Stage.MEMORY,
            toggle = Toggle.RAM_CACHE_PRE_TRIM,
            priority = DETAIL,
            restore = Restore.NONE
        ),

        // --- Thermal throttle override. ---------------------------------------------------------
        // Capability: dry-run is `cmd thermalservice reset`, which is idempotent and safe to run
        // during a probe pass; a not-found/permission-denied response means the service does not
        // exist on this build.
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = CommandEntry.THERMAL_OVERRIDE_STABLE_ID,
                activate = { "cmd thermalservice override-status 0" },
                fixedRevert = { "cmd thermalservice reset" }
            ),
            stage = Stage.POWER,
            toggle = Toggle.THERMAL_OVERRIDE,
            priority = PRIMARY
        ),

        // --- CPU priority / standby bucket lock (per active game package). ----------------------
        // Capability: dry-run is `cmd activity set-bg-restriction-level --user 0 <probe-owned
        // package> adaptive_bucket` against FrameX's own package, which is always installed and
        // the call is idempotent; `am set-standby-bucket` is probed the same way.
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "cpu_priority_bg_restriction",
                activate = { "cmd activity set-bg-restriction-level --user 0 ${it.packageOrDefault} unrestricted" },
                fixedRevert = { "cmd activity set-bg-restriction-level --user 0 ${it.packageOrDefault} adaptive_bucket" }
            ),
            stage = Stage.POWER,
            toggle = Toggle.CPU_PRIORITY_LOCK,
            priority = PRIMARY,
            need = Need.PACKAGE
        ),
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "cpu_priority_standby_bucket",
                activate = { "am set-standby-bucket --user 0 ${it.packageOrDefault} active" },
                fixedRevert = { "am set-standby-bucket --user 0 ${it.packageOrDefault} working_set" }
            ),
            stage = Stage.POWER,
            toggle = Toggle.CPU_PRIORITY_LOCK,
            priority = DETAIL,
            need = Need.PACKAGE
        ),

        // --- Network & doze firewall (per active game package + uid). ---------------------------
        // Capability: `cmd netpolicy list restrict-background-whitelist` and `cmd deviceidle
        // whitelist` are read-only dry runs; a response (even an empty list) means the service
        // exists, a not-found/permission-denied error means it does not.
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "network_restrict_bg_whitelist",
                activate = { "cmd netpolicy add restrict-background-whitelist ${it.uid}" },
                fixedRevert = { "cmd netpolicy remove restrict-background-whitelist ${it.uid}" }
            ),
            stage = Stage.NETWORK,
            toggle = Toggle.NETWORK_FIREWALL,
            priority = PRIMARY,
            need = Need.PACKAGE_AND_UID
        ),
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "network_deviceidle_whitelist",
                activate = { "cmd deviceidle whitelist +${it.packageOrDefault}" },
                fixedRevert = { "cmd deviceidle whitelist -${it.packageOrDefault}" }
            ),
            stage = Stage.NETWORK,
            toggle = Toggle.NETWORK_FIREWALL,
            priority = DETAIL,
            need = Need.PACKAGE_AND_UID
        ),
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "network_deviceidle_force_idle",
                activate = { "cmd deviceidle force-idle" },
                fixedRevert = { "cmd deviceidle unforce" }
            ),
            stage = Stage.NETWORK,
            toggle = Toggle.NETWORK_FIREWALL,
            priority = DETAIL,
            need = Need.PACKAGE_AND_UID
        ),

        // --- Phantom process killer disable. -----------------------------------------------------
        // Capability: the setting does not exist before API 31; the probe's device_config/
        // settings dry run also gates on SDK level explicitly (see DeviceCapabilityProbe), since
        // a probe alone cannot safely tell "not supported" from "supported but unreadable now".
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "phantom_proc_max_override",
                activate = { "device_config put activity_manager max_phantom_processes 2147483647" },
                fixedRevert = { "device_config delete activity_manager max_phantom_processes" }
            ),
            stage = Stage.KERNEL,
            toggle = Toggle.PHANTOM_PROC_KILLER,
            priority = PRIMARY
        ),
        CommandEntry(
            target = CommandTarget.SettingsKey(SettingsTable.GLOBAL, "settings_enable_monitor_phantom_procs"),
            stage = Stage.KERNEL,
            toggle = Toggle.PHANTOM_PROC_KILLER,
            priority = DETAIL,
            value = fixed("false")
        ),

        // --- Fixed performance governor. ---------------------------------------------------------
        // Capability: dry-run reads the current fixed-performance-mode state via `cmd power
        // is-fixed-performance-mode-enabled` where available; a risky device (one where the probe
        // sees the display mode already locked, or where toggling does not settle) is flagged by
        // OemOverridePolicy rather than hidden, so it still requires explicit confirmation.
        CommandEntry(
            target = CommandTarget.ServiceCommand(
                stableId = "fixed_performance_mode",
                activate = { "cmd power set-fixed-performance-mode-enabled true" },
                fixedRevert = { "cmd power set-fixed-performance-mode-enabled false" }
            ),
            stage = Stage.POWER,
            toggle = Toggle.FIXED_PERFORMANCE_MODE,
            priority = PRIMARY
        ),

        // --- Refresh rate lock. -------------------------------------------------------------------
        // Capability: write-verify-restore on both keys (Decision 1); existence is "settings get"
        // returning non-null.
        CommandEntry(
            target = CommandTarget.SettingsKey(SettingsTable.SYSTEM, "peak_refresh_rate"),
            stage = Stage.DISPLAY,
            toggle = Toggle.REFRESH_RATE_LOCK,
            priority = PRIMARY,
            value = { it.refreshRateHz.toString() }
        ),
        CommandEntry(
            target = CommandTarget.SettingsKey(SettingsTable.SYSTEM, "min_refresh_rate"),
            stage = Stage.DISPLAY,
            toggle = Toggle.REFRESH_RATE_LOCK,
            priority = DETAIL,
            value = { it.refreshRateHz.toString() }
        ),

        // --- Touch response boost. -----------------------------------------------------------------
        // Capability: write-verify-restore (Decision 1).
        CommandEntry(
            target = CommandTarget.SettingsKey(SettingsTable.SYSTEM, "touch_response_speed"),
            stage = Stage.TOUCH,
            toggle = Toggle.TOUCH_BOOST,
            priority = PRIMARY,
            value = fixed("2")
        ),

        // --- Always-captured baseline, never written on activation. -------------------------------
        // FrameX never sets this key itself; it is only ever put back on deactivation/Restore to
        // Default because some OEM tools or the user's own display settings can change it while a
        // session is active. Capability: write-verify-restore, same as any other settings key.
        CommandEntry(
            target = CommandTarget.SettingsKey(SettingsTable.SECURE, "user_preferred_display_mode_id"),
            stage = Stage.DISPLAY,
            toggle = Toggle.ALWAYS,
            priority = DETAIL,
            value = fixed("-1"),
            appliesOnActivation = false
        )
    )

    init {
        val ids = entries.map { it.id }
        check(ids.size == ids.toSet().size) { "Duplicate CommandEntry id in GamingCommandCatalog" }
    }
}
