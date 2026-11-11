# Driverade legacy comparison — 2026-11-05

## Scope and evidence
Compared current GitHub `main` files `app/src/main/java/com/driverade/ui/MainActivity.kt`, `service/BugReportManager.kt`, `data/DriverPreferences.kt` against legacy Google Drive documents `MainActivity.kt`, `BugReportManager.kt`, `DriverPreferences.kt`, `fragment_dashboard.xml`, and `Driverade_v3.63_Architecture_Summary.md` (Drive folder ID `15c_4VezNnH2ZqvISXllKtrVde4jK2WEF`). Also inspected folder structure for legacy DriverLogger (`1WqdBGLeuj_InAHPK2WC_RNCo0lQIbYnH`) and one older Driverade tree (`13X3sFllzA4-zBxl6NDmDNcMV91dUky4c`). Their nested code folders currently returned no readable file contents through Drive search, so no claims about their functions are made.

## Confirmed regressions
1. **Manual diagnostics test**: legacy `BugReportManager.runDiagnosticTest()` appends a timestamped `[TEST_TRIGGER]` marker and shows a toast. Legacy `MainActivity` binds `btnRunDiagnosticsTest`, and legacy XML declares the button. All are absent in current files.
2. **Error reporting opt-in**: legacy `DriverPreferences.errorReportingOptIn` is stored in SharedPreferences with a default of `false`; legacy UI has `switchErrorOptIn`. Current preferences and UI binding omit both.
3. **Caught error helper**: legacy `BugReportManager.handleCaughtError()` logs exception class/message to local diagnostics; current `BugReportManager` omits it. Note: the old implementation does **not** transmit reports automatically; the opt-in only affects a toast. Do not describe it as an operational automatic reporter.

## Retained features
- Manual email diagnostics intent in current `BugReportManager`.
- Current local log export to Downloads.
- Current dashboard offers, thresholds, platform filter UI, shift summary, and heatmap status summary are visible in `MainActivity`; functional wiring not verified.

## Priority recommendations
1. Restore a **local diagnostics test** independently of the actual offer/alerter test; avoid conflating them.
2. Implement a **real alerter test** that exercises notification/overlay/sound without creating a fake live offer or polluting telemetry.
3. Restore a privacy-first **error-reporting opt-in**, default off; distinguish local error logging from any future transmission. Audit redaction, consent, and retention before enabling automatic sending.
4. Check current XML IDs and Gradle build compatibility, then test on device.
5. Inspect DriverLogger and older Driverade code only when readable files are available; do not infer absent features from empty folder listings.

## Safety
No app code changed; no builds run. Legacy source is unverified and must not be merged wholesale.
