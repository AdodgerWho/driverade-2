# Driverade — next controlled iteration roadmap

Status: **working plan, not merge authorization**. Updated 2026-11-08 on PR #37 experimental branch. The owner authorizes more implementation initiative, **not** reduced testing, automatic merges, broad redesigns or bypass of privacy safeguards.

## Working agreement
- Owner defines expected behavior, supplies field observations, reviews meaningful design tradeoffs and decides whether to promote a build.
- Assistant may proactively inspect the code, write narrow fixes, add regression tests/diagnostics, and prepare build/test instructions and PR changes.
- Discuss uncertainty, cross-feature changes, privacy/security compromises, destructive steps, and major architectural decisions before acting.
- Preserve functioning behavior first; instrument and reproduce before changing logic. One independently verifiable layer at a time. No unverified PASS claims.

## Baseline (installed test)
- Repo `AdodgerWho/driverade-2`, branch `experiment/legacy-three-path-parser-pr37-20261107`, PR #37 stacked on #34.
- Installed update: v3.64, versionCode 10257, workflow run 42526682710, SHA 64c196672fc321e2964ddd5d65ee9e3559a04641.
- Update-over-existing and basic UI startup observed. New-build diagnostics show engine/feature checks, but no live offer result or alert. Older records in cumulative logs are not current-build evidence.
- Visual Debug Capture off in current new-build diagnostic snapshot. Do not confuse absent PNGs with failed OCR.

## Stage A — close parser observability and fixture gap (NEXT)
- [ ] Run **17** synthetic fixtures on the actual installed build; verify exact values and gate decisions, not summary count alone.
- [ ] Ensure synthetic injection cannot publish live offers or alter real stats, alerts or earnings.
- [ ] Observe a real actionable Uber offer; capture source/window provenance and correlation IDs through tree/timed/OCR, candidate, evaluation, output and alert.
- [ ] Check Comfort/Electric/Comfort Electric service handling and strict two-leg requirements.
- [ ] Negative checks: map pill, history, wallet, nonactionable reservation, stale screenshot, foreign-app display, ambiguous payout.
- [ ] If failure: isolate first broken transition, add minimal regression, fix only that transition, repeat tests.
**Exit:** at least one visually corroborated real offer with correct payout, pickup and trip legs and single correct dispatch; negative screens rejected.

## Stage B — diagnostic integrity and integration
- [ ] Test PNG opt-in ON, verify actual file and ZIP readback, 8s pre/10s post rolling capture where triggered.
- [ ] Test opt-in OFF, no new retained PNG, transient OCR still operates; note old retained files may remain.
- [ ] Confirm text-only export, capture-ID correlation, dedupe, resource bounds, privacy-safe logs.
- [ ] Test background/foreground, Lyft/Hich when field events or trustworthy fixtures are available.
- [ ] Recheck build, Semgrep, permissions, branch/main divergence, update signing continuity.
**Exit:** diagnostics support reproducible failures and integration gates met; owner explicitly approves any merge.

## Stage C — completed-trip earnings (AFTER A/B)
- [ ] Observe completion screen and capture actual payout, trip status, timestamp and safe matching metadata.
- [ ] Link completion to offer without assuming equal payout; record positive/negative adjustments separately from tips.
- [ ] Handle missing offer, delayed completion and platform corrections.
- [ ] Keep history/balance screens excluded from live-offer parsing.

## Stage D — ride-history tip reconciliation (AFTER C)
- [ ] Implement distinct history-row recognition and component classification.
- [ ] Lyft: `incl. $X tip` is included in headline.
- [ ] Uber: labeled `Tip`, `Pro Perk`, `Flash Incentive`; verify whether headline includes them before arithmetic.
- [ ] Link history to completed trip, dedupe revisits, confirm/correct provisional map-total hypotheses, avoid double count.
- [ ] Test delayed tips, incentives, mismatched amounts, cross-midnight periods and missing trip records.
- [ ] Store minimal on-device data; never put unredacted routes/rider data in public repo.
**Exit:** known labeled tips reconcile to trip earnings without double counting, with unresolved cases marked uncertain.

## Evidence and release discipline
For every change record: source commit/build ID, affected function, hypothesis, expected/actual result, fixture or capture ID, PASS/FAIL/BLOCKED, regression coverage, signing fingerprint, and privacy implications. Distinguish successful compile, synthetic pass, field validation, and merge approval.

Related references:
- [RESTORED_LEGACY_TRIAL.md](RESTORED_LEGACY_TRIAL.md)
- [INTEGRATION_FUNCTION_TEST_SHEET.md](INTEGRATION_FUNCTION_TEST_SHEET.md)
- [EARNINGS_HISTORY_RECONCILIATION_NOTES.md](EARNINGS_HISTORY_RECONCILIATION_NOTES.md)
