# Uber OCR context recovery experiment — 2026-11-24

Status: IMPLEMENTED IN ISOLATED EXPERIMENTAL BRANCH; CI AND DEVICE VERIFICATION PENDING.

## Field evidence and problem
User reports intermittently missed Uber offer overlays for visually similar offer screens, while other offers reach overlays. The November 24 bundle confirms reliable screenshot API acquisition and at least one successful parsed-to-rendered offer, but many OCR parses reject with `ocr_no_service_context`, `missing_uber_action` and missing two-leg pairs. The user's Uber Earnings Activity screenshot shows completed rides at 11:30 AM and 11:48 AM with no matching successful parsed offer in the November 24 logging window; the screenshot itself is historical earnings data, NOT an offer fixture. This does not establish the precise contents of the failed OCR screenshot.

The PR #41 restoration prefers full DISPLAY capture when accessibility verifies Uber owns the foreground. The previous parser still demanded an exact known Uber service label for every DISPLAY result, even from verified Uber foreground. That mismatch can reject otherwise complete offer readings.

## Scoped engineering decision
- Mark display screenshots `DISPLAY_UBER_FOREGROUND` **only** when the service's foreground accessibility root belongs to `com.ubercab.driver`. Generic DISPLAY and WINDOW_FALLBACK remain untrusted mixed-app sources.
- Add bounded, anchored OCR service-line variants for simple visual punctuation/spacing changes.
- If an exact service label is absent on a **verified** Uber foreground display, allow parsing only with a distinct offer cue (Exclusive/trip request/ride request); still require action, uniquely identifiable payout, two complete duration/distance legs, within-range values, and rejection of earnings/navigation/history.
- On OCR rejection, emit *field-presence counts and provenance only*, never OCR screen content, personal addresses or unredacted text.
- Preserve 200 ms event-to-acquisition delay, independent platform gates, existing screenshot throttle, privacy defaults, deduplication, and overlay behavior.

## Test and deployment criteria
Run `testDebugUnitTest` and APK build for the new SHA. Verify missing-label acceptance occurs *only* with verified app-foreground provenance plus cue and complete offer data; negative fixtures for mixed-app display, missing cue, missing action/leg, earnings activity and ambiguous payouts must remain rejected.
This does NOT prove all missed rides were caused by label recognition. Field logs after a permanently signed test build must compare `RESTORED_OCR_PARSE`, fieldEvidence, `PARSE_RESULT`, `ALERT_DISPATCH`, and `OVERLAY_SHOWN` with manually observed real offers.
No automatic upload of diagnostic text. Do not merge to main or discard the prior PR #41 candidate until on-device verification.
