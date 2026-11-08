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

## Numeric reference cases
- Uber Exclusive: $5.53; pickup 3 min/0.7 mi, trip 7 min/2.1 mi, total 10 min/2.8 mi, $33.18/hr.
- Uber Match: $6.74; pickup 6 min/2.4 mi, trip 7 min/2.3 mi, total 13 min/4.7 mi, $31.11/hr.
- Historical conflict: Accept $12.04 and unrelated $13.08 must select $12.04 or reject if provenance unavailable.
- Lyft: $18.50 payout and distinct $37/hr app label, 5+25 minutes and 9 miles.

## Important limits
- The old image first-dollar assumption is intentionally discarded; two distinct leg pairs are mandatory, not guessed.
- Raw OCR is transient; raw accessibility trace persistence requires user-enabled Developer Inspection.
- Legacy screenshot pre-roll/post-roll buffering is NOT yet ported. The separate existing opt-in exporter currently retains at most 100 PNGs.
- Synthetic text OCR fixtures do not establish real screenshot recognition; the next live Uber offer is required.
