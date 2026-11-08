## Change class and scope

- Mode: [ ] DISCOVER/experiment [ ] ENGINEER/behavior [ ] VERIFY/process [ ] Documentation only
- User-visible problem / desired result:
- Source baseline (branch + SHA / APK identity if applicable):
- Narrow scope; affected upstream/downstream components:
- What this PR **does not** implement or prove:

## Hypothesis and predicted result

- Proposed mechanism and relevant alternatives:
- Smallest observation that distinguishes them:
- Expected positive case:
- Expected negative/false-positive case:
- Unchanged behavior/regression expectations:

## Evidence (use PASS / FAIL / BLOCKED / NOT TESTED / N/A)

| Layer / required behavior | Status | SHA / build, conditions, observed result, link, limitation |
| --- | --- | --- |
| CI compilation and unit checks | NOT TESTED | |
| Synthetic fixtures / pipeline | NOT TESTED | |
| Real Android acquisition and parse (if relevant) | NOT TESTED | |
| Rules/gates and actual alert/output (if relevant) | NOT TESTED | |
| Screenshot / OCR / PNG / ZIP path (if relevant) | NOT TESTED | |
| Regressions (Uber/Lyft/Hich as applicable) | NOT TESTED | |
| Opt-in/privacy/security behavior | NOT TESTED | |

**Important:** Synthetic PASS ≠ real offer PASS. CI green ≠ safe to merge. Mark unrelated tests N/A with justification; never silently turn NOT TESTED into PASS.

## Integration and recovery

- Known-good rollback SHA/APK, signing and data-continuity concerns:
- Stack dependencies / cherry-pickable verified portion:
- Remaining unknowns / field conditions needed:
- [ ] Tested integrated candidate, with linked run and build identity
- [ ] Owner approval and verified APK Drive snapshot **before** any merge to `main`
- [ ] No raw rider details, private diagnostics, tokens or secrets in this public PR

See [engineering evidence standard](../docs/ENGINEERING_EVIDENCE_STANDARD.md) and `AGENTS.md`. For documentation-only changes, mark runtime tests N/A and explain why.
