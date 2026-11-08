# Restored Driverlogger3_4 parser — on-device trial

Status: experimental. Stacked on PR #34; not approved for main.

Original reference: Driverlogger3_4/LoggerService.java, Offers.java, Rules.java.
The active parser is a Kotlin port of the legacy design with stricter payout and two-leg safeguards.

## Acquisition paths
1. Event-triggered accessibility: watched window and notification events examine each app-owned root separately. Diagnostics: RESTORED_TREE_PARSE, RESTORED_TREE_MISS, UBER_FIELD_PROVENANCE, PARSER_COMPARISON.
2. Independent timed reinspection: every 1200 ms while screen is interactive, examine watched windows. Recent Uber events also allow OCR retries for eight seconds.
3. On-device OCR: try the specific Uber window through Android API 34+; fall back to display capture only when the window is unavailable and log it. OCR text is not saved in routine diagnostics. Diagnostics: VISUAL_DEBUG_CAPTURE_SUCCESS, RESTORED_OCR_PARSE.

The restored parser is authoritative for Uber/Lyft/Hich offer decisions. Existing alert toggle, overlays, Android Auto, stats and debug export remain connected. The previous flat largest-dollar parser no longer determines live offers. OfferParserComparison is read-only.

## Required trial

- [ ] CI unit tests and APK build pass at exact head SHA.
- [ ] Confirm APK signing compatibility before installing over current version.
- [ ] Run all 12 batch fixtures. Verify exact payout, minutes and miles, not only PASS/GATE_REJECT. Expected: SYNTHETIC_BATCH_SUMMARY 12/12.
- [ ] Confirm synthetic runs do not affect live alerts, counters or stats.
- [ ] Observe one real Uber Match or Exclusive in background; inspect TREE_MISS/TREE_PARSE, capture source, RESTORED_OCR_PARSE and PARSE_RESULT by capture IDs.
- [ ] Compare screenshot with parsed payout and both pickup/trip legs, and confirm only one alert after retries.
- [ ] Verify non-offer map pill / navigation does not result in PARSE_RESULT.
- [ ] Verify visual-debug ON exports actual PNG; OFF saves no PNG while live OCR can still run.
- [ ] Recheck Lyft/Hich on available captured fixtures and with live events as available.
- [ ] Review battery/CPU and ensure no raw OCR or rider data appears in ordinary diagnostic text.
- [ ] With Visual Debug Capture ON, observe ROLLING_STAGED before an offer, ROLLING_TRIGGER on an offer/window/visual change, ROLLING_FRAME_SAVED for both PRE_ROLL and POST_ROLL with shared capture IDs, and verify the exported ZIP contains readable PNGs.
- [ ] Turn Visual Debug Capture OFF while a buffer is active: verify ROLLING_CLEARED, no further new PNGs, no automatic uploads, and normal transient live OCR remains available.
- [ ] Confirm periods without an offer or visual trigger can leave zero PNGs without being counted as screenshot pipeline failures.

## Numeric reference cases
- Uber Exclusive: $5.53; pickup 3 min/0.7 mi, trip 7 min/2.1 mi, total 10 min/2.8 mi, $33.18/hr.
- Uber Match: $6.74; pickup 6 min/2.4 mi, trip 7 min/2.3 mi, total 13 min/4.7 mi, $31.11/hr.
- Historical conflict: Accept $12.04 and unrelated $13.08 must select $12.04 or reject if provenance unavailable.
- Lyft: $18.50 payout and distinct $37/hr app label, 5+25 minutes and 9 miles.

## Important limits
- The old image first-dollar assumption is intentionally discarded; two distinct leg pairs are mandatory, not guessed.
- Raw OCR is transient; raw accessibility trace persistence requires user-enabled Developer Inspection.
- **Restored rolling OCR diagnostic frame buffer:** with Visual Debug Capture explicitly on, Driverade stages compressed image previews in app memory for **8 seconds before** an interesting event and saves the next **10 seconds** after. Memory is bounded to **10 previews / 8 MB**, duplicate previews are suppressed, and remaining previews expire automatically. An Uber window transition, substantial visual change, likely OCR offer card, or recognized accessibility-tree offer can trigger promotion.
- The opt-in rolling previews are used to find the correct offer frame, not as an additional authoritative live parser. **Live OCR still uses the original full-resolution screenshot**, while selected buffer previews are converted into the existing verifiable PNG export path. Existing exported PNG count remains capped at 100, and routine text diagnostics contain only capture IDs / reason codes, not raw pixels or OCR strings.
- **Prior to an offer:** while an Uber-owned window is available and screenshot opt-in is enabled, timed captures create the pre-roll history. **After a trigger:** further frames are retained for 10 seconds. Turning the switch off immediately discards the in-memory buffer but does not delete already exported/saved files. An opt-in session without any trigger may legitimately produce no PNGs.
- This is an opt-in diagnostic refinement, **not a live parser correctness or merge prerequisite**. The original Driverlogger3_4 retention configuration (8s/10s and grayscale change thresholds) informed it; new memory caps and PNG export are Driverade-specific.
- Synthetic text OCR fixtures do not establish real screenshot recognition; the next live Uber offer is required.


