# Driverade engineering evidence standard

**Status:** Proposed through PR #36; not yet merged into `main`. **Scope:** Driverade only. Complements `AGENTS.md`; does not modify Android behavior, signing, permissions, or merge approval.

## Purpose

Preserve unconstrained exploration while ensuring changes to the working application are small, observable, reproducible, and reversible. **Ideas are not requirements; code is not verification; passing simulated tests is not proof of live behavior.** The goal is working functionality before architectural polish.

## Three modes (do not silently cross boundaries)

1. **DISCOVER:** Enumerate plausible mechanisms, rejected options, uncertainties, and experiments. No automatic production edits. A hypothesis may be useful without being true.
2. **ENGINEER:** Select a narrow, falsifiable hypothesis and define a *change contract* before implementation. Prefer the smallest change that distinguishes or corrects the mechanism. Keep exploratory branches separate and retain a known-good baseline.
3. **VERIFY:** Test observed behavior against the predeclared contract. Record exactly what passed, failed, was blocked, or was never tested. Promote only substantiated behavior into the verified integration candidate; owner approval and pre-merge snapshot rules in `AGENTS.md` still apply.

A task may return to DISCOVER after a failed test; that is not a failure of the process.

## Minimal change/evidence contract

For every behavior-changing PR, state in its description or linked verification record:

- **Problem / user-visible result:** What is failing or missing? What matters to a driver?
- **Baseline:** Source branch and SHA; known-good APK/build identity (versionCode and signing certificate fingerprint when relevant); current observed behavior.
- **Hypothesis and alternatives:** Proposed mechanism, 1–3 plausible alternatives, and a *discriminating observation* that would change the decision.
- **Smallest change / dependency scope:** Files and connected functions affected; expected upstream and downstream handoffs; unrelated functionality that must stay unchanged.
- **Predicted results:** At least one positive and one negative/failure test when meaningful. Define expected output and rejection behavior before running tests.
- **Evidence plan:** Unit/fixture, synthetic pipeline, instrumented output, actual Android device/live offer, regression, privacy/security as applicable.
- **Rollback:** Last known-good SHA/APK and exact return route. Preserve app data, signing identity, and diagnostics when switching installed builds.
- **Observation limits:** Missing data, sample size, device/OS/app version, disabled instrumentation, and alternative explanations.

For documentation-only or trivially scoped PRs, mark inapplicable fields **N/A with a brief reason**; avoid ceremonial paperwork.

## Evidence states: no ambiguous checkmarks

- `PASS`: Recorded observation matches the expected outcome on the stated SHA/build, under specified conditions. Link sanitized logs, tests or CI run.
- `FAIL`: A test was performed and observed behavior contradicted an expectation; include earliest known failing boundary.
- `BLOCKED`: Cannot run/interpret a required test because a dependency or environment is unavailable. Record blocker and unblock condition.
- `NOT TESTED`: No execution evidence exists. Do **not** infer success from code review, a successful build, an older APK, or other tests.
- `N/A`: Not relevant to the scoped feature; explain why. Do not use N/A to hide missing evidence.

Every row should name the source SHA/build, test method, date, expected outcome, observed outcome, evidence reference, and limitations. Preserve `PASS` evidence across branches **only** when the feature code, dependencies and test conditions are equivalent. New integration effects require focused rechecking.

## Explicit test layers

| Layer | What passing means | What it does not prove |
| --- | --- | --- |
| Compile and CI | Code compiles and configured automated checks pass | Actual offer acquisition, alerts, signing continuity or safety on the owner's phone |
| Unit/fixture | Algorithm handles predefined inputs | Uber/Lyft/Hich will expose those inputs in real windows |
| Synthetic pipeline | Simulated events traverse the exercised connected path | The OS delivers real events or the real UI looks identical |
| Instrumented device | Observed acquisition/parser/gate/dispatch behavior for a known APK | Reliability across other devices, app versions, or all offers |
| Field sample | A defined set of real captured conditions behaved as recorded | Universal accuracy without a representative denominator |
| Privacy/security review | Reviewed specified controls and tested selected paths | Comprehensive cryptographic assurance or immunity to unknown threats |

For offer accuracy, log separate **true positive**, **false positive**, and **missed offer** counts *only when independent ground truth exists*; otherwise use `unknown`. A successful parser test has no information about offers that never entered acquisition. Report sample size and conditions; do not invent a success rate. Track output confirmation (not just dispatch intent).

## Debug from the first uncertain boundary

Use capture/request IDs to follow: **external source → event/window acquisition → parser candidate → active-offer gate → configured rule → dispatcher → actual user-facing alert/export**. For a missed event, start at the earliest unproven boundary rather than rewriting the last visible component. Ask before adding every diagnostic: **Which hypotheses will this measurement distinguish? What decision depends on it?**

For screenshots, distinguish `capture succeeded`, `local OCR ran`, `PNG persisted`, `ZIP export contains readable image`, and `file was actually shared`; one does not imply another. Inspect false positives from pill totals, wallet/history figures, unrelated dollar labels, and duplicate events. Preserve diagnosis of background/foreground and app-owned windows.

Diagnostics stay **opt-in, local/private, bounded, minimal and reversible** as required. Do not store raw rider information, precise routes, unredacted accessibility data, private keys or secret parameters in this public repository or PR comments.

## Promotion and stopping rules

- Keep experimental, observation-only, and partial functionality labeled until its corresponding evidence contract passes. CI green alone never authorizes claiming live behavior.
- When a hypothesis is falsified or a discriminating test fails, stop broad feature edits and correct the earliest failing boundary. If repeated tests cannot distinguish remaining mechanisms, explicitly record `BLOCKED` and identify the smallest useful next observation.
- Promote the **smallest verified functional change** and its necessary dependencies into the integration candidate; validate the **combined build** for regressions and privacy. Do not drag in unverified siblings from a stacked PR.
- A fully checked-off feature is presumptively integration-ready absent a concrete integration, regression, dependency, or privacy risk; no arbitrary indefinite re-review. Preserve the owner-approved `main` merge and Google Drive verified APK snapshot gates from `AGENTS.md`.
- Retain superseded PRs until inclusion and no unique work are confirmed. Never automatically delete branches, close PRs or change signing.
- Record decisions concisely in the PR or a small architecture decision record: decision, alternatives, rationale, reversal conditions, evidence link.

## Working example: restored Uber offer parser

**Question:** Did an actual offer reach the parser and downstream alert output?

1. Identify the installed source SHA/signing identity and the exact ride event; do not confuse PR #34 evidence with PR #37 evidence.
2. Confirm synthetic fixture fields and negative examples, then separately test real Uber accessibility window acquisition and local OCR.
3. Correlate acquisition, parsed fields with source provenance, gate decision, and **visible/sound/vibration alert confirmation** by capture ID.
4. Run opt-in screenshot save/export checks separately from transient OCR, including the persistence-off case.
5. Record PASS / FAIL / BLOCKED / NOT TESTED for each handoff, with sample size. No live offer → no live PASS.
6. Verify Lyft/Hich regressions as applicable before integration; keep `main` untouched until verified promotion.

See `docs/PR_VERIFICATION_LEDGER.md` for a dated inventory, not a substitute for test logs.
