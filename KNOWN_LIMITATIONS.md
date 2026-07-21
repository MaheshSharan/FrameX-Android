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

| Device | Chipset | Evidence |
|---|---|---|
| Samsung Galaxy Tab S6 Lite (2022) | Snapdragon 720G / 732G | `dumpsys thermalservice` returns `HAL Ready: false` with zero sensor entries. Raw kernel thermal zones (`/sys/class/thermal/thermal_zone*`) checked directly — the only zone present, `pm6150-tz`, is confirmed via [upstream kernel source](https://lkml.iu.edu/hypermail/linux/kernel/2106.0/03446.html) to be the PM6150 **PMIC's own** on-die protection sensor (added to trigger orderly shutdown before a 145°C hardware power-off) — not a CPU/GPU/SKIN sensor. The remaining zones (`ibat-lvl0/1`, `vbat-lvl0/1/2`) are battery current/voltage protection thresholds, not temperatures. See [#19](https://github.com/MaheshSharan/FrameX-Android/issues/19) for the full investigation. |

**Status:** Not fixable from the app. This is a vendor firmware limitation —
Samsung's vendor partition for this device either never shipped a working
Thermal HAL implementation, or routes real thermal management through a
private system never wired into the standard Android interfaces FrameX (or
any app) can read.
