# Offer recognition engine blueprint

**Status:** Integrated heuristic, not validated as a standalone or universally reliable offer recognizer.

## Existing source
- `app/src/main/java/com/driverade/service/OfferScreenClassifier.kt`
- `app/src/main/java/com/driverade/service/OptimizedOfferEngine.kt`
- `app/src/main/java/com/driverade/service/FeatureDiagnostics.kt`

## Proposed reusable API
`OfferCandidate(sourcePackage, visibleTexts, observedAt)`; `OfferDecision(accepted, reason, confidenceEvidence)`; `OfferClassifier.classify(candidate)`. Separate platform adapters from the core decision logic and payout parsing.

## Existing behavior
The classifier requires an action term, trip term, dollar amount, duration, and distance; known earnings/history phrases cause rejection. The accessibility service filters app packages and throttles duplicate alerts.

## Known gaps
A real offer can be rejected if the platform does not expose the Accept button or other required text through accessibility. Synthetic diagnostic checks are not live ride verification. Uber's changing interface may cause false positives or negatives. Do not equate a candidate with a confirmed offer.

## Acceptance tests
Real anonymized positive and negative fixtures; earnings-pill false positive; inaccessible action button; incomplete accessibility tree; platform change; dedup window; regression suite for Uber/Lyft. Do not retain rider identity or exact address in test fixtures.
