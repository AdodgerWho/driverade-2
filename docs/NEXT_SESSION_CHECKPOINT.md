# Driverade — conversation and development checkpoint

**Updated:** 2026-11-08. **Status:** Existing Android/Kotlin app plus unmerged experimental feature stack; some CI/build success, live Uber offer correctness **not yet established**.

**Coding-context rule (2026-11-09):** Before each substantive Driverade code change or review, consult this checkpoint, current architecture and the [mandatory roadmap-refresh / architecture-writeback protocol](BRAINSTORMING_PROTOCOL.md). Reconcile any owner-approved design change in the project's records before implementation. The details below are historical to their stated date; verify current PRs and hardware evidence.

## Where we stopped

Goal: restore the gig-work command-center's reliable real **Uber/Lyft/Hich offer acquisition → validated parser → configured rules/gates → nonblocking alerter**, using actual phone evidence. Make it **work**, prove that it works, then refine implementation. Avoid unnecessary breakage during Kotlin migration. This is the immediate revenue-supporting priority before broader personal-assistant development.

- **Active development repo:** `AdodgerWho/driverade-2` (this repo). Older `Driverade`, `Driverlogger`, `Driverlogger3_4`, and `test*` repositories are **legacy reference sources**, not competing active projects; compare by actual code, not repo naming chronology.
- **Experimental PR:** [#37](https://github.com/AdodgerWho/driverade-2/pull/37), head `experiment/legacy-three-path-parser-pr37-20261107`, verified at GitHub head SHA `b5d971a3e5c86219e331de4268f2b86bf2722c9f` at this checkpoint, stacked on PR #34 (itself stacked on other experiments), **not on main**.
- **Automated checks:** [PR #37 CI run](https://github.com/AdodgerWho/driverade-2/actions/runs/42440064518) completed successfully, and [manual run #236](https://github.com/AdodgerWho/driverade-2/actions/runs/42449062494) for that head completed successfully. A successful workflow is **not** a live parser/alert verification. Review actual artifact signing identity and installed build revision before replacing anything on the phone.
- **Trial instructions:** [RESTORED_LEGACY_TRIAL.md on PR #37](https://github.com/AdodgerWho/driverade-2/blob/experiment/legacy-three-path-parser-pr37-20261107/RESTORED_LEGACY_TRIAL.md); that branch document is authoritative for specific synthetic cases and event/PNG correlation names.
- **Actual prior installed diagnostic baseline:** PR #34, not PR #37. The baseline documents opted-in saved PNGs and Uber hierarchy inspection, **no confirmed live offer through the restored #37 parser**. Do not mistake old events/screenshots for evidence of #37 behavior.
- Other relevant integration/process work: [PR #36](https://github.com/AdodgerWho/driverade-2/pull/36) unmerged CI/promotion candidate; do not silently mix it into the parser work or claim it landed.

## Existing experimental approach (code present on PR #37, not necessarily main)

1. **Event-triggered app-window accessibility inspection** with source/window provenance.
2. **Timed app-window reinspection** approximately every 1200 ms to catch missed accessibility updates.
3. **Transient device-local screenshot OCR** to handle visually rendered Uber offers when accessibility tree data is absent/incomplete. Independent diagnostics permit opt-in PNG persistence; turning PNG persistence off must **not** disable transient live OCR.
4. A strict `RestoredOfferParser` rather than interpreting the largest visible dollar amount as payout. Requires offer context, valid action/ride state, pickup **and** trip leg pairs, source identity, and rule/gate validation. Distinguish real offers from map pill, earnings history, navigation, and accepted/completed trips.
5. **Source-aware diagnostics:** Correlate each evidence path through capture IDs and gate decisions. Side-by-side legacy vs new parsing is diagnostic/shadow output, not automatically authoritative.
6. Optional **rolling visual debug buffer** for examining pre/post offer frames (as defined in PR #37's trial document), opt-in and bounded; not a second authoritative live parser.

These items are not a statement that device behavior has been verified. Device API constraints, Uber updates, timing and OCR ambiguity must be tested with **fresh** captured evidence.

## Resume with this concrete test sequence

1. Identify the installed APK's actual branch/commit, signing certificate, versionCode and diagnostic flags. Distinguish PR #34 baseline from any newer PR #37 install. Preserve app data, settings and private diagnostics; don't promise an in-place update unless cert and versionCode are compatible.
2. Confirm synthetic batch tests and exact values before real-world inference: $5.53 Uber Exclusive and $6.74 Uber Match pickup/trip legs; $12.04 vs unrelated $13.08; Lyft payout vs an unrelated $/hour label. Synthetic success is **not** real app recognition.
3. While a live offer is visible, capture app/source/window state and correlate `PLATFORM_MATCH`, `RESTORED_ACQUISITION`, `RESTORED_TREE_PARSE/MISS`, `VISUAL_DEBUG_OCR_COMPLETED`, `RESTORED_OCR_PARSE/SKIPPED`, `PARSE_RESULT` and alert dispatch by capture ID. Confirm the actual ride-card fields, not a floating map pill or a false $73 offer.
4. Verify screenshot capture, **saved PNG**, ZIP export and readable image are **separate** observable stages. OFF mode should save no **new** PNG but may continue transient OCR; previously saved files may persist. Keep raw captures restricted to the owner's private storage, never this public repo.
5. Confirm one valid offer → one correct rule evaluation → one actual visible/sound/vibration alert, and zero alerts from non-offers; verify no silent gate bypass. Include Lyft/Hich as captured tests allow.
6. Record observed results and precise failures as **PASS / FAIL / BLOCKED / NOT TESTED**. Fix the earliest failing layer rather than rewriting the entire chain blindly. Only promote verified features to main through review.

## Operational safeguards and decisions

- Privacy by default. Keep rider material local. Diagnostic evidence should be opt-in, bounded, redacted and explicitly authorized. High-visibility developer inspection is a temporary testing tool, not automatically a production default.
- **Test coverage is itself a requirement:** the internal diagnostic must verify every feature it claims to verify, including action-to-output confirmation, with no unobserved critical handoffs. A feature checklist isn't evidence.
- User favors function-by-function restoration and preservation of working behavior over UI polish or architectural elegance until live capture works.
- Alerter includes nonblocking visual pulse/overlay with useful payout context and sound/vibration as applicable; ensure overlay doesn't steal touch input.
- Driverade licensing goal: **one-time payment, feature-based tiers, no recurring subscription**.
- Offline-first local behavior; network heatmaps or shared demand data are optional/availability-dependent. No user/device-specific data uploaded unless permitted through a privacy-preserving, opt-in scheme.
- Preserve signed update continuity and settings; PR builds/debug certificates are different from owner-signed update artifacts. Automatic CI success ≠ owner-signed APK.
- App enhancement ideas, deferred features and reusable toolkit concepts are preserved in [FEATURE_IDEAS_BACKLOG.md](FEATURE_IDEAS_BACKLOG.md).

## What is NOT proven

- Live Uber parser recognition and correct alert output on **the PR #37 build**.
- Reliable PNG exporter behavior in every mode and every screenshot lifecycle, despite successful earlier opt-in captures.
- Full Android Auto runtime integration, open-source route guidance, traffic/construction rerouting, or a persistent encrypted 30-day hotspot history.
- Delivery-platform parser integration. Do not present future modules as hooked up.
- A merged stable integration of every recent experiment.

**Next-session starting question:** Which exact commit/APK is now installed, and what do the newest correlated live Uber capture, fields, gate and alerter outputs show? Use the PR #37 trial plan once that is established. No installation or build automatically triggered by this note.
