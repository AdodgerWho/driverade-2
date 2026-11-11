# Diagnostic engine blueprint

**Status:** Implemented inside Driverade; standalone extraction and end-to-end verification pending.

## Existing source
- `app/src/main/java/com/driverade/service/InternalDiagnosticsLogger.kt`: asynchronous event logging to text and SQLite using a single-thread executor, bounded at 2,000 queued jobs.
- `app/src/main/java/com/driverade/service/DiagnosticSqliteStore.kt`: SQLiteOpenHelper for `events(id,time_ms,category,detail)`, 30-day and 50,000-row pruning.
- `app/src/main/java/com/driverade/service/FeatureDiagnostics.kt`: feature probes with PASS, FAIL, and BLOCKED semantics.
- `app/src/main/java/com/driverade/service/BugReportManager.kt`: diagnostic bundle/export integration (inspect current implementation before extraction).

## Proposed reusable API
`DiagnosticSink.record(category, detail, timestamp)`; `DiagnosticStore.query(range, category)`; `DiagnosticProbe.run(): CheckResult`; `DiagnosticExporter.export(destination, policy)`.

## Extraction dependencies
Android Context, SQLiteOpenHelper, app-private files, DriverPreferences, app-specific feature checks and screenshot exporter. Split into a generic event store and app-specific probe adapters. Keep debug UI optional; lightweight crash/health checks should not depend on debug UI.

## Known gaps and cautions
- SQLite rows are not yet verified as included in diagnostic exports.
- SQLite storage is **not encrypted** merely because it is app-private.
- The text file retains untruncated `details` while SQLite truncates to 1,000 characters; both require privacy review.
- Pruning currently triggers on `time % 97L == 0L`, not a predictable maintenance schedule.
- Asynchronous writes can be dropped above queue limit; the diagnostic probe's immediate file check is not proof that its newly queued event was persisted.
- A simple fatal-exception handler is not an ANR detector.
- No independent module build, migration tests, crash-recovery tests, or load tests established.

## Acceptance tests
Prove queued event persisted; restart/recover; export includes database events; privacy redaction; disk-full behavior; SQLite migration; retention schedule; 2,000+ event burst without main-thread blocking; PASS/FAIL/BLOCKED accuracy.
