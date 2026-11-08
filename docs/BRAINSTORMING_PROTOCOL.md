# Feature Brainstorming — Checkpoint and Refresher Protocol

**Purpose:** Make planning sessions easy to resume without reconstructing context or accidentally treating ideas as approved implementations.

**Scope:** Apply this convention to feature-planning and architecture brainstorming for any project, including Driverade, Personal-Assistant, and cryptographic-engine. Store each project's notes **in its own project context/repository**, not in unrelated repos. This is a discussion protocol, **not** a background agent, scheduled reminder, authorization to build, or automatic deployment trigger.

## When it triggers

- **At the beginning of a resumed planning conversation:** When the owner mentions returning to brainstorming, planning, or feature design for a recognizable project, look up the latest saved checkpoint for **that project/topic** and offer a brief refresher before proposing changes.
- **At the end of a planning session:** Capture meaningful conclusions, unresolved questions, fresh ideas, rejected options, and the precise next starting point in a checkpoint document.
- **Mid-session:** Update the checkpoint when a consequential decision or major new research finding changes the plan.
- **Not triggered by incidental mentions:** Ordinary troubleshooting, code review, or a brief project reference need not launch the entire refresher. If the user requests implementation or testing, preserve the latest instructions and do not substitute brainstorming for the requested work.

A conversational trigger works **when this protocol and its checkpoint are available to the assistant**; it is not an OS event listener or guaranteed autonomous notification. If the relevant repo/context is unavailable, say so rather than inventing a previous decision.

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
