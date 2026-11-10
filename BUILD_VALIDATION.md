# Driverade change validation and merge policy

## Branches
- `main` is the integration baseline for verified work.
- Implement features and fixes on separate branches; use draft pull requests while incomplete.
- Do not assume the latest pushed commit has compiled or is safe to install.
- Preserve history in commits and pull requests. Delete obsolete branches only after confirming no outstanding work depends on them.

## Verification gates
1. **Source review:** Identify affected modules, interfaces, privacy implications, and possible regressions.
2. **Build:** GitHub Actions `gradle assembleDebug` succeeds for the exact head commit. Link the run and artifact.
3. **Automated tests:** Run applicable unit/instrumentation/static checks and document results or gaps.
4. **Device validation:** Test Android permissions, startup, exporter, and relevant app interactions on the installed commit.
5. **Live-behavior validation:** For Uber/Lyft parsers and online-state sessions, require representative positive and negative diagnostic evidence. A build pass alone is insufficient.
6. **Merge:** Merge only after relevant checks pass and remaining limitations are recorded. The assistant is authorized to merge verified PRs; unverified work remains unmerged.

## Change record template
- Date, branch, PR and commit SHA
- Problem / requested behavior
- Files and behavior changed
- Build / test / device evidence
- Known limitations and follow-up
- Merge commit and cleanup outcome

## 2026-11-06 — Diagnostic history and export scopes
- Branch: `feature/platform-specific-offer-parsers-20261106`
- PR: #23 (draft)
- Changes: Preserve diagnostic text across accessibility service reconnects and diagnostic snapshots; introduce explicit verified-platform online/offline session-state API; expose current-session and all-history text export options.
- Status: **Unverified; do not merge.** No successful CI run or device validation confirmed at documentation time.
- Known gaps: No verified app online/offline signal is yet connected to the session API; current-session export can be empty. Screenshot export is not integrated with these text export options. Existing diagnostic logs may predate session markers.
- Next: Confirm compilation and on-device behavior, wire trustworthy platform-state detection, extend screenshot export, then update this record with evidence before merge.
