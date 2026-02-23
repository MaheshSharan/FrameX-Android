# FrameX — Play Store Listing

Internal reference. Do not edit the live Play Console listing without reviewing this document.

---

## App Title (30 chars max)

```
FrameX – FPS Meter & Performance Overlay
```

*Keywords covered: FPS Meter, Performance Overlay*

---

## Short Description (80 chars max)

```
Real-time FPS meter and system stats overlay for Android games.
```

---

## Full Description

```
FrameX is a lightweight performance overlay for Android that displays real-time system metrics on top of games and apps.

Built for gamers, developers, and power users, FrameX provides accurate frame rate data and system telemetry without requiring root access. Powered by Shizuku for secure privileged access.

────────────────────────────

Key Features

• Real-time FPS monitoring
• Draggable overlay that works on top of any app
• CPU frequency monitoring
• RAM usage display
• Battery temperature tracking
• Network speed and ping measurement
• Customizable overlay appearance
• Multiple layout modes (Minimal, Compact, Expanded)

────────────────────────────

Accurate Frame Rate Monitoring

FrameX reads frame timing data using Android's compositor statistics (SurfaceFlinger timestats), the same source used by professional performance tools. This ensures precise frame rate reporting without interfering with game rendering.

────────────────────────────

Customizable Overlay

Choose how your overlay looks and behaves:

• Adjustable text size
• Opacity control
• Background color (Black, Navy, Charcoal, Transparent)
• Border style (Accent, Subtle, Ghost, None)
• Metric value color (White, Accent, Silver, Auto)
• Accent color selection
• Multiple display modes
• Drag and reposition anywhere on screen

All settings persist across restarts.

────────────────────────────

Powered by Shizuku

FrameX uses the Shizuku API to securely access advanced system telemetry without requiring root. Shizuku must be installed and activated separately via wireless ADB or Sui (for rooted devices).

FrameX does not bypass system protections and does not modify game behavior.

────────────────────────────

Privacy First

FrameX does not collect personal data.
No accounts. No analytics. No background tracking.

Internet permission is used only for optional ping measurement.

────────────────────────────

Requirements

• Android 8.0 (API 26) or higher
• Shizuku installed and running

FrameX is designed as a monitoring tool for performance insight and diagnostics. It does not boost, modify, or alter game performance.
```

---

## Safe / Conservative Description (use if policy team requests)

```
FrameX is a system monitoring overlay for Android that displays frame rate and device telemetry in real time.

It provides diagnostic visibility into device performance while running games or applications.

FrameX does not modify system behavior, adjust performance parameters, or alter any application functionality. It is strictly a monitoring utility.
```

---

## Privacy Policy URL

```
https://maheshsharan.github.io/FrameX-Android/privacy-policy
```

Enable GitHub Pages in repo Settings → Pages → Source: `docs/` folder.

---

## ASO Keywords

### Primary (in title + description)
- FPS meter
- FPS monitor
- FPS counter
- Performance overlay
- Game FPS

### Secondary (use naturally in description)
- Real-time FPS
- Frame rate monitor
- Android FPS
- Gaming overlay
- System performance monitor
- Game performance stats

### Title Variations (A/B test later — don't change early)
- FrameX – FPS Monitor & Overlay
- FrameX FPS Meter for Android
- FrameX – Game Performance Monitor

---

## Screenshot Copy

Each screenshot = one benefit statement.

| Screen | Overlay text |
|--------|-------------|
| Overlay on game | "Real-Time FPS Monitoring" |
| Drag demo | "Drag Anywhere on Screen" |
| Appearance screen | "Fully Customizable Overlay" |
| Permissions screen | "Powered by Shizuku — No Root Required" |
| Expanded mode | "CPU · RAM · Temp · Network · Ping" |

Avoid: blank dark screens, jargon walls, tiny unreadable text.

---

## Pre-Launch Checklist

- [x] "Performance Adjustments" button renamed to "Diagnostics" — no boost language
- [ ] Privacy policy hosted at GitHub Pages URL above
- [ ] Privacy policy URL entered in Play Console
- [ ] Tested on Android 8.0 (API 26) — minimum SDK
- [ ] Tested on Android 14/15 — latest
- [ ] Verified graceful behavior when Shizuku is not installed (no crash)
- [ ] Verified Shizuku requirement is clearly explained in onboarding
- [ ] No language in app suggesting FPS boosting or game modification
- [ ] Reviewed all permissions — each has a stated purpose
- [ ] App content rating questionnaire completed in Play Console

---

## Review Strategy

First 50 reviews define early ranking.  
Ask beta testers to naturally mention:
- "FPS meter"
- stability
- accuracy

Do **not** incentivise reviews. Against Play policy.

---

## Versioning

- `v1.0.0` — initial release (current)
- Plan `v1.1.0` — Diagnostics panel, additional metrics
- Tag every release: `git tag -a v1.x.x -m "description" && git push --tags`
