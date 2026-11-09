# Feature Brainstorming — Checkpoint and Refresher Protocol

**Purpose:** Make planning sessions easy to resume without reconstructing context or accidentally treating ideas as approved implementations.

**Scope:** Apply this convention to feature-planning and architecture brainstorming for any project, including Driverade, Personal-Assistant, and cryptographic-engine. Store each project's notes **in its own project context/repository**, not in unrelated repos. This is a discussion protocol, **not** a background agent, scheduled reminder, authorization to build, or automatic deployment trigger.

## When it triggers

- **At the beginning of a resumed planning conversation:** When the owner mentions returning to brainstorming, planning, or feature design for a recognizable project, look up the latest saved checkpoint for **that project/topic** and offer a brief refresher before proposing changes.
- **At the end of a planning session:** Capture meaningful conclusions, unresolved questions, fresh ideas, rejected options, and the precise next starting point in a checkpoint document.
- **Mid-session:** Update the checkpoint when a consequential decision or major new research finding changes the plan.
- **Not triggered by incidental mentions:** Ordinary troubleshooting, code review, or a brief project reference need not launch the entire refresher. If the user requests implementation or testing, preserve the latest instructions and do not substitute brainstorming for the requested work.

A conversational trigger works **when this protocol and its checkpoint are available to the assistant**; it is not an OS event listener or guaranteed autonomous notification. If the relevant repo/context is unavailable, say so rather than inventing a previous decision.

## Required roadmap refresh before every coding decision (2026-11-09)

**Standing owner instruction:** Before writing, modifying, reviewing, merging or generating code for a named project, **identify that project and read its current authoritative roadmap/architecture, relevant feature contract, most recent decision/checkpoint notes and actual branch/test state**. Apply these requirements to the code being written. If a current owner-approved architectural correction changes the design, update the relevant documents before (or as part of) implementing that change. Do not assume a recalled older plan supersedes a newer owner decision.

## Required architecture writeback when discussion focus changes (2026-11-09)

**Standing owner instruction:** An architecture or project-planning discussion is not complete merely because the conversation moves on. When a material design decision is reached, a prior decision is corrected, a safeguard is changed, or the discussion shifts to another substantial topic or project, **checkpoint the change in the appropriate project repository during the active conversation, before moving on**.

1. Read the latest project checkpoint and related design notes. Compare new decisions with existing records; distinguish **DECIDED**, **PROPOSED**, **RESEARCH**, **IMPLEMENTED**, **CI-TESTED**, and **DEVICE-VERIFIED**.
2. When there is a meaningful delta, save a dated entry to the affected project's roadmap/decision log, capturing **what changed, why, alternatives rejected, risks, unresolved questions, and the next step**. Never silently overwrite history or promote a proposed idea to proven functionality.
3. When changing projects, save **separate** checkpoints to their own repositories. Driverade, Personal Assistant, cryptographic-engine, and the proposed project-continuity service do not inherit each other's code, keys, telemetry, permissions or decisions implicitly.
4. Confirm a successful write with actual commit/PR/file links. If access/history is unavailable, disclose that limitation and give a ready-to-apply note; **never claim an unsaved decision was saved**.
5. For each next code change, refresh from authoritative documents and actual branch/CI/device status; explain any owner-approved architectural deviation rather than quietly altering the project.

**No-change exception:** Routine chatter and repeated unchanged facts do not need commits. This is an **interactive workflow**, not an always-running background monitor or automatic access to every conversation.

## Beginning-of-session refresher

Keep the initial refresher concise (normally 4–8 sentences or a compact checklist), in this order:

1. **Topic + last stopping point:** What were we designing, and where did we pause?
2. **Established decisions:** What did the owner actually approve or settle?
3. **Promising ideas:** Which candidate designs remain under consideration, clearly **not yet decided**?
4. **Unresolved risks/assumptions:** What would need research, measurement, or user choice?
5. **Suggested next question:** Resume directly from the saved next step rather than restarting broad discovery.

Read the saved checkpoint and any linked design documents first. Distinguish source-confirmed/current state from conjecture and ideas. Verify time-sensitive technical details when they become relevant. Don't claim a capability has been implemented because it appears in a design note.

## Session checkpoint structure

```markdown
# <Project> — <Feature> brainstorm checkpoint
Updated: <date>
Status: IDEA / EXPLORING / PROPOSED / DECIDED / IMPLEMENTING / VERIFIED

## Intent and constraints
What problem are we trying to solve, and which security, privacy,
cost, performance or product constraints apply?

## Established decisions
Only settled choices, with rationale where useful.

## Candidate designs and reference ideas
Possibilities, analogies, novel thoughts, and source references,
each labeled unverified/research-needed/validated/rejected as appropriate.

## Known facts and evidence
Observed tests, relevant file/PR references, external research with sources;
explicitly distinguish hypotheses from evidence.

## Open questions and risks
Unknowns, tradeoffs, failure modes and security assumptions.

## Tests / verification proposed
How we would know whether the intended behavior actually works.

## What was NOT authorized or built
No implicit permission to alter code, keys, security controls,
branches, production deployment, or third-party services.

## Next-session starting point
The exact unresolved choice, investigation, or design diagram to begin with.
```

## Reliability and safety rules

- Keep **decisions, speculative ideas, and implemented/tested facts separate**. Never silently promote an idea into an approved feature.
- Don't overwrite earlier decisions without noting the change and reason. Capture alternatives even when not adopted so they can be revisited.
- Avoid storing private keys, credentials, unredacted personal data, or unsafe diagnostic extracts in notes.
- Prefer links to relevant code, PRs, specs, and tests over copying volatile facts. Record dates and review current standards before acting.
- A brainstorm checkpoint must not trigger a build, merge, root operation, deploy, purchase, or automation on its own.
- When a conversation crosses projects, maintain project-specific checkpoints and do not transfer requirements without explicit intention.
- If there were no material new decisions or ideas, do not create noisy documentation changes.

## Example spoken trigger

"Let's pick up the cryptographic engine architecture brainstorm."

**Expected response:** Retrieve its latest checkpoint, recap security and stability goals, sealed private profile, pending decisions about exact authorized pathways and specialized crypto research, then resume at those questions. Other projects should use their own checkpoint.
