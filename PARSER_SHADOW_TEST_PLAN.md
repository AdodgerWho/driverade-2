# Parser shadow comparison — PR28 follow-up (test-only)

## What is wired

- **Synthetic input:** Dashboard **Inject and compare offer card** sends a platform and newline-separated nodes to OptimizedOfferEngine.
- **Gate:** Original OfferScreenClassifier runs before active parse. Rejected screens remain rejected, regardless of any legacy candidate.
- **Parallel observation:** Active parse and isolated legacy Lyft-rate / Uber-reservation candidates receive the same nodes. The active parser remains the only source of production OfferEvaluation.
- **Diagnostics:** Each injected test generates SYNTHETIC_TEST_RESULT and PARSER_COMPARISON; a small private response broadcast returns the formatted comparison to the dashboard.
- **Live input:** After existing offer-screen gates, Uber and Lyft events also invoke the candidates in a read-only shadow path. Logs are deduplicated for a repeated input signature within 60 seconds. No legacy field changes live decisions, overlays, alerts, counters, Android Auto, or earnings.
- **Other platforms:** HICH is active-parser-only; NO_LEGACY_ADAPTER is expected. Delivery app integrations are outside this test and are not advertised as ready.

## Test expectations (before promotion)

| Case | Active result | Shadow status | What it verifies |
|---|---|---|---|
| Uber ride $18.50 / 25 min / 5.2 mi | PASS, $44.40/hr | NO_LEGACY_CANDIDATE | Ordinary Uber cards still rely on existing logic |
| Uber reservation "Accept • $24.50" / two legs | PASS, $49.00/hr with 30 min / 9.6 mi | PARTIAL | Legacy adapter returns payout but cannot establish complete pickup+trip totals |
| Lyft $18.50, $37/hr est rate, 5 min/1.0 mi + 25 min/8.0 mi | PASS, $37.00/hr calculated | MATCH | Legacy Lyft extraction matches complete active results, while platform-provided est rate is separately labeled |
| Earnings history with payout / Accept | GATE_REJECT | GATE_REJECT | Neither parser bypasses history protections |
| Map pill with payout and duration | GATE_REJECT | GATE_REJECT | No false ride offer |
| Missing distance | GATE_REJECT | GATE_REJECT | Incomplete cards cannot pass |
| HICH baseline card | PASS if gate and fields valid | NO_LEGACY_ADAPTER | HICH is not falsely presented as dual-parser coverage |

**Important:** The legacy Uber adapter calls the first recognized duration/distance pair a "trip" leg, but the text alone does not prove it is pickup vs. trip. Treat this as an unresolved mapping and do not promote that value to production.

## On-device procedure

1. Install only a successfully compiled APK from this experimental branch (keep current working APK until verified).
2. Enable Driverade Accessibility. Open the synthetic injector from the dashboard.
3. Select **Uber**, **Lyft**, or **HICH**, choose a scenario, and press **Run comparison**. Confirm a comparison dialog arrives and a corresponding PARSER_COMPARISON entry appears in exported diagnostics. A missing dialog is an output-path failure even when a log exists.
4. Compare the displayed active numbers, candidate numbers, statuses and diagnostics with the table above. Use a custom Lyft card containing two payout amounts to deliberately test DIFF.
5. Verify that today's offer counts, live offer history, Android Auto messages, and live alerts remain unchanged after synthetic testing.
6. Separately, during a genuine *actionable* Uber/Lyft offer, inspect PARSER_COMPARISON source=LIVE_SHADOW and the existing parse/gate logs. No comparison entry is required for rejected map pills (those are already logged at their gates).
7. Run Feature Diagnostics: three shadow fixture checks should PASS; dashboard result delivery remains BLOCKED until actually observed on device.
8. Export diagnostic text. Verify parser comparison rows include source, capture, gate, status and numeric fields **but not raw offer nodes**. Other pre-existing diagnostic categories have separate privacy rules.

## Promotion gates

- CI JVM tests and Android assembleDebug must pass.
- On-device dialog delivery and diagnostic export confirmed.
- Validate on anonymized real Lyft and Uber reservation fixtures. Synthetic fixtures do **not** prove live accessibility capture, OCR, screenshot ZIP export, or end-to-end alert delivery.
- Do not merge to main or make the legacy adapters authoritative until the field mapping, disagreements, and privacy implications are understood.
