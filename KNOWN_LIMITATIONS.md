# Known Limitations

Some things FrameX can't fix from the app side — usually because a device's
vendor firmware doesn't expose the data we need, at any level (Android
framework, HAL, or even raw kernel). This page tracks confirmed cases so
duplicate reports can be closed quickly, and so anyone evaluating FrameX
knows what to expect on their device.

If you hit one of these on a device *not* listed below, please still open
an issue — chipset/OEM coverage here is incomplete by nature, and we want
to know about new cases.

---

## Thermal Diagnostics: no CPU / GPU / SKIN / NPU readings

**Symptom:** Thermal Diagnostics shows "Parse Failed" (or "Not supported on
this device" after the fix) for CPU, GPU, SKIN, and NPU — while Battery
Temp and Thermal Status still work normally.

**Root cause:** The device's Thermal HAL never connects
(`mHalReady` stays `false` in Android's own
[`ThermalManagerService`](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/power/ThermalManagerService.java)),
so `dumpsys thermalservice` has no per-sensor data to report — this isn't
something `dumpsys` is hiding or formatting differently, there's genuinely
nothing there. We also checked one level below Android entirely, at the raw
Linux kernel thermal zones (`/sys/class/thermal/thermal_zone*`), in case the
kernel exposed sensors the HAL didn't — on the confirmed device below, it
doesn't either.

Why Battery Temp and Thermal Status still work: they don't depend on the
per-sensor HAL data at all. Battery Temp comes from a separate command
(`dumpsys battery`), and Thermal Status comes from `PowerManager`'s public
`currentThermalStatus` API — both independent code paths.

**Confirmed affected devices:**

| Device | Chipset | Evidence | Status |
|---|---|---|---|
| Samsung Galaxy Tab S6 Lite (2022) | Snapdragon 720G / 732G | `dumpsys thermalservice` returns `HAL Ready: false`. | **RESOLVED in v1.5.7**: Multi-sensor thermal readings (CPU, GPU, Skin, Battery) fully restored via `v1.5.7` single-pass per-zone sysfs fallback (`/sys/class/thermal/thermal_zone*`). See [#19](https://github.com/MaheshSharan/FrameX-Android/issues/19). |

**Status:** Resolved in `v1.5.7`. Snapdragon devices with inactive HALs (`HAL Ready: false`) are now fully handled by the single-pass per-zone sysfs thermal loop (`for z in /sys/class/thermal/thermal_zone*; do echo "$(cat $z/type 2>/dev/null):$(cat $z/temp 2>/dev/null)"; done`), restoring CPU, GPU, Skin, NPU, and Battery monitoring.

---

## Gaming Mode: Vivo Platform Deactivation during Shizuku IPC Disconnect

**Symptom:** If the Shizuku background service crashes or permission is revoked during Gaming Mode deactivation on a Vivo/iQOO device, platform-specific overrides may not be fully reverted.

**Detail:** `revertPlatformOptimizations()` executes cleanup via Shizuku shell. If Shizuku Binder communication drops mid-deactivation, the failure is logged and session state is cleared to avoid locking the UI. If Gaming Mode is reactivated before a reboot, current values could be captured as baseline.

**Workaround:** Ensure Shizuku remains running in the background before toggling off Gaming Mode, or reboot the device if Shizuku crashed while Gaming Mode was active.

---

## Vivo Performance Tools: UI Visibility with Master Switch Disabled

**Detail:** When the "Vivo Hardware Optimizations" master toggle is switched off in Settings, the `Vivo Performance Tools` card (DEX speed compilation, `perf_game_list` management) and MEMC 120 FPS modal toggle are hidden from `PerformanceScreen`.

**Behavior:** Backend methods (`removePerfGame`, `compileSpeedAot`, `setMemcTargetFps(enabled = false)`) remain callable by session cleanup routines via hardware-level gating (`isVivoHardware`), but user-facing UI controls require the master switch to be enabled.
