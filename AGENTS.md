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
- Each PR has a feature checklist with required tests. Completing a test on a downstream branch checks off the originating PR's corresponding item when the code and relevant dependencies are equivalent. Do not repeat equivalent testing without a concrete reason.
- **When all checklist items are verified, presume the originating PR safe to merge into its intended integration branch.** Do not require an extra discretionary review or indefinite waiting without a specific failure, code change, merge conflict, or dependency risk. If some changes remain unverified, separate them instead of blocking already verified functionality.
- Merge fully checked-off PRs in dependency order into their intended integration branch, or consolidate by selective cherry-picking when needed. Recheck only what integration actually changes. Ensure verified fixes and logging are not accidentally dropped.
- Require automated unit tests and APK build, relevant on-device verification, diagnostic observability, and privacy/security review. For core parsing changes include positive and negative fixtures for Uber/Lyft/Hich where relevant.
- **Synthetic test success is not live validation.** Live overlays, OCR, screenshots, alerts, and background behavior require their own evidence.
- Maintain clear diagnostic labels: experimental, observation-only, partial, and verified. Do not promote an OCR candidate to a live offer without independent confirmation.

## Integration and housekeeping

1. Inventory PR head/base SHAs, dependent branches, duplicate changes, and test evidence.
2. Categorize each PR as verified, partially verified, experimental, superseded, or blocked.
3. Merge or cherry-pick only proven behavior into a clean integration candidate.
4. Test the entire combined candidate, including regressions, alerts, screenshot export, diagnostics, and privacy controls.
5. Promote a fully verified integration candidate to `main` after a clean combined build and the project's release approval; don't restart feature-by-feature verification merely because code moved branches.
6. Close superseded PRs **only after** verifying their needed changes exist elsewhere, referencing the replacement PR or commit. Delete stale branches only after verifying there is no unique work or diagnostics.
7. Keep a short PR ledger so the next developer knows what is verified, what is waiting, and why.

## Versioned Google Drive archive

- Use the existing Google Drive **Update builds** folder as the canonical archive for each tested iteration: https://drive.google.com/drive/folders/1gsKOPoqRIqt5wqnT3G8RSI245wroq0-c . Keep an individual, clearly named folder per version/build/commit rather than overwriting an older APK.
- For each build worth retaining, copy the signed or debug APK artifact **when available**, plus `BUILD_INFO.md` (version, build/run ID, SHA, source branch, signing/update compatibility and timestamp), `CHANGELOG.md` (features/fixes and originating PRs), `TEST_CHECKLIST.md` (tests, outcomes, verification provenance, known failures and limitations), and pertinent **sanitized, explicitly approved** diagnostics or regression fixtures.
- Mark each iteration `EXPERIMENTAL`, `CANDIDATE`, or `VERIFIED` in its documentation; never infer verification from successful compilation alone. When a feature is checked off in a later build, reference the prior originating PR and the later verified build in both records.
- GitHub remains the canonical source-code and change-history repository; Drive holds reproducible build artifacts and supporting documents, not uncontrolled copies of the repository.
- Keep the existing Drive **Driverade** folder as a curated home for reference screenshots and historical diagnostics: https://drive.google.com/drive/folders/1nOveqTivf5JBPPHoM9oTSqqSkAvcdgjh . Inventory and categorize apparent clutter before relocating it. Do not mass-delete or empty that folder without an explicit reviewed retention decision.
- Do not automatically copy live screenshots, rider or location information, raw accessibility captures, or other personal data to Drive. Keep diagnostics local unless specifically selected for sharing, and redact or minimize identifying data before archival.
- Archive after an actual tested iteration or release candidate; do not create empty placeholder build folders or claim files have been copied before the upload and readback succeed.

## Safety guardrails

- A green build alone does not complete a PR checklist, but a fully checked-off PR is presumed merge-ready. Only concrete failures, changed code/dependencies, or conflicts justify withholding integration. Preserve approval requirements for stable-branch releases and destructive operations; do not force-push or discard unique work.
- Never weaken offer-screen gating, data anonymization, opt-in settings, or local-only diagnostics merely to make tests pass.
- A failed diagnostic or missing screenshot is evidence to investigate, not a reason to claim success or silently suppress failures.
- Prioritize device-agnostic parsing: source acquisition may vary by device, while normalized offer parsing uses semantic identifiers and relationships.
