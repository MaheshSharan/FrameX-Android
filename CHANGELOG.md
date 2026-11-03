# Changelog

## 1.6.0-beta

### Added
- **Root mode.** Choose Shizuku, Root or None in Permissions. The selected mode is the only one used; FrameX never falls back from one to the other. Root uses one persistent shell (Magisk and KernelSU supported).
- **Mode switcher.** The privilege mode cannot be changed while Gaming Mode is on, because changes made through one backend can only be undone through that same backend. A message explains this; turn Gaming Mode off first.
- **CPU and GPU tuning safeguards.** Values are checked against what the device offers, every write is read back, and **Restore defaults** returns the original values. Tuning is read-only unless the active backend can write kernel settings (normally Root).
- **Tuning Apply bar.** Dropdown choices are drafts. Nothing is written until you press **Apply**.
- **Root tuning lock.** In Root mode, Apply writes the values and then locks the CPU and GPU frequency and governor files read-only so system services cannot change them. The section stays greyed out until you press **Unlock to edit**.
- **Execution Center catalog.** Vivo and iQOO commands come from one catalog. Activation writes only the switches you enabled, and Gaming Mode off restores exactly those. Original values are saved before the first write and survive the app being killed.
- Gaming Mode deactivation timing is logged per phase and per command (`adb logcat -s PrivExec GamingModeEngine`).
- Crash recovery for Gaming Mode now uses the backend that started the session, and raises a high-priority alert if it cannot finish.

### Changed
- Settings now live in a single store. Settings from earlier versions are migrated automatically on first launch, including overlay appearance, positions, Gaming Mode options and onboarding.
- Gaming Mode notifications use two channels: a quiet one while it runs and a separate alert channel for recovery problems. The old gaming channels are removed, so per-channel notification preferences reset once.
- The overlay holds a partial wake lock only while it is sampling and releases it on screen-off, stop and teardown.
- The overlay restarts after a reboot and after an app update.
- The phantom-process guard moved out of the Vivo/iQOO code path and applies on Android 12 and later; its original value is restored when Gaming Mode ends.

- The update check now offers `1.6.0` to `1.6.0-beta` users, and `-beta` tags are published as GitHub pre-releases.

### Fixed
- Turning Gaming Mode off in Root mode could report that the privileged service was unavailable, and could take about 25 seconds. It now waits for the backend and restores in parallel groups.
- Gaming Mode off could write values to settings that were never enabled.
- Tuning log lines used the wrong tag.
- Settings and onboarding being reset on launch.
- Quick Settings tile not refreshing when the overlay starts or stops from the app or notification.
- Overlay start and stop entries missing from the audit log.

### Known limitations
See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). In particular, system services on some devices can override CPU limits after they are set, and Root mode has had limited testing across devices.
