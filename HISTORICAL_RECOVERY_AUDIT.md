# Historical Driverlogger3_4 recovery audit — 2026-11-07

**Source of truth:** `AdodgerWho/Driverlogger3_4`, especially `app/src/main/java/com/example/driverlogger/{LoggerService,Offers,Rules,Store,Flasher,MainActivity}.java`. Historical `DriverLog_20261101_1346_text.zip` includes **4,000** accessibility captures, not screenshot files. The source findings below are directly from those files; integration priorities are engineering recommendations.

## Cross-checked against original historical captures

- 3,921 captures show an Uber window; total Uber window occurrences 3,924. Layer occurrences: L0=3,694; L1=202; L2=15; L-1=13.
- The archive contains 181 Lyft window occurrences under `com.lyft.android.driver` and 45 Hich occurrences under `us.mi.hich.driver`. These differed from two exact package names in the modern `PACKAGE_PLATFORM_MAP`, which is also used to gate screenshot capture; both historical aliases have been restored on this experimental branch.
- At least 18 snapshots contain Uber's `upfront_offer_configurable_details_auditable_accept_button` and associated offer-specific view IDs. There are hundreds of `RecyclerView` observations. The known historic values are valid regression evidence, not guaranteed current UI identifiers.

## Proven older mechanisms and their modern status

| Old code path | Behavior proven by source | Modern status / action |
|---|---|---|
| `onAccessibilityEvent`, `scheduleCapture`, `tick` | Event-driven capture, `TYPE_WINDOWS_CHANGED`, plus 1,200ms periodic inspection with 400ms debounce | Current service listens mostly to state/content events; implement a bounded, conditional independent inspection path (preferably diagnostic-first), so a missed overlay event does not make it blind |
| `dumpWindows`, `dumpNode` | Multi-window ownership, layer, roots, resource ID, class, bounds, visibility, and children; detects large textless Uber RecyclerView overlay | PR #34 adds opt-in raw hierarchy diagnostics; determine root-null versus textless-root and bind screenshots to a window/capture ID |
| `ocrUber`, `cropShot`, `readCrop` | Uses visible Uber window bounds, crops screenshot and invokes ML Kit OCR when overlay has no text | PR #32 currently runs **diagnostic-only full-display OCR**, not the old bounded window-based offer parser. Restore the crop and apply semantic offer validation before promoting OCR to live calculations |
| `shoot`, `saveShot`, `trigger`, `promote` | 8s pre-roll, 10s post-roll, visual difference signature, duplicate suppression, expiring pending shots | Current PNG capture has a bounded file count but not the historic pre/post-roll. Port buffer with opt-in, minimal retention and separation from ordinary diagnostics |
| `scanOffers`, `Offers.fromTree` | Separate parsers for Lyft, Hich, and Uber reservations; segment by package/window | Legacy read-only adapters already covered by PR #28–29; retain modern source provenance and conservative history/false-positive gates |
| `Offers.fromUberOcr` | OCR payout + two legs, `UNREAD` if incomplete; distinct source label | Do **not** copy first-dollar selection or raw OCR storage; gate by verified Uber offer region and distinguish Match versus Exclusive explicitly |
| `Rules.Scheduled.merge` and `Rules.decide` | Hich ride fields assembled across successive screens and missing fields held briefly as `WAIT`; quiet hours supported | Reuse partial-field merge and WAIT state as read-only parser concepts after fixture tests; auto-accept actions require separate design/opt-in |
| `Flasher.flash` | Short visible overlay plus optional vibration/audio; `FLAG_NOT_TOUCHABLE` leaves Accept/Match tappable | Current banner is clickable and lacks `FLAG_NOT_TOUCHABLE`; change only with visual/device tests, then confirm no offer-tap obstruction |
| `Store.addOffer`, `recentOffers`, `prune` | Bounded local offer history, stable observed values, and retention | Current state prominently exposes only last offer and totals; local bounded history may be worthwhile, but strip raw text and never auto-upload |
| `Store.DEFAULT_APPS` | Exact older Lyft/Hich package names | Restored aliases for capture; modern parser already has broad package fallback |
| Notification handling | Captures notification immediately and again after 1.5s for dynamic layout settle | Consider brief deferred diagnostic retry on a relevant Uber notification/window transition, without notification actions or auto-taps |

## Important differences and cautions

- **The source does not implement a literal three-way voting parser.** It uses event monitoring, periodic inspection and image/OCR acquisition, with platform-specific parsing and deduplication.
- Legacy Hich automated clicks, notification opens and scheduled auto-accept were present; several legacy preferences defaulted **on**. Do not import actions or defaults implicitly into the modern privacy-first app.
- Legacy JSONL offers stored raw OCR strings. The modern optional `LoggerService.recordOfferDiagnostic` also had a raw-node logging path; on PR #34 this is changed to omit raw nodes from routine logs, reserving raw inspection for explicit Developer Mode.
- A `nodes=0` modern diagnostic refers to the number of collected **text strings** rather than actual tree nodes. A textless node hierarchy is not equivalent to an absent window.
- Modern `OfferScreenClassifier` expects accept/decline/reject and is therefore not sufficient to identify every actionable Uber **Match** presentation when OCR does become available. Match support needs a separate positive/negative gate.
- Recovered legacy code provides useful mechanisms, **not** a claim that the current target OS/app renders identically.

## Priority / verification sequence

1. Verify CI and keep main stable. PR #33/#35 policy+CI candidate is separate from unverified runtime work.
2. Retain current real $5.53 Exclusive and $6.74 Match screenshots as positive fixtures, with map pill and earnings screens as negatives. Expected rate calculations are 33.18/hr and 31.11/hr, respectively.
3. Diagnose event windows and textless roots with existing #34 instrumentation. Restore `TYPE_WINDOWS_CHANGED` and a bounded timed probe only after scope/overhead review.
4. Restore cropped OCR for real Uber overlays with spatial and semantic checks; retain source provenance and do not let untrusted OCR override a verified accessibility payout.
5. Test nonintercepting alert overlay, source-specific capture, deduplication, privacy and update signing compatibility on device before including the runtime work in a main integration.

**Release gate:** Historical architecture is useful, but previous 9/9 synthetic batch checks do not prove live Uber matching, and the last real Exclusive and Match offers were visually captured but not parsed. Do not mark that behavior verified until an on-device test passes.
