# Driverade development and PR integration rules

This policy applies to coding assistants and human changes in this repository.

## Stable and experimental branches

- `main` is the last fully verified integration baseline. Do not add experimental or partially tested behavior directly.
- Keep unfinished or exploratory features on isolated `experiment/*` branches; keep these separate from the stable baseline.
- Consolidate independently verified features on a named integration candidate branch. Run the full regression suite on the *combined build* before promoting to `main`.
- Preserve a known working commit and a documented rollback route. Avoid force pushes on shared branches.

## Verification and promotion

- **Downstream tests can validate an upstream feature** if the relevant code is unchanged, its dependencies are present, and the test actually exercises that feature. A successful downstream build alone is insufficient.
- Record the feature name, tested commit/build SHA, test conditions, outcomes, applicable platforms, observed diagnostics, and limitations on the feature's PR.
- Once confirmed, an upstream PR becomes **eligible for integration**, not automatically safe for wholesale merging. If it includes unverified changes, selectively cherry-pick or separate out the verified work.
- Merge eligible work in dependency order, or consolidate by selective cherry-picking into a clean integration candidate. Ensure verified fixes and logging are not accidentally dropped.
- Require automated unit tests and APK build, relevant on-device verification, diagnostic observability, and privacy/security review. For core parsing changes include positive and negative fixtures for Uber/Lyft/Hich where relevant.
- **Synthetic test success is not live validation.** Live overlays, OCR, screenshots, alerts, and background behavior require their own evidence.
- Maintain clear diagnostic labels: experimental, observation-only, partial, and verified. Do not promote an OCR candidate to a live offer without independent confirmation.

## Integration and housekeeping

1. Inventory PR head/base SHAs, dependent branches, duplicate changes, and test evidence.
2. Categorize each PR as verified, partially verified, experimental, superseded, or blocked.
3. Merge or cherry-pick only proven behavior into a clean integration candidate.
4. Test the entire combined candidate, including regressions, alerts, screenshot export, diagnostics, and privacy controls.
5. Promote to `main` only after a clean build and explicit approval of that integration candidate.
6. Close superseded PRs **only after** verifying their needed changes exist elsewhere, referencing the replacement PR or commit. Delete stale branches only after verifying there is no unique work or diagnostics.
7. Keep a short PR ledger so the next developer knows what is verified, what is waiting, and why.

## Safety guardrails

- Do not merge, close, delete, force-push, or release automatically just because a test turned green. Require explicit approval for changes to stable branches and destructive cleanup.
- Never weaken offer-screen gating, data anonymization, opt-in settings, or local-only diagnostics merely to make tests pass.
- A failed diagnostic or missing screenshot is evidence to investigate, not a reason to claim success or silently suppress failures.
- Prioritize device-agnostic parsing: source acquisition may vary by device, while normalized offer parsing uses semantic identifiers and relationships.
