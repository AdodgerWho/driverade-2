# Driverade APK build validation

This branch exists to validate the current Android debug APK using the existing GitHub Actions workflow (`.github/workflows/build.yml`).

## Scope
- No Kotlin source, Android manifest, permissions, or privacy/diagnostics behavior changes.
- The existing `pull_request` trigger targeting `main` should run `gradle assembleDebug` and upload the `Driverade` artifact if the build succeeds.
- This is a debug APK for testing, not a production release or security audit.

## Review checklist
- [ ] GitHub Actions build completes successfully.
- [ ] Download and install the APK from the workflow artifact.
- [ ] Verify startup, accessibility permissions, offer engine, overlay, and alert behavior on-device.
- [ ] Record missing alerter test function and other Kotlin migration regressions for follow-up.

Do not merge until the build and device testing have been reviewed.
