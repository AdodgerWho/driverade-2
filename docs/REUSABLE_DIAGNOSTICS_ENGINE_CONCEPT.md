# Reusable Diagnostics Engine — extraction concept

**Status:** ADDENDUM TO EXISTING EXTRACTION BLUEPRINT — not a newly discovered project or an independently implemented library. **Owner intent reaffirmed 2026-11-08:** Optimize **development diagnostics for clarity** while protecting diagnostic access; production clients must separately enforce privacy-minded defaults.

## Existing source of truth and continuity

- **Original reusable diagnostic-engine blueprint:** [PR #21 — `blueprints/diagnostics/README.md`](https://github.com/AdodgerWho/driverade-2/blob/blueprints/reusable-engine-catalog/blueprints/diagnostics/README.md). It inventories `InternalDiagnosticsLogger.kt`, `DiagnosticSqliteStore.kt`, `FeatureDiagnostics.kt`, and `BugReportManager.kt`; gives proposed sink/store/probe/export interfaces; describes coupling, known flaws, and extraction tests.
- **Original catalog:** [reusable engine blueprints](https://github.com/AdodgerWho/driverade-2/tree/blueprints/reusable-engine-catalog/blueprints), preserved on `blueprints/reusable-engine-catalog`, with source snapshot and extraction rules. The blueprint explicitly says standalone extraction and end-to-end verification are **pending**.
- **Earlier archive:** [Code Vault diagnostics notes](https://github.com/AdodgerWho/driverade-2/blob/archive/code-vault/diagnostics/README.md) already articulate **broad development diagnostics** and **opt-in production reporting**.
- **Relationship of this document:** A 2026-11-08 clarification and potential next-phase extension of that **existing** work, emphasizing signal fidelity, causal tracing and a standalone inspection UI. Do not start a second engine from scratch, treat PR #36 as an extraction implementation, or overwrite prior blueprints. When extracting later, reconcile against the current Driverade source, not a historical snapshot alone.


## Objective

Build a standalone, reusable, transparent instrumentation toolkit that answers:

1. **What happened?** Capture what a system actually emitted, observed, transformed and delivered.
2. **Where did it happen?** Identify process/component, app/OS state, source window/event, and build identity.
3. **Why was it handled this way?** Capture explicit parser, filter, validation, gate, decision and rejection reasons.
4. **What did not happen?** Surface missing events, downstream non-delivery, skipped capture, blocked permissions and dropped diagnostics as first-class outcomes.
5. **Which observations can distinguish competing hypotheses?** Preserve broad captures for discovery and gradually narrow without losing the ability to reopen instrumentation.

## Separation of responsibilities

- **Core event schema:** versioned structured records with event type, source, session ID, correlation/trace ID, causal parent ID, capture time (wall clock + monotonic time), component, build SHA, state, raw/derived provenance, processing outcome and optional linked artifact IDs. Records can state `not observed` instead of fabricating data.
- **Capture adapters:** separate Android accessibility/window/overlay, screenshot and OCR, app lifecycle, logs, exceptions, parser and gate adapters. Later adapters can serve other runtimes and projects. An adapter only reports what it actually sees and can report lack of access.
- **Pipeline:** capture → correlate → buffer → inspect → filter/query → export. Development capture should happen **before** aggressive interpretation or minimization; each stage of the client system should emit input/output/result links where available.
- **Fidelity controls:** session-scoped FULL / TARGETED / SUMMARY sampling levels. FULL supports broad discovery; TARGETED preserves chosen sources and pivots; SUMMARY is the lightweight normal mode. Capture limits and transparent dropped-event counters protect stability, not suppress inconvenient evidence.
- **Local inspector:** chronological and causal trace viewer, stage-by-stage comparisons, source-window tree and parsed field views, screenshot/OCR alignment, event diffing and a search/filter facility. Mark factual observations distinctly from analyst hypotheses.
- **Exporter:** portable structured JSONL plus binary artifact bundle with checksums and a manifest containing build/source/session metadata, schema version, time range, recording settings, sampling gaps, and evidence status. Explicitly separate capture, artifact persistence, successful bundle creation and user sharing.
- **Client release adapters:** development instrumentation library can be integrated into testing builds; production privacy policy, collection controls and retention belong to each consuming app. No shared cloud service is necessary for a first version.

## Security without blinding development

The development toolkit may collect raw intermediate details needed to diagnose a defect, including sensitive screen content when knowingly enabled by the owner. It should **not** silently collect credentials, private signing keys, authentication tokens or unrelated device content. Keep sessions visible, bounded, device-private, explicitly controlled, and deletion/export available. Do not auto-upload diagnostics. Strong access protection and authorized export are security requirements; blanket loss of useful signal is not.

Production defaults belong to the consuming app: privacy-minimized summaries, no raw background logging, conservative retention, opt-in support capture, and explicit build-variant separation to prevent accidental shipping of developer instrumentation.

## First deliverable / non-goals

**Suggested first milestone:** extract the event envelope, trace IDs, stage diagnostics, bounded ring buffer, drop counters and JSONL export into a tiny dependency-light Kotlin module, with fixture tests and a local viewer prototype. Prove it can trace a synthetic input through acquisition → parser → decision → actual output, including intentional missing/dropped events. Build it independent of Driverade; use Driverade only as a validation consumer later, after separately approved integration.

**Not yet authorized or built:** a new repository, SDK, native Android app, library published for reuse, production release, cross-project integration, cloud collection, automatic raw export, or any changes to Driverade's runtime instrumentation. No guarantee that Android accessibility or screenshot privileges will be available to a standalone process; such capture must occur through lawful platform-supported adapters in the instrumented app or explicit user-granted facilities.

## Decisions to evaluate later

- Minimal Kotlin library first versus independent Android inspector UI.
- Ring buffer sizing/retention and backpressure behavior under high event rates.
- Schema portability beyond Android and whether visual artifacts are referenced or embedded.
- How to keep debug build identity and integrity independently verifiable without embedding private signing keys.
- Whether a separate specialized repository is warranted after the first API/test design is proven.

**Next step:** Compare Driverade's existing acquisition/diagnostic stage names and export logic to this proposed schema, then define a minimal interface and test fixtures. Keep separate project boundaries; do not automatically copy cryptographic-engine or personal-assistant requirements.
