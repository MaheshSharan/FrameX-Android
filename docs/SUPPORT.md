# FrameX Support & Troubleshooting

This guide describes the minimum information needed to investigate FrameX issues.

## Before opening an issue

1. Update to a recent FrameX build when possible.
2. Search the existing GitHub issues for the same problem.
3. Reproduce the problem and record the shortest reliable reproduction steps.
4. Do not include passwords, tokens, account information, IMEI/serial numbers,
   location data, or other private information in an issue.

## What to include

### Bug reports

Provide:

- FrameX version.
- Device manufacturer and model.
- Android version.
- ROM / OS skin.
- Affected FrameX feature.
- Actual behavior.
- Expected behavior.
- Reproduction steps.
- Relevant configuration.
- Screenshots, recordings, or logs when they help diagnose the problem.

### Performance reports

Provide the same environment information plus:

- Game or app being tested.
- Expected performance.
- Observed performance.
- Reproduction steps.
- Whether FrameX overlay was enabled.
- Whether Gaming Mode was enabled.
- Whether Shizuku was connected.
- Relevant enabled metrics and configuration.
- A comparison with FrameX disabled when possible.

Logs are useful when relevant, but they are not required for every performance
report.

### Feature requests

Explain:

- The problem you want to solve.
- The behavior you want.
- Why the current behavior is insufficient.

Device information and logs are normally unnecessary for feature requests.

## Diagnostics

When available, use FrameX's built-in sanitized diagnostic-information feature
and attach or paste its output. Diagnostic information should contain only data
needed to troubleshoot FrameX.

Never include:

- IMEI or device serial numbers.
- Google or other account information.
- Phone numbers or precise location data.
- Passwords, access tokens, API keys, or credentials.
- Shizuku credentials.
- Arbitrary private file contents.

## Thermal diagnostics

If Thermal Diagnostics reports `Parse Failed` or `Unsupported Hardware`, include
the device model, SoC, Android/ROM version, and the relevant thermal diagnostics
output. Debug builds may provide more useful diagnostic logs than release builds.

## Security issues

Do not disclose security vulnerabilities in public GitHub issues. Use GitHub's
private security-advisory reporting flow instead.
