package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.driverade.data.DriverPreferences
import com.driverade.receiver.AndroidAutoReceiver
import java.io.File

/**
 * Non-destructive feature verification. PASS means a concrete assertion passed;
 * BLOCKED means device permissions or real-world evidence are missing.
 * NOT_IMPLEMENTED must never be reported as PASS.
 */
object FeatureDiagnostics {
    data class Check(val feature: String, val status: String, val evidence: String)
    private fun check(name: String, block: () -> Pair<Boolean, String>): Check =
        try {
            val (passed, evidence) = block()
            Check(name, if (passed) "PASS" else "FAIL", evidence)
        } catch (e: Exception) {
            Check(name, "FAIL", "Exception: ${e.javaClass.simpleName}")
        }

    fun run(context: Context): List<Check> {
        val prefs = DriverPreferences(context)
        val checks = mutableListOf<Check>()
        checks += check("Accessibility registration") {
            val component = ComponentName(context, OptimizedOfferEngine::class.java)
            val service = context.packageManager.getServiceInfo(component, 0)
            (service.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE") to
                "Manifest permission verified: ${service.permission}"
        }
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expected = ComponentName(context, OptimizedOfferEngine::class.java).flattenToString()
        checks += Check("Accessibility enabled", if (enabled.split(':').any { it.equals(expected, true) }) "PASS" else "BLOCKED",
            "Requires the driver to enable Driverade accessibility")
        checks += Check("Overlay permission", if (Settings.canDrawOverlays(context)) "PASS" else "BLOCKED",
            "Android overlay permission state")
        checks += check("Platform mapping") {
            (OptimizedOfferEngine.PACKAGE_PLATFORM_MAP["me.lyft.driver"] == "LYFT" &&
                OptimizedOfferEngine.PACKAGE_PLATFORM_MAP["com.ubercab.driver"] == "UBER") to
                "Known Uber/Lyft package mappings"
        }
        checks += check("Restored live gate rejects earnings history") {
            (RestoredOfferParser.fromTree("UBER", listOf("Your earnings", "\$101.83",
                "Trip", "Accept", "10 min • 2 mi", "20 min • 4 mi")).offer == null) to
                "Historical balance must not trigger an active offer"
        }
        checks += check("Restored live gate accepts complete actionable sample") {
            (RestoredOfferParser.fromTree("UBER", listOf("Trip request", "Accept",
                "\$18.50", "5 min • 1.0 mi", "20 min • 4.2 mi")).offer != null) to
                "Two-leg synthetic offer classification only; not a live platform test"
        }
        checks += check("Lyft shadow comparison fixture") {
            val nodes = listOf("Ride request", "Accept", "$18.50",
                "$37/hr est rate for this ride", "5 min • 1.0 mi", "25 min • 8.0 mi")
            val report = OfferParserComparison.compare("LYFT", nodes, true,
                OfferParserComparison.ActiveSnapshot(18.50, 30.0, 9.0, 37.0))
            (report.status == "MATCH" && report.reportedEstimatedRate == 37.0) to
                "Both comparators agree on complete Lyft fixture; no live input tested"
        }
        checks += check("Uber reservation candidate stays partial") {
            val nodes = listOf("Trip request", "Accept • $24.50",
                "12 min • 3.2 mi", "18 min • 6.4 mi")
            val report = OfferParserComparison.compare("UBER", nodes, true,
                OfferParserComparison.ActiveSnapshot(24.50, 30.0, 9.6, 49.0))
            (report.status == "PARTIAL" && report.fields.first().state == "MATCH") to
                "Reservation candidate has payout but no verified pickup total"
        }
        checks += check("Shadow parser cannot override offer gate") {
            val nodes = listOf("Your earnings", "Trip", "Accept", "$101.83", "20 min", "4 mi")
            val report = OfferParserComparison.compare("UBER", nodes,
                OfferScreenClassifier.isActiveOffer(nodes), null)
            (report.status == "GATE_REJECT") to
                "Historical earnings remain rejected even when values are present"
        }
        checks += check("Restored Uber Exclusive OCR fixture") {
            val result = RestoredOfferParser.fromUberOcr(
                "UberX Exclusive\n\$5.53\n3 min • 0.7 mi\n7 mins • 2.1 mi\nAccept")
            val offer = result.offer
            (offer != null && offer.kind == "live" &&
                kotlin.math.abs(offer.payout - 5.53) < 0.01 &&
                kotlin.math.abs(offer.hourlyRate - 33.18) < 0.01) to
                "Historical visual $5.53 Exclusive expected 10 min; synthetic OCR text only"
        }
        checks += check("Restored Uber Match OCR fixture") {
            val result = RestoredOfferParser.fromUberOcr(
                "UberX\n\$6.74\n6 min • 2.4 mi\n7 mins • 2.3 mi\nMatch")
            val offer = result.offer
            (offer != null && offer.kind == "match" &&
                kotlin.math.abs(offer.payout - 6.74) < 0.01 &&
                kotlin.math.abs(offer.totalMiles - 4.7) < 0.01) to
                "Historical visual $6.74 Match expected 13 min; synthetic OCR text only"
        }
        checks += check("Restored Uber ambiguous payout rejection") {
            val result = RestoredOfferParser.fromTree("UBER", listOf(
                "Trip request", "Accept", "\$12.04", "\$13.08",
                "6 min • 2 mi", "7 min • 3 mi"))
            (result.offer == null) to "No arbitrary largest-dollar selection"
        }
        checks += Check("Live restored OCR and timed accessibility inspection", "BLOCKED",
            "Requires actual background Uber offer; verify RESTORED_TREE_PARSE, " +
                "RESTORED_OCR_PARSE and PARSE_RESULT; synthetic tests do not prove capture")
        checks += Check("Restored three-path diagnostic correlation", "BLOCKED",
            "New-build device evidence required: RESTORED_DIAGNOSTIC_READY, " +
                "RESTORED_ACQUISITION (EVENT/TIMED), RESTORED_TREE_PARSE or MISS, " +
                "VISUAL_DEBUG_OCR_REQUESTED/COMPLETED, RESTORED_OCR_PARSE or SKIPPED; " +
                "match capture IDs. Synthetic parser success cannot verify live capture")
        checks += Check("Synthetic result delivery to dashboard", "BLOCKED",
            "Run the injector; require a comparison dialog and PARSER_COMPARISON export record")
        checks += check("Tip ledger read") {
            val count = TipLedger.countToday(context)
            val total = TipLedger.totalToday(context)
            (count >= 0 && total.isFinite() && total >= 0.0) to
                "Local aggregate readable; not proof of accurate real-world tip recognition"
        }
        checks += check("Demand grid rejects invalid location") {
            (!DriveradeState.recordValidatedTelemetry(100.0, 200.0, "UBER", 30.0)) to
                "Invalid GPS rejected without writing an observation"
        }
        checks += check("Demand grid read") {
            val grid = DriveradeState.getRollingGridDensity()
            grid.values.all { it >= 0 } to "Rolling in-memory grid readable; ${grid.size} cells"
        }
        checks += check("Diagnostics file writable") {
            val file = File(context.filesDir, "driverade_diagnostics.txt")
            InternalDiagnosticsLogger.logInteraction(context, "DIAGNOSTIC_PROBE", "Local write probe")
            (file.exists() && file.canRead() && file.length() > 0L) to
                "App-private diagnostic log exists and is readable"
        }
        val visualEnabled = context.getSharedPreferences("driverade_visual_debug", 0)
            .getBoolean("enabled", false)
        val screenshotFiles = File(context.filesDir, "visual_debug").listFiles()
            ?.filter { it.isFile && it.extension.equals("png", true) }.orEmpty()
        val validScreenshots = screenshotFiles.count { file ->
            runCatching {
                file.inputStream().use { stream ->
                    val bytes = ByteArray(8)
                    stream.read(bytes) == 8 && bytes.contentEquals(
                        byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
                }
            }.getOrDefault(false)
        }
        checks += Check("PNG persistence vs transient OCR", "BLOCKED",
            "pngOptIn=$visualEnabled retainedPng=${screenshotFiles.size}; " +
                "retained files may predate the current toggle. With PNG opt-in OFF, " +
                "Uber OCR still operates transiently; expect VISUAL_DEBUG_PNG_SKIPPED " +
                "and VISUAL_DEBUG_OCR_COMPLETED, and no new VISUAL_DEBUG_FILE_VERIFIED")
        checks += Check("Screenshot capture file verification",
            if (validScreenshots > 0) "PASS" else "BLOCKED",
            "optIn=$visualEnabled savedPng=${screenshotFiles.size} verifiedPng=$validScreenshots; " +
                "PASS proves readable selected image files exist; opt-in rolling mode may save none " +
                "until an offer/window/visual-change trigger, not proof of current-screen capture")
        checks += check("Rolling OCR pre/post frame selector") {
            val ring = RollingOcrFrameBuffer()
            fun frame(id: String, t: Long, shade: Int) =
                RollingOcrFrameBuffer.Frame(id, t, "WINDOW", ByteArray(24) { shade.toByte() },
                    IntArray(12) { shade })
            ring.add(frame("expired", 1_000L, 5))
            ring.add(frame("relevant", 4_000L, 25))
            val before = ring.promote(10_000L).map { it.captureId }
            val after = ring.add(frame("followup", 11_000L, 50)).toPersist.map { it.captureId }
            val passed = before == listOf("relevant") && after == listOf("followup")
            ring.clear()
            (passed && ring.pendingCount == 0) to
                "8s pre-roll, 10s post-roll and memory clearing; pure logic, not live image capture"
        }
        val rollingOptIn = context.getSharedPreferences("driverade_visual_debug", 0)
            .getBoolean("enabled", false)
        checks += Check("Rolling OCR screenshot sequence on device", "BLOCKED",
            "Opt-in=$rollingOptIn; verify ROLLING_STAGED, ROLLING_TRIGGER and " +
                "ROLLING_FRAME_SAVED with matching capture IDs and readable exported PNGs")
        checks += Check("Screenshot retention capacity",
            if (screenshotFiles.size <= VisualDebugCapture.MAX_FILES) "PASS" else "FAIL",
            "limit=${VisualDebugCapture.MAX_FILES} saved=${screenshotFiles.size} verified=$validScreenshots; " +
                "rolling retention can discard older screenshots; export promptly after an Uber offer")
        checks += Check("Screenshot ZIP export verification", "BLOCKED",
            "Run Export ZIP and require VISUAL_DEBUG_EXPORT_VERIFIED plus screenshot entries in the downloaded archive")
        checks += check("Android Auto receiver registration") {
            val receiver = context.packageManager.getReceiverInfo(
                ComponentName(context, AndroidAutoReceiver::class.java), 0)
            (receiver != null) to "Broadcast receiver declared; does not prove car display"
        }
        checks += Check("Android Auto car session", "NOT_IMPLEMENTED",
            "No CarAppService/Session yet; connection and rendering cannot be verified")
        checks += Check("Navigation and rerouting", "NOT_IMPLEMENTED",
            "Routing engine and turn guidance not installed")
        checks += Check("Live traffic and construction", "NOT_IMPLEMENTED",
            "No active provider or road-event validation")
        checks += Check("30-day encrypted hotspot history", "NOT_IMPLEMENTED",
            "Only a 5-minute in-memory demand grid exists")
        checks += check("Failure-tree database readable") {
            // A readable summary is structural evidence, not a complete behavior test.
            val summary = FailureTreeDiagnostics.summary(context)
            summary.isNotBlank() to summary.take(500)
        }
        checks += Check("Failure-tree anomaly detection in live operation", "BLOCKED",
            "Requires opted-in diagnostic logger and actual observed repeated failures; " +
                "EVENT_SILENCE_CANDIDATE is only a contextual observation, not a proven outage")
        checks += Check("Real offer and alert delivery", "BLOCKED",
            "Requires a live offer and visible overlay/car-output observation; synthetic tests are insufficient")
        checks.forEach { InternalDiagnosticsLogger.logInteraction(context, "FEATURE_CHECK",
            "${it.feature}: ${it.status} - ${it.evidence}") }
        return checks
    }

    fun format(checks: List<Check>): String = buildString {
        append("Feature verification (PASS is not end-to-end certification)\n\n")
        checks.forEach { append("${it.status} | ${it.feature}\n${it.evidence}\n\n") }
        append("No private screenshots, raw offer nodes, or exact coordinates are included.")
    }
}
