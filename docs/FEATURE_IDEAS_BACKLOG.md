# Driverade — feature and architecture brainstorm backlog

**Updated:** 2026-11-08. **Classification:** Historical brainstorms and candidate designs gathered from available planning conversations, not a feature-completion matrix. [Current checkpoint](NEXT_SESSION_CHECKPOINT.md). **Public-safe summary:** do not add raw screenshots, rider information, precise user mobility history, signing credentials or internal cryptographic-engine designs here.

## Priority A — recognition and alert reliability (under experiment, not certified)

- **Reusable app-specific recognizers:** restore known Uber/Lyft/Hich accessibility and OCR lessons from `Driverlogger3_4`, while supporting app-version/device-specific layouts and safe fallback modes. Inspect real fields and matched bounds before changing algorithms.
- **Three-path capture with causal evidence:** accessibility events, independently timed tree scans, and screenshot OCR each report their own source/provenance; never count the same evidence twice as independent corroboration.
- **Normalized offer evidence:** each candidate property (payout, pickup time/distance, trip time/distance, offer/action identity) should carry source, window ID, time, bounds and confidence; reconcile conflicting hypotheses rather than trusting the first/largest number.
- **Offer state-machine and gates:** distinguish idle/map, actionable offer, accepted ride, earnings history and navigational overlays; compare amount/action/pickup/trip fields in a source-scoped window before alerting.
- **Learning parser profiles (idea):** maintain an opt-in, on-device, validated per-app/version/device mapping learned from successful captures; detect layout changes, quarantine uncertain matches and keep a known-good mapping. No blind online retraining on rider data.
- **Shadow comparator and injector:** test the legacy and replacement parsers against synthetic sequences and sanitized real snapshots, with exact numeric expected outputs and explicit false-positive rejection. Synthetic fixtures are useful but never replace live validation.
- **Nonblocking alerter:** restore missing test mode and visible/sound/vibration pathways, including controlled intensity, debounce and platform-specific presentation. Verify actual overlay + signal delivery, not just an internal callback.
- **Diagnostics as sensors:** source/capture ID correlation across every stage, per-feature self-tests, screenshot export, PNG existence/ZIP validation, privacy controls, predictable error/status classification and regression history. Developer view should expose actionable gaps without leaking secrets.

## Priority B — driver operations and decisions (future / partially explored)

- **Unified multi-gig command center:** services such as Uber, Lyft and Hich first; delivery apps are later adapters, not presumed integrated.
- **Cross-app best-offer selection:** timestamp concurrent candidates, compare configured payout, $/hour, $/mile, travel time and pickup factors, reject stale cards, initially recommend an action before considering automatic foreground switching. Historical Driverlogger/Hich auto-switching is reference behavior to investigate, not verified Kotlin behavior.
- **Earnings and ride economics:** capture total offer amount, mileage, pickup + ride duration, derived $/hour and $/mile, completed trip/tip records, platform-specific metric meanings, and prevent app-supplied 'hourly estimate' text from being mislabeled as payout.
- **Platform-aware rules:** filters, thresholds, Hich scheduling behavior, cooling/debounce, offer status, valid GPS and permission checks; prefer explicit verifiable test cases.
- **UI restoration:** dashboard, alerter/logger mode controls, missing statistics/earnings views, settings and high-information diagnostic mode. UI should reflect verified engine state rather than mock successes.
- **Android Auto:** registration/setup, safe dashboard/alerts and navigation integration only after car-session capability checks. Treat a registered receiver as distinct from a functioning in-car interface.
- **Open-source mapping/navigation:** assess providers and licenses; turn-by-turn guidance, unified destination routing, real-time traffic and construction routing as long-term options. Offline support where possible; real-time traffic intrinsically depends on external data.
- **Personal hotspot intelligence:** store coarse/truncated encrypted location observations locally with an approximately 30-day retention policy; analyze personal demand trends when shared heatmaps are absent. Verify actual persistence and pruning (the existing short-lived in-memory grid is not this feature).
- **Optional crowdsourced heatmaps:** voluntary anonymized, ephemeral-tokenized contributions with privacy-first defaults and server inability to reconstruct rider/driver histories; initially owner-controlled hosting, potentially managed later. Offline operation must remain useful when the shared network is unavailable.
- **Distribution/business model:** one-time feature-tier purchases; avoid mandatory subscriptions or recurring charges.

## Priority C — future reusable toolkit / project separation

- Extract **diagnostic instrumentation, capture/export, parser/evidence comparison and verification harnesses** into reusable architectural blueprints. Draft [PR #21](https://github.com/AdodgerWho/driverade-2/pull/21) contains earlier catalog material, but no standalone reusable package is confirmed.
- Proposed independent **private tools/blueprint library** may be created later; no dedicated repo was verified in this review. Keep ideas indexed here until a private destination exists. Standalone cryptographic-engine is **already a separate private project**; do not copy its internals into this public repository.
- Preserve known-good legacy features before refactors. Backups to owner's private Drive may serve as recovery snapshots, but GitHub branch/commit remains the source reference; never commit personal diagnostic bundles to public GitHub.

## Next brainstorm prompts

1. How should fresh visual evidence bind each numerical field to the correct offer/action window?
2. Which gates must be proven before a payout can trigger an alert?
3. Which feature-specific diagnostics prove the output actually happened?
4. After live parsing works, which historical function should be recovered next: alerter test, best-offer switch, navigation, or heatmaps?
5. How do we separate stable core, experimental diagnosis, private tooling and optional crowd data without recurring network dependencies?

**Do not equate backlog presence with implementation, approval for a merge/build, or device verification.**
