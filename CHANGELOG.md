# Changelog

## 1.6.0

### Added
- **Root mode.** Choose Shizuku, Root or None in Permissions. The selected mode is the only one used; FrameX never falls back from one to the other. Root uses one persistent shell (Magisk and KernelSU supported).
- **Mode switcher.** Switching while Gaming Mode is on turns Gaming Mode off first, through the backend that applied it.
- **CPU and GPU tuning safeguards.** Values are checked against what the device offers, you confirm each change, every write is read back, and **Restore defaults** returns the original values. Tuning is read-only unless the active backend can write kernel settings (normally Root).
- Crash recovery for Gaming Mode now uses the backend that started the session, and raises a high-priority alert if it cannot finish.

### Changed
- Settings now live in a single store. Settings from earlier versions are migrated automatically on first launch, including overlay appearance, positions, Gaming Mode options and onboarding.
- Gaming Mode notifications use two channels: a quiet one while it runs and a separate alert channel for recovery problems. The old gaming channels are removed, so per-channel notification preferences reset once.
- The overlay holds a partial wake lock only while it is sampling and releases it on screen-off, stop and teardown.
- The overlay restarts after a reboot and after an app update.
- The phantom-process guard moved out of the Vivo/iQOO code path and applies on Android 12 and later; its original value is restored when Gaming Mode ends.

### Fixed
- Settings and onboarding being reset on launch.
- Quick Settings tile not refreshing when the overlay starts or stops from the app or notification.
- Overlay start and stop entries missing from the audit log.

### Known limitations
See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). In particular, system services on some devices can override CPU limits after they are set, and Root mode has had limited testing across devices.
