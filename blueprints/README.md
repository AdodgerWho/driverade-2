# Driverade reusable engine blueprints

This directory is a **versioned blueprint catalog**, not a set of independently buildable libraries yet. It records source entry points, integration contracts, limitations, and extraction work so future Codex sessions can reuse the architecture without copying unverified assumptions.

Source snapshot: `fix/uber-offer-state-and-dedup` at `bd1d4b123ac4ebb21adaba99b68ab120af3305ae` (initial inventory). Source may evolve independently. No Driverade runtime files were changed by this catalog.

## Catalog
- [Diagnostic engine](diagnostics/README.md): event collection, SQLite storage, verification, exports.
- [Offer recognition engine](offer-recognition/README.md): conservative active-offer classifier and accessibility integration.
- [Visual capture engine](visual-capture/README.md): opt-in screenshot capture and PNG verification.
- [Ephemeral proof engine](ephemeral-proof/README.md): in-memory HMAC proof for demand points, **not** a full encryption system.

## Extraction rules
1. Preserve the original source and behavior in Driverade; extract into separate Gradle Android library modules only on a dedicated branch.
2. Move application-specific dependencies behind interfaces; do not import Driverade UI, preferences, or package mapping into generic libraries.
3. Add tests for normal operation, failure recovery, privacy redaction, retention, and platform API compatibility before labeling a package reusable.
4. Keep personal data, screenshots, credentials, and keys out of this catalog and Git history.
5. Before any full cryptographic-engine implementation, **verify this GitHub repository has been made private**. A private repository does not retroactively erase previously public history or forks.
6. Package release must have a version, README, sample integration, license decision, and independently passing build.

## Codex handoff
Ask Codex to open this catalog first, inspect the current source files referenced in each blueprint, and work on **one extraction at a time**. Never assume the blueprints are production-ready modules. Confirm the current branch, build state, and user approval before changing Driverade runtime code.
