# Changelog

## 1.6.0-beta

### Added
- **Root mode.** Choose Shizuku or Root in System Status. The selected mode is the only one used; FrameX never falls back from one to the other. Root uses one persistent shell (Magisk and KernelSU supported).
- **System Status.** The Permissions screen is now System Status, and it is the one place to grant access. It lists Overlay, Notifications, Usage Access, Battery optimization, Write Settings, Do Not Disturb and Notification Listener, and updates when you return to the app.
- **Setup step.** The last onboarding page now picks the privilege mode and asks for the overlay and notification permissions. Skip still works.
- **Mode switcher.** The privilege mode cannot be changed while Gaming Mode is on, because changes made through one backend can only be undone through that same backend. A message explains this; turn Gaming Mode off first.
- **CPU and GPU tuning safeguards.** Values are checked against what the device offers, every write is read back, and **Restore defaults** returns the original values. Tuning is read-only unless the active backend can write kernel settings (normally Root).
- **Tuning Apply bar.** Dropdown choices are drafts. Nothing is written until you press **Apply**.
- **Root tuning lock.** In Root mode, Apply writes the values and then locks the CPU and GPU frequency and governor files read-only so system services cannot change them. The section stays greyed out until you press **Unlock to edit**.
- **Execution Center catalog.** All Gaming Mode commands come from one catalog and run on every device. A command appears only if the device has it and it is writable, checked once when the privileged service connects. Activation writes only the switches you enabled, and Gaming Mode off restores exactly those. Original values are saved before the first write and survive the app being killed.
- **Mode switcher messages.** Setup and System Status show the same messages when a switch is blocked by Gaming Mode or a pending recovery, or when Root access is denied (FrameX then keeps the previous mode).
- Gaming Mode deactivation timing is logged per phase and per command (`adb logcat -s PrivExec GamingModeEngine`).
- Crash recovery for Gaming Mode now uses the backend that started the session, and raises a high-priority alert if it cannot finish.

### Changed
- **Shizuku is the default privilege mode and None is gone.** One mode is always selected. If you had None selected, you are now on Shizuku.
- **Performance shows access read-only.** It no longer opens permission screens itself; the System Status card there links to System Status.
- **One readiness rule.** The Dashboard and System Status both count FrameX as ready when the selected mode is connected and Overlay and Notifications are granted.
- The Dashboard card is now **System Status**.
- **What's New is removed.** Release notes are shown in the update dialog only.
- **Vivo and iQOO specific commands, tabs and the 2-minute maintenance pulse are removed.** They had no measurable effect and could lower FPS. Refresh rate, touch and the other optimizations are now generic and verified per device.
- Gaming Mode no longer suspends apps you already disabled or suspended yourself, and does not turn them back on afterwards.
- Suspending and restoring background apps takes under a second instead of about five.
- The Common and Generic Execution Center tabs show only the commands the device supports.
- CPU and GPU tuning appears only in Root mode.
- Settings now live in a single store. Settings from earlier versions are migrated automatically on first launch, including overlay appearance, positions, Gaming Mode options and onboarding.
- Gaming Mode notifications use two channels: a quiet one while it runs and a separate alert channel for recovery problems. The old gaming channels are removed, so per-channel notification preferences reset once.
- The overlay holds a partial wake lock only while it is sampling and releases it on screen-off, stop and teardown.
- The overlay restarts after a reboot and after an app update.
- The phantom-process guard moved out of the Vivo/iQOO code path and applies on Android 12 and later; its original value is restored when Gaming Mode ends.

- The update check now offers `1.6.0` to `1.6.0-beta` users, and `-beta` tags are published as GitHub pre-releases.

### Fixed
- The Dashboard and the Permissions screen could disagree about whether setup was complete.
- Permission status could be out of date after returning from a system settings screen.
- Gaming Mode failing with "Failed to capture system settings snapshot" when a device lacked one of the settings (for example on HyperOS).
- One failing command stopped the rest of Gaming Mode activation or restore. Each command is now independent, and a partial result is reported.
- Restoring settings could fail under Shizuku for global and secure values.
- Brave and other installed browsers were never suspended because every browser counted as the default.
- Turning Gaming Mode off in Root mode could report that the privileged service was unavailable, and could take about 25 seconds. It now waits for the backend and restores in parallel groups.
- Gaming Mode off could write values to settings that were never enabled.
- Tuning log lines used the wrong tag.
- Settings and onboarding being reset on launch.
- Quick Settings tile not refreshing when the overlay starts or stops from the app or notification.
- Overlay start and stop entries missing from the audit log.

### Known limitations
See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). In particular, system services on some devices can override CPU limits after they are set, and Root mode has had limited testing across devices.
