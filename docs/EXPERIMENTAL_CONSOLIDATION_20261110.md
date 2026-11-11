# Driverade — experimental consolidation record

Updated: 2026-11-10
Status: **EXPERIMENTAL, COMPILED STATUS PENDING; NOT DEVICE VERIFIED; NOT PROMOTED**

## Why and precise baseline
Owner requested one cohesive experimental build and a safe route to eventual branch cleanup, while preserving all useful new code. Create a non-destructive integration branch rather than indiscriminately merging historical branches or deleting them. Root baseline is the actual installed-build source commit `f9589e82b508f9442128b8a27fc493d5eab4528a`, extended with PR #39's proposed earnings-dashboard patch at `bdf54950d39d2550ddf40bfeb1bdc722d625044b`.

## Reconciliation result
- PR #25 and the PR #27→#28→#29→#30→#31→#32→#34→#37 runtime stack, plus the subsequent live OCR/dedup commit, are **ancestors** of the integration baseline. No redundant cherry-pick; exact git commit ancestry confirmed through GitHub compare, not inferred from PR descriptions.
- Ancestor branches for Android Auto guided setup, tip observations, source-aware offer parsing, diagnostics export, visual capture, window visibility, test infrastructure and earlier security patches are already in the baseline history. Not all functions are field-verified merely because their commits are present.
- PR #39 adds a strictly independent local Uber map earnings observer and dashboard updates; no changes to offer parser decisions, alerts, or privacy defaults. CI tests must run on the new combined SHA, and device behavior remains unverified.
- PR #36 process branch has independent evidence policies, PR template, diagnostic concept and ledger. These documentation files have been incorporated. Current v3.64 build workflow is **retained** because PR #36's older v3.63 workflow expected an Actions-cached keystore and static passwords, in conflict with later secrets-only/manual signing safeguards. Likewise, old AGENTS instructions were reconciled into a new AGENTS document rather than replacing present-day rules.
- The reusable blueprints PR #21 contributes 5 new docs; restored.
- Code vault branch contributes 9 archival reference files; restored without overwriting runtime sources. Its vault-only root README was replaced by a proper active repository README.
- Current `main` roadmap/refresher documents were added as dated historical notes. They should be refreshed with live evidence before stable promotion.

## Divergent branches excluded from direct code merge
- `experiment/consistency-sweep-20261106`: older alternative implementation touching core engine, MainActivity, service settings, and logger. Its work predates the installed restored acquisition/parser; **not** blindly overlaid over newer code. Retain this ref until each unique behavior is verified against the consolidated candidate.
- `feature/platform-specific-offer-parsers-20261106`: historical modifications to diagnostics/LoggerService/MainActivity; later stack uses their descendants or parallel revisions, so no older file overwrite without a behavioral comparison.
- `AdodgerWho-patch-1`: old code and a deployable patch, not the current base; retain for recovery.
- `dev/apk-build-validation`: historical BUILD_VALIDATION notes, not a current tested APK artifact.
- Archive/reusable concept docs are carried, but **no** old experimental parser is promoted to authoritative live parsing merely by consolidation.

## Safeguards and verification
1. CI: Run full `testDebugUnitTest`, compile APK, and inspect resolved files and signing outputs on the *consolidated SHA*. `PASS` requires the actual CI result, not just creation of this branch.
2. Simulator: Validate Uber Exclusive/Match, Lyft/Hich where fixtures exist; reject Uber map total, earnings activity, navigation, stale/OCR duplicate, inactive platform, below-threshold cards. Confirm exact classification and one dispatch per real validated offer.
3. Earnings: Compare observed Uber `Finding trips` map total against dashboard; repeated $42.45 frames remain $42.45, non-map amounts rejected. Validate no raw OCR stored, manual fallback, reset/date boundary, and negative total correction.
4. Device: Preserve installed update identity (package/cert/version code). Confirm live accessibility→parser→gates→overlay, optional screenshot saving/export, privacy switches, logger/alerter modes, background/foreground state, and hourly/earnings display. A debug-signed CI artifact is **not** an in-place update.
5. Archive: Only after full test, save the tested permanent-signed APK to approved Drive Update builds with SHA/build/test manifest, then consider `main` merge with explicit owner authorization.
6. Cleanup: Only close an obsolete PR after confirming its unique code and evidence links are represented by a newer head; delete an old branch only after confirming no unique commits needed or recovery references. No branches deleted as part of this experiment.

Known limits: Uber earnings totals are a provisional passive observer, not completed-ride reconciliation. Lyft/Hich earnings, individual ride settlement and active-hour counting remain separate. Android Auto-only match events may not be visible on the phone. No unsupported claim of a complete release.

## Diagnostic modernization candidate (experimental, November 10)
- Added `FailureTreeRules`, `FailureTreeStore`, and `FailureTreeDiagnostics`: an owner-opted-in local SQLite recurrence index of bounded sanitized categories, cause, stage, first/last time, last capture ID and occurrence count. It records no raw accessibility content.
- Existing diagnostic event logging is preserved; failure-tree indexing errors are contained, and user-facing feature diagnostics displays a short status snapshot.
- Repeated ERROR/WARNING events of the same kind (four in one minute, two-minute cooldown) create `ANOMALY_REPETITION` records; this is a repetition signal, **not** a root-cause diagnosis. Missing-root, disabled OCR and ordinary parser rejections remain observations.
- When the Uber-owned window is visible but no Uber accessibility event has arrived for at least a minute, a throttled `EVENT_SILENCE_CANDIDATE` is recorded. It does not assert that a service stopped; the service may be legitimately idle. No automatic recovery is attempted.
- This is a **first failure-tree foundation**, not parity with the separate Personal Assistant's advanced cross-component diagnostic tree. Remaining future work: unified cross-stage causal graph, persistent capture correlation, independent watchdog, explicit data pruning, missing-event reliability tests, and exported structured incident report. Do not represent these as implemented.
- Privacy and gating: Diagnostic recurrence tracking requires the existing user-enabled diagnostic logger; disabling it does not disable the accessibility engine, parsing, earnings monitoring, or alerts. No remote diagnostic transmission. Device and synthetic tests required before promotion.
