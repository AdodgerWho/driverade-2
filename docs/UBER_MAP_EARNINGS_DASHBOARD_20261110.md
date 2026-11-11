# Uber map earnings into dashboard — 2026-11-10

Status: IMPLEMENTING / NOT DEVICE-VERIFIED. Branch based on installed diagnostic build commit `f9589e82b508f9442128b8a27fc493d5eab4528a`. This is a narrow additive change; offer parser, alerts and existing deduplication are unchanged.

User evidence: Uber idle-map accumulated earnings read $42.45 after four completed rides; Driverade displayed $0.00 while counting three offers. These are different metrics; never infer completed earnings from offered payout amounts or compare offer count with ride count as proof.

Implementation: passive Uber map-total observation from app-owned accessibility trees and from Uber-triggered temporary OCR. Only recognize a single money header with the visible 'Finding trips' cue; reject screens containing offer/action text, ambiguous dollar values and non-map context. Store amount as integer cents with an approximate 4 AM local earnings-day boundary; repeated readings are idempotent. Decreases within a day are diagnostic/review events, not an automatic negative ride. Use a separate, local-only ledger and display its current-day value in dashboard, preserving the manual fallback and existing tip ledger. Do not persist screenshots, raw OCR, locations or rider information.

Testing: pure parsing tests for idle map, offers, historical screens, ambiguous values and zero. Device test needed to verify whether the exact Uber map header and 'Finding trips' are actually exposed together in the accessibility tree or OCR capture; test map reading with both source paths, UI refresh, app restart, daily reset, and decreasing totals. Confirm original offer-to-alert chain remains unchanged. No APK signing or merge approved by this note.
