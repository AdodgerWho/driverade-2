# Driverade combined function verification — PR #20 baseline

**Status:** TEST PLAN ONLY — no checks marked passed without evidence.  
**Baseline:** `fix/uber-offer-state-and-dedup` at `bd1d4b123ac4ebb21adaba99b68ab120af3305ae`.  
**Scope:** cumulative ancestry includes PRs #12, #13, #15, #16, #17 and #19, plus intervening changes through #20. This is **not** a claim that every feature works.  
**Do not merge** this test branch until the checks below pass and the CI/signing workflow is reconciled with current `main`.

## Verification key
- [ ] = not yet verified
- Record device/Android version, APK run URL, log bundle filename, expected and observed results, and PASS/FAIL/BLOCKED for each case.
- Prefer privacy-safe diagnostic reason codes; never upload unredacted screenshots or ride/customer information automatically.

## Build and installation
- [ ] CI debug APK compiles and uploads successfully.
- [ ] Verify package ID, version code, signing certificate fingerprint, and compatibility with installed APK **before installing**. Do not uninstall without exporting local data.
- [ ] App launches, onboarding and required accessibility/overlay permissions can be enabled.
- [ ] App resumes after force-stop/restart; no crash or permission loop.
- [ ] Compare branch CI workflow with current `main`: retain immutable action SHA pins, preserve intended signing and update path, and resolve differences before merge.

## Offer recognition and alerting
- [ ] Real actionable Uber offer: payout, duration, distance and accept affordance are recognized; alert dispatches exactly once when enabled.
- [ ] Uber map pill / pickup navigation: no false offer alert.
- [ ] Uber wallet, earnings history, balance and trip completion: no false offer alert.
- [ ] Lyft actionable offer: accepted fields parse correctly and alert dispatches once.
- [ ] Incomplete/ambiguous offers: reject conservatively with a diagnostic reason, no fabricated duration or hourly rate.
- [ ] Rapid repeated accessibility events: dedupe suppresses repeat alert; new distinct offer can alert.
- [ ] Alerter disabled: no audible/vibration alert, explicit disabled reason in logs.
- [ ] Foreground/background changes: do not mix unrelated app root text into offer candidates.
- [ ] Verify Android Auto related state is not guessed from window visibility.

## Diagnostics and screenshot export
- [ ] Live listener records accessibility event, candidate and rejection reason without raw sensitive text.
- [ ] Screenshot capture opt-in off: no screenshot persisted.
- [ ] Screenshot capture opt-in on: actual PNG is saved; inspect file signature and readable image.
- [ ] Atomic capture/write/readback and retention behavior verified.
- [ ] Export text-only log still works independently.
- [ ] ZIP export with confirmation contains actual PNG(s), diagnostic log and privacy README; verify archive can be opened.
- [ ] Empty capture/export case explains why no PNG exists; does not claim capture success.
- [ ] Capture funnel counters and feature diagnostics agree with actual files/events.
- [ ] Diagnostic logs survive expected service transitions without leaking customer data.

## Other integrated functions
- [ ] Tip observations from clearly labeled Uber/Lyft earnings/activity: local provisional totals, dedupe, no false tips.
- [ ] Android Auto guided setup: instructions/highlights work; no automatic taps or unauthorized actions.
- [ ] Local ephemeral demand HMAC/replay checks: valid records accepted; modified/replayed records rejected; no unsupported claim of remote authenticity.
- [ ] App UI settings and dashboard controls map to working functions.

## Security and regression
- [ ] Repeat Semgrep scan on this integration branch; record rules, findings and any justified exceptions.
- [ ] Review exported Android components and broadcast permissions.
- [ ] Confirm debug export and screenshot data stay on-device unless explicitly shared.
- [ ] Compare changes with current `main` and PR #22 security fixes before considering merge.

## Evidence / result sheet

| ID | Test area | Result (PASS/FAIL/BLOCKED) | Evidence (run/log/device) | Notes |
|---|---|---|---|---|
| B01 | Build and APK | NOT RUN | | |
| B02 | Signing/update compatibility | NOT RUN | | |
| O01 | Uber real offer | NOT RUN | | |
| O02 | Uber pill false-positive | NOT RUN | | |
| O03 | Lyft real offer | NOT RUN | | |
| O04 | Duplicate dispatch | NOT RUN | | |
| D01 | PNG capture | NOT RUN | | |
| D02 | ZIP archive export | NOT RUN | | |
| D03 | Diagnostic continuity | NOT RUN | | |
| T01 | Tip ledger | NOT RUN | | |
| A01 | Android Auto guide | NOT RUN | | |
| S01 | Semgrep | NOT RUN | | |
| S02 | Privacy and exported components | NOT RUN | | |

**Gate:** Compile + basic diagnostics first; then on-device capture/export; then live Uber/Lyft acceptance and rejection; finally Semgrep, signing, and merge review.
