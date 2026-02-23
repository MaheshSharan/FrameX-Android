---
layout: default
title: Privacy Policy – FrameX
description: Privacy policy for the FrameX Android application
---

# Privacy Policy — FrameX

**Effective date:** February 23, 2026  
**App:** FrameX – FPS Meter & Performance Overlay  
**Developer:** MaheshSharan  
**Contact:** [Open a GitHub issue](https://github.com/MaheshSharan/FrameX-Android/issues)

---

## Summary

FrameX does not collect, transmit, or share any personal data.  
There are no accounts, no analytics, and no tracking of any kind.

---

## Data Collection

FrameX **does not** collect:

- Personal information (name, email, phone number)
- Device identifiers or advertising IDs
- Location data
- Usage statistics or analytics
- Crash reports sent to external servers

---

## Data Storage

All user preferences (overlay position, appearance settings, enabled metrics) are stored **locally on your device** using Android's `SharedPreferences`. This data never leaves your device.

---

## Permissions Explained

| Permission | Purpose |
|---|---|
| Draw over other apps | Display the performance overlay on top of games and apps |
| Foreground service | Keep the overlay running while the screen is on |
| Wake lock | Prevent CPU sleep during an active monitoring session |
| Package usage stats | Identify which app is currently in the foreground |
| Request ignore battery optimizations | Survive aggressive OEM background-kill policies |
| Receive boot completed | Optionally restart the overlay after device reboot |
| Internet | Used **only** to measure network latency (ping to google.com). No personal data or browsing data is transmitted. |

---

## Shizuku

FrameX uses the [Shizuku API](https://github.com/RikkaApps/Shizuku) to access system telemetry (frame timing, CPU frequency, RAM) without requiring root. Shizuku is a separate application developed by RikkaApps.

- FrameX does not transmit data via Shizuku
- FrameX is not affiliated with Shizuku or RikkaApps
- Shizuku's own privacy practices are governed by its [documentation](https://github.com/RikkaApps/Shizuku)

---

## Third-Party Services

FrameX uses **no** third-party analytics SDKs, advertising networks, or tracking services.  
No SDK from Google Analytics, Firebase, Crashlytics, Amplitude, Mixpanel, or similar services is included.

---

## Children

FrameX does not knowingly collect data from children or anyone. It collects no data at all.

---

## Changes to This Policy

If this policy is updated, the new version will be published at this URL with a revised effective date. Significant changes will also be noted in the app's release notes.

---

## Contact

For any privacy-related questions, please [open an issue](https://github.com/MaheshSharan/FrameX-Android/issues) on the GitHub repository.
