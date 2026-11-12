# Driverade — reservation offer and overlay handoff experiment

**Date:** 2026-11-11  
**Status:** IMPLEMENTED ON EXPERIMENTAL BRANCH; UNIT/CI PENDING; DEVICE NOT TESTED; PERMANENT-SIGNED APK NOT YET BUILT

## Owner-approved scope
- Investigate missing Lyft alerts before the common alerter; preserve successful Uber earnings and live capture paths.
- Add scheduled/reservation offer support for Uber and Lyft. Never treat existing reservations or history as new opportunities.
- Fix confirmed overlay lifecycle bug: an older offer's delayed timeout could dismiss a newer overlay.
- Preserve existing 90-second duplicate suppression for now. Fingerprint collisions and 90-second tuning remain deferred until independently observed evidence supports changes.

## Implemented
- RestoredOfferParser recognizes reservation context plus a standalone Reserve/Book button as an actionable scheduled offer. Existing Accept, Match, Request and Decline recognition remains. Scheduled offers carry kind=`reservation`.
- Existing non-offer gates, unique payout validation, two pickup/trip leg pairs, and amount provenance remain unchanged. An incomplete scheduled booking screen is **not** accepted. Some real offers without two legs may still be missed; collect fresh source-backed examples before loosening gates or inventing hourly estimates.
- Shared overlay path adds capture-correlated OVERLAY_REQUEST, OVERLAY_ATTACHED/DETACHED, OVERLAY_WINDOW_STATE and OVERLAY_DISMISSED events. OVERLAY_SHOWN remains strictly the addView-returned stage, not proof the user saw the banner.
- OverlayRequestGate prevents stale queued requests and old timers from dismissing a newer offer, including during service interruption/teardown.

## Evidence and remaining work
- Existing field evidence: Uber daily total tracked accurately; several Uber/Lyft live offer alerts dispatched, but intermittent invisible overlays and scheduled-offer misses observed. This patch is not validated by that earlier field evidence.
- Tests added for Uber and Lyft scheduled-offer action gates, rejection of reservation listings/previews, missing trip leg, and stale overlay generation.
- Mandatory next checks: CI unit tests/compile on exact head SHA; then **permanent-signed update** from manual workflow_dispatch (sign_for_updates=true), verifying installed signing certificate and monotonically increasing versionCode before phone installation; synthetic and actual device tests; correlate each missed offer from acquisition through rendered attachment.
- CI debug-signed artifacts are **not installable updates** over the existing permanent-signed package; do not distribute or install as an in-place update.
- Do not merge to main or delete experimental branches without evidence review and owner approval.

## Reversal conditions
If scheduled-offer support generates false positives from reservation lists or accepted bookings, disable/revert that parser expansion without altering live acquisition. If overlay lifecycle tests reveal unintended removal behavior, revert the guard independently. Preserve diagnostic source IDs and no raw rider details in public logs.
