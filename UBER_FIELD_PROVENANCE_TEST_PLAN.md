# Uber field provenance verification (PR #30)

This draft is stacked on PR #29. Do not merge until a device build and negative/positive gates are validated.

## Synthetic injector (no side effects)
Use existing **Test Offer Card** / side-by-side comparison UI with platform UBER.

**Case A — historical conflict, unstructured synthetic nodes**

```text
Trip request
Accept • $12.04
$13.08
13 min (3.3 mi) trip
```

Expected: `UBER_FIELD_PROVENANCE` with `payoutSource=ACCEPT_CONTROL`, `verifiedPayout=12.04`. Note: synthetic nodes do **not** contain Android view IDs, so trip label binding is deliberately `NA` in the provenance diagnostic. The existing synthetic active-path calculation still uses its previous flat-text behavior; differences are intentional evidence, not live alerts.

**Case B — no actionable offer**

```text
Home
$73.52
2 min
1 mi
Start navigation
```

Expected gate rejection and `NO_BOUND_PAYOUT`; no live offer side effects.

**Case C — history suppression**

```text
Your earnings
Trip
Accept
$101.83
20 min
4 mi
```

Expected `history_screen`, no offer.

## Real-capture verification

1. Install CI-built debug APK from the PR #30 head, not the previous PR #29 build.
2. Enable feature diagnostics, observe a **genuine incoming** Uber offer or reservation.
3. Export text log and screenshots. Search by capture ID for `UBER_FIELD_PROVENANCE`, `NODE_SCRAPE`, `UBER_OFFER_GATE_DROP`, `PARSER_COMPARISON`, `PARSE_RESULT`.
4. Verify that `payoutSource` is `ACCEPT_CONTROL` or `OFFER_HEADER` and that `verifiedPayout` matches the screenshot. When multiple windows/conflicting fields are present, verify the resolver declines confidence.
5. Compare total time/mileage with the actual pickup **and** trip legs. This PR does not yet independently bind pickup leg values; do not treat plausible totals as proven.
6. Repeat with pickup navigation, earnings and wallet screens. These must not create new offers.
7. Test Lyft and Hitch unchanged; confirm output and screenshot ZIP exporter separately (not modified in this PR).

## Current implementation boundary

- The resolver is *used* for live Uber payout selection when it finds a single, nonconflicting offer-bound amount. It restricts parsing to the associated window in that case.
- When it cannot bind a payout, the preexisting live evaluation path remains active to avoid regressing unknown ordinary Uber layouts. This fallback can still encounter the prior largest-dollar issue; its diagnostic status identifies missing provenance.
- The read-only synthetic injector reports provenance but does not supply actual Android view IDs and does not simulate full structured window selection.
- Historical regression cases include a confirmed `$12.04` offer header / Accept control and unrelated `$13.08` notification. This is not a guarantee that modern Uber renders identical resources.
- Build and unit tests must pass in CI before device installation. This document does not claim they have run.
