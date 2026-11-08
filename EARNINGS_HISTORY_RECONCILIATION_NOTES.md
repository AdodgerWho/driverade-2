# Earnings history and tip reconciliation — visual reference notes

Status: deferred design reference; **not implemented**. Captured from two user-provided screenshots on 2026-11-08. Do not commit original screenshots, maps, street-level locations, rider data, or raw diagnostic bundles to this repository.

## Lyft day history screenshot
- Day selector and date navigation; daily total displayed separately.
- Individual ride rows show monetary amount, platform "Lyft", and time.
- One ride row shows **$11.01 Lyft**, with a distinct green label **"incl. $5.00 tip"**. This means the $5.00 is included in the displayed $11.01, not additive.
- Other visible rows ($17.06, $25.06) have no tip label visible. Absence of a label in a screenshot is not proof that no tip ever occurred.
- A daily total ($53.13) and an earnings-stat amount are distinct UI elements; do not parse them as new offers or individual tips.
- "See all daily activity" leads to more granular records.

## Uber Earnings Activity screenshot
- Earnings Activity view with Type, Feature, and date-range filters, and day headings.
- Trip A: headline **$10.32**, **UberX**, **7 min 4 sec**, **1.52 mi**, **1:48 PM**; labeled **$2.00 Tip**, **$0.28 Pro Perk**, **$2.50 Flash Incentive**.
- Trip B: headline **$27.21**, **UberX**, **17 min 27 sec**, **12.66 mi**, **1:33 PM**; labeled **$7.18 Tip**, **$0.62 Pro Perk**, **$7.00 Flash Incentive**.
- Rows include trip map and pickup/dropoff labels. Do not retain exact addresses in shared diagnostics.
- **Unverified:** Whether Uber headline earnings already include each labeled component. Confirm with detailed trip earnings before performing additive arithmetic.
- The history is a scrollable list; distinguish distinct trip rows and associate each labeled component with the correct parent row.

## Parser / reconciliation requirements
1. Keep separate records for initial offer quote, end-of-ride displayed payout, ride-history headline, and labeled compensation components (tip, incentive, perk, adjustments).
2. Preserve source, event time, extraction confidence, and amount inclusion semantics (included / additional / unknown); do not guess the latter.
3. Match records by platform, date/time window, service, duration/distance, and other safe available signals. A trip ID is preferable if accessible; do not assume one is present.
4. Treat completion-screen payout as a snapshot of trip earnings at completion, subject to later corrections. Compare with offer quote to detect over/underpayment; don't label the difference as a tip.
5. Lyft "incl. $X tip": subtract from the ride headline only when computing a non-tip component; never add the tip again.
6. Uber "Tip", "Pro Perk", and "Flash Incentive" are separate semantic categories. Do not assume they are additive to the headline until validated.
7. Map/running-total changes are reconciliation signals, not authoritative evidence of tips. Explicit history labels can confirm or correct provisional tip hypotheses.
8. Deduplicate history re-reads and repeated accessibility/OCR events by stable event identity and trip linkage; no double-counting on revisits.
9. Do not misclassify historical earnings rows, daily totals, balances, or stats as live offers.
10. Keep privacy-first handling: process on-device; avoid retaining route maps, pickup/dropoff addresses, rider identifiers in persistent logs or shared fixtures. Development diagnostics opt-in.

## Validation dependencies
- First: reliable live offer parser and end-to-end output.
- Then: completion-screen parsing and final-payout linkage.
- Then: history row parsing, inclusion semantics validation, tip reconciliation.
- Test both positive and negative payout adjustments, delayed tips, missing completion events, duplicate history scans, and cross-midnight/platform day boundaries.
- The screenshots are visual references, **not** evidence that the parser currently supports these fields.
