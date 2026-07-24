# Vivo T3 Ultra (PD2362IF) — Full Device & Performance Audit Report

> [!NOTE]
> **Audit Environment**: Non-Root ADB Shell / Shizuku Context (UID 2000)  
> **OS**: OriginOS 6 / FuntouchOS (Android 16 · SDK 36) · Build: `PD2362IF_EX_A_16.2.12.2.W30`  
> **Kernel**: `5.15.197` · Architecture: `arm64-v8a`

---

## 1. Hardware Architecture Summary

| Component | Specification | Operational Detail |
| :--- | :--- | :--- |
| **SoC** | MediaTek Dimensity 9200+ (`MT6985`) | 4nm TSMC process |
| **CPU Cluster 0** | 4× Cortex-A510 (LITTLE) @ 2.0 GHz | Cores 0–3 (`policy0`, governor: `sugov_ext`) |
| **CPU Cluster 1** | 3× Cortex-A715 (Mid) @ 3.0 GHz | Cores 4–6 (`policy4`, governor: `sugov_ext`) |
| **CPU Cluster 2** | 1× Cortex-X3 (Prime) @ 3.35 GHz | Core 7 (`policy7`, governor: `sugov_ext`) |
| **GPU** | MediaTek Immortalis-G715 | Hardware MEMC Supported (`game_memc = 1`) |
| **Thermal HAL** | Android ThermalHAL 2.0 | 6 Hardware Thermal Sensors Active |
| **Display** | 120 Hz Hardware Panel | Dynamic Hz driver (`vivo_screen_refresh_rate_mode`) |

---

## 2. Complete Shizuku ADB Capability Matrix (UID 2000)

> [!IMPORTANT]
> This matrix documents every tested system command on the Vivo T3 Ultra in non-root ADB context.

### ✅ Fully Executable & Persisted (Exit Code 0)

| Domain | Command / Setting | Empirical Effect | Reversion Command |
| :--- | :--- | :--- | :--- |
| **Thermal** | `cmd thermalservice override-status 0` | Suppresses OS thermal throttling drops (`THERMAL_STATUS_NONE`) | `cmd thermalservice reset` |
| **PowerHAL** | `cmd power set-fixed-performance-mode-enabled true` | Forces PowerHAL into maximum performance state | `cmd power set-fixed-performance-mode-enabled false` |
| **Display Driver** | `settings put global vivo_screen_refresh_rate_mode <maxHz>` | Locks Vivo display driver refresh rate to hardware max | `settings delete global vivo_screen_refresh_rate_mode` |
| **GameCube** | `settings put system gamecube_competition_mode_state 1` | Enables OriginOS GameCube Esports Competition mode | `settings put system gamecube_competition_mode_state 0` |
| **Resolution** | `settings put system game_screen_resolution_switch 1` | Enables Vivo Game Resolution Control | `settings put system game_screen_resolution_switch 0` |
| **Touch** | `settings put system com.vivo.vtouch.persist 1` | Activates Vivo Touch Controller low-latency mode | `settings delete system com.vivo.vtouch.persist` |
| **FPS & Render Scaling** | `cmd game set --fps <maxHz> --downscale 0.9 <pkg>` | Sets Android GameManager 120 FPS target & 0.9x GPU downscaling | `cmd game reset <pkg>` |
| **Heap Compaction** | `am compact background` | Trims and compacts ART memory heaps before launch | Automatic OS scheduling |
| **Framework Pinning** | `cmd pinner repin /system/framework/framework.jar` | Pins Android framework binaries into RAM Pinner quota (728 MB) | Automatic OS management |
| **CPU Priority** | `cmd activity set-bg-restriction-level --user 0 <pkg> unrestricted` | Removes background CPU restriction on active game | `cmd activity set-bg-restriction-level --user 0 <pkg> adaptive_bucket` |
| **App Bucket** | `am set-standby-bucket --user 0 <pkg> active` | Locks game process into ACTIVE standby bucket | Automatic OS recovery |
| **Network** | `cmd netpolicy add restrict-background-whitelist <uid>` | Exempts game from background data restriction | `cmd netpolicy remove restrict-background-whitelist <uid>` |
| **Doze Control** | `cmd deviceidle force-idle` | Freezes non-whitelisted background app polling | `cmd deviceidle unforce` |
| **RAM Cache** | `pm trim-caches 4G` | Clears page cache to maximize free RAM | Automatic OS allocation |

---

### ❌ Blocked / Non-Executable (SELinux & OEM Daemon Protected)

| Command / Setting | Block Reason | Technical Explanation |
| :--- | :--- | :--- |
| `setprop persist.sys.performance.mode 1` | **SELinux Permission Denied** | OriginOS blocks `persist.sys.*` property mutations from UID 2000 |
| `setprop persist.sys.touch.response 2` | **SELinux Permission Denied** | OriginOS blocks property writes without root context |
| `taskset -p <mask|pid>` | **SELinux Permission Denied** | CPU affinity mask pinning requires root / kernel access |
| `gamecube_hawkeye_effective` | **Daemon Overridden** | `com.vivo.game` daemon holds `ContentObserver` & reverts external writes |
| `settings_battery_charge_director_game_cube` | **Daemon Overridden** | Controlled exclusively by Vivo PowerHAL daemon |
| `vsr_value_from_gamecube` | **Daemon Overridden** | Controlled by MediaTek VSR hardware engine |

---

## 3. System Table Key Audit (`system`, `global`, `secure`)

### Key Vivo System Properties (Live In-Game Readout)

```text
vivo_rms_active_request_reason = FrameOverride:com.pubg.imobile/com.epicgames.ue4.GameActivity|120
gamewatch_game_target_fps = 120
current_game_package = com.pubg.imobile
is_game_mode = 1
com.vivo.vtouch.persist = 1
vivo_screen_refresh_rate_mode = 120
gamecube_competition_mode_state = 1
game_screen_resolution_switch = 1
game_do_not_disturb = 1
```

---

## 4. Hardware Thermal HAL Sensor Mapping

```text
ThermalHAL 2.0 Connected: YES
Sensors Monitored:
  - Sensor 0: CPU (Max Hot Threshold: 105.0°C)
  - Sensor 1: GPU (Max Hot Threshold: 105.0°C)
  - Sensor 2: BATTERY (Max Hot Threshold: 50.0°C)
  - Sensor 3: SKIN (OEM Throttle Thresholds: 38.0°C, 40.0°C, 44.0°C, 48.0°C)
  - Sensor 5: POWER_AMPLIFIER (Max Hot Threshold: 68.0°C)
  - Sensor 9: NPU (Max Hot Threshold: 105.0°C)
```

> [!TIP]
> **Thermal Suppression Verification**: During high-load gaming sessions with `SKIN` temp at ~45°C, `cmd thermalservice override-status 0` successfully prevents the OS from triggering Level 3 frame-rate throttling, keeping frame render times at 3ms–6ms.
