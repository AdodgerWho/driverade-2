# Driverade Code Vault

An archival branch for known-good code snapshots, engineering decisions, recovery instructions, and build references. This branch is **not** the active development branch.

## Rules
- Archive source from an identified commit, with its SHA, date, and test status.
- Never label a snapshot **known good** until its build and device behavior have been verified.
- Keep raw diagnostic logs, personal data, credentials, signing keys, and tokens out of the vault.
- Do not merge archival documentation into `main` unless explicitly requested.
- Prefer immutable Git tags and release assets for verified APKs; do not treat a branch as an off-site backup.

## Sections
- `engine/` — offer detection, parsing, evaluation, filters
- `alerter/` — notifications, overlays, and test behavior
- `privacy/` — data minimization, ephemeral authentication, location transformations
- `diagnostics/` — development logging and opt-in production reporting
- `ui/` — activities, fragments, layouts, and navigation
- `builds/` — commit-to-APK validation and restoration records
- `snapshots/` — manifest of source snapshots and verification status

See `snapshots/INDEX.md` for the initial baseline. Source code is already retained in this branch's Git history from `main`; avoid duplicating unverified files as independent copies.