## November 7 diagnostic baseline and correlation hooks (PR #37)

The user's *installed* diagnostic baseline is from **PR #34**, not this uninstalled PR #37:
- Build ID `c3cee61a968cd3f2f6d964a9c59987b57f670bb3` (version 3.64); captured 2026-11-07.
- Explicitly enabled Developer Inspection yielded 8 private Uber hierarchy records, including readable Uber-owned roots in several captures.
- The exported screenshot ZIP contains **24 PNGs**, and its `VISUAL_DEBUG_EXPORT_PRECHECK` says `enabled=true imageCount=24`. This is evidence that screenshot persistence was opted in at the time, **not** by itself a capture bug.
- No `RESTORED_*` markers can exist in that baseline, as PR #37 is not installed. Never label it a live test of the restored three-path parser.
- Private raw JSONL and screenshot ZIP belong only in the user's restricted Drive diagnostics archive; **do not commit or upload them to this public repository**.

Instrumentation added directly to this PR's existing branch (without changing parser acceptance, alerts, OCR semantics or screenshot opt-in):
1. `RESTORED_DIAGNOSTIC_READY`: active build's diagnostic readiness and persistence-mode state.
2. `RESTORED_ACQUISITION`: event vs timed acquisition, target window ID, app-owned root count and event-source ownership; `RESTORED_TREE_PARSE/MISS` includes trigger and parser reason.
3. `RESTORED_TIMED_OCR_TRIGGER`: why a timed image path was requested.
4. `VISUAL_DEBUG_CAPTURE_SUCCESS`, `VISUAL_DEBUG_PNG_SKIPPED` and `VISUAL_DEBUG_FILE_VERIFIED`: distinguish an in-memory screenshot used for OCR from a PNG deliberately retained on disk. Turning screenshot saving **off** does not delete earlier saved PNGs.
5. `VISUAL_DEBUG_OCR_REQUESTED/COMPLETED`, `RESTORED_OCR_PARSE/SKIPPED`: correlate local ML Kit OCR completion, listener connection, gate decisions, and stale-event skips; never write raw OCR text to routine diagnostics.
6. Opt-in `DeveloperInspection` private JSONL now includes the same `captureId` as the ordinary `DEV_INSPECTION_CAPTURE` and `PLATFORM_MATCH` logs. This is available **only in a build containing this change**, not retroactively for the baseline.

### Next device verification on this exact PR build
- Record the Build ID and ensure the revision is this PR's updated head.
- With visual-debug PNG persistence **off**, observe an Uber accessibility event. Check the capture ID across `PLATFORM_MATCH`, `RESTORED_ACQUISITION`, `RESTORED_TREE_PARSE/MISS`, `VISUAL_DEBUG_OCR_COMPLETED`, and `RESTORED_OCR_PARSE/SKIPPED`. `VISUAL_DEBUG_PNG_SKIPPED` means no **new** PNG for that image. Existing PNGs may remain from earlier sessions.
- If an actual offer appears, compare observed offer fields with `PARSE_RESULT` and `ALERT_DISPATCH/ALERT_DISABLED`; verify one alert and no map-pill false positives.
- With PNG saving on deliberately, expect `VISUAL_DEBUG_FILE_VERIFIED` and a readable opt-in ZIP with the reported count. Do not confuse `CAPTURE_SUCCESS` (a transient bitmap) with `FILE_VERIFIED` (persisted PNG).
- Distinguish the private inspector's every-few-seconds throttle from normal event parsing. If no matching private JSONL entry exists, that does not imply the regular event path failed.
- Treat feature-check `BLOCKED` as an unperformed live test rather than a failure or certification; synthetic batch tests are separate.
