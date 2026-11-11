# Experimental legacy rolling capture restoration — 2026-11-10

Status: IMPLEMENTED ON ISOLATED BRANCH; CI AND DEVICE VERIFICATION PENDING.

## Evidence and motivation
The owner reported one successful $28.80 Uber offer capture on the signed PR #40 build, followed by another missed offer. The export showed screenshot capture failures, unrecognized offer contexts and the new diagnostic failure observations. A single duplicate was suppressed after a successful alert, not evidence of an erroneous 90-second deduplication block.

The legacy source `AdodgerWho/Driverlogger3_4/app/src/main/java/com/example/driverlogger/LoggerService.java` used an accessibility-driven, visually monitored rolling capture loop and full **display** screenshot for Uber OCR, cropping the display to the suspected offer bounds. The PR #40 `RollingOcrFrameBuffer` still exists (8 seconds before, 10 seconds after, bounded ten frames / 8 MB), but is primarily an **opt-in diagnostic buffer**; it does not automatically operate as the authoritative live-offer parser.

## Scoped change
In `VisualDebugCapture.capture`, prefer a **full-display screenshot whenever Uber owns the active accessibility root**; retain existing Uber-window screenshot acquisition when another application owns the active screen (where a display screenshot would capture the wrong app). Existing 1.5-second capture throttle, rolling diagnostic buffer, screenshot opt-in, transient OCR, parser authorization, debouncing, alerts and earnings remain unchanged. This is a narrow restoration of the earlier full-display OCR input, **not a complete port of the old 400-ms rolling capture scheduler or area-cropping OCR**.

## Required verification
- Build unit tests and debug APK for this exact SHA. Confirm signing and version code separately before any on-device installation.
- Field test Uber foreground exclusive/match card, Uber background accessible window, small floating-window contamination, overlay capture, screenshot failure code and accepted trip/navigation rejection.
- Confirm no regression in previously successful captures, no duplicate alerts or history false positives, and no unauthorized screenshot persistence.
- Compare log fields `VISUAL_DEBUG_CAPTURE_SUCCESS source=DISPLAY`, `RESTORED_OCR_PARSE`, `PARSE_RESULT`, `ALERT_DISPATCH`, `OVERLAY_SHOWN`, and failure-tree events.
- If direct DISPLAY capture is also unreliable, next candidate is a separate, guarded crop of the Uber-owned display region and/or adaptation of legacy rolling scheduler, rather than automatically dropping parser guards.
