# Driverade PR verification ledger

**Snapshot:** 2026-11-08. **Purpose:** Navigational inventory, not an authorization to merge, close, delete or deploy. Verify current GitHub status and SHA before every action. This public file contains no private screenshots or rider diagnostics.

**Stable target:** `main`; the policy's intended verified baseline, **not a claim** that all prior desired runtime features exist there. Experimental APKs must not be confused with stable source. See `docs/NEXT_SESSION_CHECKPOINT.md` for the latest project-specific runtime checkpoint.

## Active tracks

| PR | Track / base | What is evidenced | Status / next decisive check |
| --- | --- | --- | --- |
| [#36](https://github.com/AdodgerWho/driverade-2/pull/36) | **Process integration candidate**, base `main` | Combines proposed PR CI and `AGENTS.md` promotion policy; this standard adds docs and a PR template. No intended Android runtime edits. | **OPEN, NOT YET MERGED.** Verify CI on resulting head, diff contains no app/permission/signing changes, and gain owner approval before integration; historical green CI does not cover new commits. |
| [#33](https://github.com/AdodgerWho/driverade-2/pull/33) and [#35](https://github.com/AdodgerWho/driverade-2/pull/35) | Earlier independent policy/CI proposals, base `main` | Intended content included in #36. | **OPEN, candidate supersession only.** Do not close until exact inclusion is compared and #36 is verified and merged. |
| [#37](https://github.com/AdodgerWho/driverade-2/pull/37) | Experimental live acquisition/parser, stacked on #34 | Project checkpoint records successful CI runs for the then-tested head `b5d971a3e5c86219e331de4268f2b86bf2722c9f`. Three paths: accessibility events, timed reinspection, transient OCR; parser/gate diagnostics. | **OPEN, LIVE VALIDATION BLOCKED / NOT TESTED on that build.** Real Uber recognition and actual output must be confirmed on the exact installed SHA, including privacy, performance, negative cases and regression checks. |
| [#34](https://github.com/AdodgerWho/driverade-2/pull/34) | Developer inspection, stacked on #32 | Historical installed opt-in diagnostic baseline, with private captures described in checkpoint. | **OPEN, experimental.** Captures on this APK are not proof that #37's new parser works. Verify opt-in expiry, private export, and noninterference separately. |
| [#27](https://github.com/AdodgerWho/driverade-2/pull/27) → [#28](https://github.com/AdodgerWho/driverade-2/pull/28) → [#29](https://github.com/AdodgerWho/driverade-2/pull/29) → [#30](https://github.com/AdodgerWho/driverade-2/pull/30) → [#31](https://github.com/AdodgerWho/driverade-2/pull/31) → [#32](https://github.com/AdodgerWho/driverade-2/pull/32) → [#34](https://github.com/AdodgerWho/driverade-2/pull/34) → [#37](https://github.com/AdodgerWho/driverade-2/pull/37) | **Stacked experimental chain**, #27 base `main` | Some scoped synthetic/diagnostic evidence reported in project notes and PR descriptions. A passing test on a child does not validate unrelated parent functionality. | **Do not bulk-merge.** Inventory exact file/dependency overlap and feature-specific evidence; selectively promote only verified code and necessary dependencies. |
| [#25](https://github.com/AdodgerWho/driverade-2/pull/25) | Earlier integration candidate based on PR20 conflict reconciliation, base `main` | Historical starting point for #27 experiment. | **OPEN, not a current verified release.** Compare needed code to newer candidates before deciding any integration. |

Other open PRs (including #14, #18, #21 and #23) remain **unreviewed in this ledger**, not implicitly validated or safe to close.

## Live behavior evidence boundaries

The 2026-11-08 project checkpoint identifies PR #34 as the *prior installed* diagnostic baseline, not PR #37. It reports opt-in PNG/hierarchy capture activity, but no confirmed live Uber offer through PR #37's restored parser. Preserve this difference until new device evidence explicitly changes it.

| Required observation | Current ledger status |
| --- | --- |
| Exact installed APK source SHA, versionCode, signing fingerprint | **NOT TESTED / not established in this ledger** |
| Synthetic offer fixture cases on named PR #37 build | **Evidence requires exact linked run/results; do not infer live success** |
| Real Uber window/root or OCR input with capture ID | **NOT TESTED on PR #37 in available checkpoint** |
| Correct source-backed payout and pickup/trip fields | **NOT TESTED live on PR #37** |
| Gate/rule decision and actual alert to device | **NOT TESTED live on PR #37** |
| Screenshot capture, transient OCR, optional saved PNG, readable ZIP | **Separate checks required, not one combined PASS** |
| Lyft/Hich non-offer rejection and offer handling regressions | **NOT TESTED on integrated candidate** |
| Privacy/opt-in defaults, bounded diagnostics, signing continuity | **Recheck on exact candidate and device** |

**Next action:** Confirm the actual installed APK identity; collect the next real, independently observed offer and correlate acquisition → parser → gate → output by capture ID; mark each handoff PASS / FAIL / BLOCKED / NOT TESTED in the relevant PR without private content in public comments. Diagnose the first failing boundary.

## Promotion and change management

1. For each feature, record exact SHA/build, evidence source, environment, expected versus observed result, and limitations using `.github/pull_request_template.md`.
2. Check upstream and downstream equivalence before reusing evidence across stacked branches. Avoid redundant testing if code and relevant dependencies are demonstrably identical.
3. Cherry-pick or merge only complete verified portions into a named integration candidate; run regression on the combined build.
4. Follow `AGENTS.md` requirements for owner-approved stable merge, archived *tested* APK, signing continuity, and no premature PR closure/branch deletion.
5. Update this ledger only when new evidence or a significant status change warrants it; do not manufacture test outcomes.

**Privacy:** Keep raw ride information, rider identities/addresses, screenshots and sensitive cryptographic details outside this public repository.
