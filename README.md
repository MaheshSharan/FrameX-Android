# FrameX

> Real-time performance overlay for Android. Powered by [Shizuku](https://github.com/RikkaApps/Shizuku) — no root required.

[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.0-orange.svg)](https://github.com/MaheshSharan/FrameX-Android/releases/tag/v1.0.0)

---

## What it does

FrameX shows a draggable, fully customisable overlay with live system stats on top of any app or game — including full-screen titles.

**Available metrics:** FPS · CPU frequency · RAM usage · Battery temperature · Network speed · Ping

---

## Requirements

- Android 8.0 (API 26) or higher
- [Shizuku](https://github.com/RikkaApps/Shizuku) installed and running
  - Activate via Wireless Debugging (no PC needed on Android 11+) or ADB
  - Also works with the Sui module on rooted devices

---

## How to use

1. Install Shizuku and follow its setup guide to activate the service
2. Open FrameX and complete onboarding — it will request overlay and Shizuku permissions
3. From the dashboard tap **Start Overlay**
4. The overlay appears on screen and stays on top of everything including games
5. **Drag** it anywhere · **Long-press** to cycle display modes without leaving the game
6. **Metrics** — choose which stats are visible via Overlay Customization
7. **Appearance** — change opacity, background, border, text color, text size, font

---

## Overlay modes

| Mode | Description |
|------|-------------|
| **Minimal** | Single line, values only, minimal visual footprint |
| **Compact** | Two-column grid of enabled metrics with labels |
| **Expanded** | Full grid with icons and labels, best for side-mounted position |

---

## Appearance options

- **Background** — Black / Navy / Charcoal / Transparent
- **Border** — Accent color / None / Subtle / Ghost
- **Text color** — White / Accent / Silver / Auto (FPS-adaptive green → amber → red)
- **Accent color** — 6 presets
- **Text size** — Small / Medium / Large
- **Font** — Default or JetBrains Mono

All settings and the last overlay position persist across restarts.

---

## How FPS is measured

FPS is read from SurfaceFlinger via Shizuku — the same compositor-level frame timing data used by production performance tools. It adds zero overhead to the game's rendering pipeline.

Other metrics poll at 1–5 second intervals and run only when their module is enabled, so disabled metrics cost nothing.

---

## Build

Standard Android Gradle project. Requires **JDK 17** and **Android SDK 34**.

```bash
git clone https://github.com/MaheshSharan/FrameX-Android.git
cd FrameX-Android
./gradlew assembleDebug
```

---

## Permissions

| Permission | Why |
|---|---|
| Draw over other apps | Display the overlay on top of games |
| Foreground service | Keep the overlay alive while the screen is on |
| Wake lock | Prevent CPU sleep during an active session |
| PACKAGE_USAGE_STATS | Identify which game is in the foreground |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Survive aggressive OEM background-kill policies |
| Receive boot completed | Auto-restart overlay after reboot if it was active |
| Internet | Ping measurement to google.com only |

---

## Security & Privacy

- **No data collection** — nothing is sent anywhere
- **No analytics** — no Firebase, no Crashlytics, no tracking SDKs
- **No accounts** — FrameX has no sign-in or user identity
- **No ads** — ever
- All data stays on-device

[Full Privacy Policy](https://maheshsharan.github.io/FrameX-Android/privacy-policy)

---

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku) by [RikkaW](https://github.com/RikkaApps) — the privileged API bridge that makes rootless system access possible. FrameX would not exist without it.

---

## License

```
MIT License — see LICENSE for details.
```
