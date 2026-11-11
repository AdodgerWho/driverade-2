# Driverade — consolidated engineering instructions

Before every substantive code change, review [project checkpoint](docs/NEXT_SESSION_CHECKPOINT.md), [brainstorming and roadmap writeback protocol](docs/BRAINSTORMING_PROTOCOL.md), and relevant feature notes. Verify installed APK commit/version/signature and current CI status. Older checkpoint statements reflect their dated evidence, not the current phone build.

## Verified integration principles

- Keep `main` as the last verified/reviewed baseline; experiment and integration branches are not release approval.
- Keep privacy-first offline operation. Developer-only raw diagnostics must be explicitly activated, kept local, time-bounded and never automatically published or uploaded.
- Preserve live acquisition → source-backed parser → configured gates → nonblocking alerts and the existing replay/dedup protections.
- Preserve observed evidence separately from inference: a CI pass is **not** live offer recognition; an offer is **not** a completed ride; an accumulated earnings increase is **not** proof of a new ride or tip.
- Use the [engineering evidence standard](docs/ENGINEERING_EVIDENCE_STANDARD.md) and [PR verification ledger](docs/PR_VERIFICATION_LEDGER.md). Keep feature-specific PASS/FAIL/BLOCKED/NOT TESTED, named SHA/build, test conditions and sanitized supporting artifacts.
- Before an architecture/code decision, reconcile authoritative roadmap, feature contract and recent decision/checkpoint notes. Write dated design deltas to project records; no silent assumption that a proposal is implemented.
- Consolidate validated features on a named integration candidate, and run combined regression/synthetic/device checks before any `main` promotion.
- Before any merge to `main`, verify tested signed APK identity, exact CI SHA, update-compatible versionCode, and preserve the tested APK in Drive Update builds: https://drive.google.com/drive/folders/1gsKOPoqRIqt5wqnT3G8RSI245wroq0-c. Do not create/test signing key in PR CI or recover signing keys from Actions cache. Archive only the selected reviewed artifact.
- Do not close old PRs or delete branches until exact changes and references have been proven accounted for. No force pushing shared branches; no APK installation based only on CI success.

The original integration policy is available in PR #36; consolidated branch status and exclusions are recorded in [integration manifest](docs/EXPERIMENTAL_CONSOLIDATION_20261110.md).
