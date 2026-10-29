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

## Gaming Mode: Vivo Platform Deactivation during Privilege Backend Disconnect

**Symptom:** If the privilege backend (Shizuku service or root shell) drops during Gaming Mode deactivation on a Vivo/iQOO device, platform-specific overrides may not be fully reverted.

**Detail:** `revertPlatformOptimizations()` runs cleanup through the active backend. If the connection drops mid-deactivation, the failure is logged and session state is cleared to avoid locking the UI. Crash recovery after a process restart uses the backend recorded when Gaming Mode started, even if you have since selected the other one. If that backend cannot connect in time, FrameX shows a high-priority recovery alert instead of guessing.

**Workaround:** Keep the backend running while toggling off Gaming Mode, or reboot the device if it crashed while Gaming Mode was active.

---

## Vivo Performance Tools: UI Visibility with Master Switch Disabled

**Detail:** When the "Vivo Hardware Optimizations" master toggle is switched off in Settings, the `Vivo Performance Tools` card (DEX speed compilation, `perf_game_list` management) and MEMC 120 FPS modal toggle are hidden from `PerformanceScreen`.

**Behavior:** Backend methods (`removePerfGame`, `compileSpeedAot`, `setMemcTargetFps(enabled = false)`) remain callable by session cleanup routines via hardware-level gating (`isVivoHardware`), but user-facing UI controls require the master switch to be enabled.

---

## Root and Shizuku modes behave differently

**Detail:** The selected mode is authoritative. FrameX never falls back from one backend to the other. Shizuku runs commands as the shell user; Root runs them as root. CPU and GPU tuning writes kernel files that the shell user cannot write on most devices, so tuning is read-only unless a probe shows the active backend can write them. Switching modes is refused while Gaming Mode is active or recovery is pending.

**Not tested:** Root mode has not been verified on a rooted device across Magisk and KernelSU versions, and SELinux policies differ between ROMs.

---

## CPU and GPU limits can be overridden by the system

**Symptom:** A frequency cap you set returns to a higher value after a few minutes.

**Detail:** FrameX writes the value and reads it back to confirm the kernel accepted it. It does not keep watching it. Vendor services (for example OEM frequency-control or performance HAL services) can rewrite `scaling_max_freq` afterward, and the app cannot prevent that. Writing the value from a terminal may behave differently because the vendor service can react to the writer.

**Workaround:** None inside FrameX. Use Restore defaults to return to the original values.

---

## Vivo and iQOO: generic overrides still apply

**Detail:** On Vivo and iQOO firmware, only the fixed performance mode is blocked, because it can lock the display. The thermal throttling override, refresh-rate lock and touch boost still apply as before. If you see display problems on these devices, turn those options off in Gaming Mode settings and open an issue with your device and ROM.

