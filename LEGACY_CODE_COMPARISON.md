# Legacy code comparison against PR #27

Baseline: PR #27 head 1eba67eaea75d0762dd2e959ec11a6a77dde9d80 (not main).

## Already present in PR27
- Opt-in screenshot capture via VisualDebugCapture, with capture failure reporting and PNG verification.
- ZIP export diagnostics, feature checks, structured capture IDs, and conservative offer-screen gates.
- Existing offer evaluation, overlay, and Android Auto telemetry.

Do not replace these with the older monolithic Java LoggerService.

## Candidate improvements from Driverlogger3_4
1. Offers.java: Lyft estimated-rate detection and Uber reservation-specific Accept/payout matching. Ported as **isolated, unused** LegacyOfferCandidates.kt for evaluation.
2. Rules.java: partial scheduled-ride merge and quiet-hours decision logic. Defer until supported platform-specific fixtures and explicit product requirements are available.
3. Flasher.java: non-touch-intercepting visual pulse and configurable vibration/audio. Review against existing alerter; do not change live behavior without device tests.
4. LoggerService.java: rolling screenshots and visual-change deduplication. PR27 already has opt-in capture and export; compare retention, event timing and failure telemetry before considering changes.

## Safety and acceptance
- No change to PR27's active parsing, offer gate, capture, signing, permissions, or diagnostic export.
- Do not auto-accept rides or silently relax the offer gate.
- Test adapters with actual anonymized Lyft and Uber reservation fixtures, including misleading map totals, incomplete cards, duplicate windows, missing legs, and rate-only labels.
- Verify compile and device diagnostics before wiring any adapter into OptimizedOfferEngine.
- Compare against other open PRs before promotion; this branch is experimental and must not be merged directly.
