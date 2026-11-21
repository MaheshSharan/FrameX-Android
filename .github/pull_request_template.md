## What and why

<!-- One or two lines. The diff shows what; say why. -->

## Checks

- [ ] `./gradlew lint testDebugUnitTest assembleDebug` passes
- [ ] No `!!`, `GlobalScope`, `runBlocking`, empty catch or dead code (see AGENTS.md)
- [ ] Files stay under 500 lines, functions under 60
- [ ] Tested on a device where the change touches privileged commands or system settings

Closes #
