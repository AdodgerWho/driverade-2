# Visual capture engine blueprint

**Status:** Integrated opt-in Android capture; standalone extraction pending.

## Existing source
- `app/src/main/java/com/driverade/service/VisualDebugCapture.kt`
- `app/src/main/java/com/driverade/service/BugReportManager.kt`
- `app/src/main/java/com/driverade/service/GuidedSettingsState.kt`

## Proposed reusable API
`CapturePolicy(isEnabled, allowedPackages, retention)`; `CaptureResult(success, file, failureReason)`; `CaptureAdapter.observe(event)`; `CaptureExporter.exportVerifiedImages()`.

## Existing behavior
Uses Android AccessibilityService screenshot API on Android 11+, opt-in setting, relevant window events, throttling, PNG temporary-file write and signature check, bounded file retention. Capture is independent of OCR keywords.

## Dependencies and risks
Requires an enabled accessibility service, Android screenshot API and appropriate user consent. A screenshot can contain names, addresses, map coordinates, or other sensitive content. PNG signature alone does not establish image integrity or successful export. Screenshot retention and privacy-safe export require explicit verification.

## Acceptance tests
Consent off/on; supported and unsupported Android versions; no screenshots when disabled; capture failure; valid image and export; file pruning; interruption during write; privacy review and sensitive-image handling.
