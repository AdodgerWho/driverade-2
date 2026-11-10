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
        checks += check("Offer gate rejects earnings history") {
            (!OfferScreenClassifier.isActiveOffer(listOf("Your earnings", "$101.83", "Trip", "Accept", "20 min", "4 mi"))) to
                "Historical balance must not trigger an offer"
        }
        checks += check("Offer gate accepts actionable sample") {
            OfferScreenClassifier.isActiveOffer(listOf("Trip request", "Accept", "$18.50", "25 min", "5.2 mi")) to
                "Synthetic offer classification only; not a live platform test"
        }
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
        checks += Check("Screenshot capture file verification",
            if (validScreenshots > 0) "PASS" else if (visualEnabled) "FAIL" else "BLOCKED",
            "optIn=$visualEnabled savedPng=${screenshotFiles.size} verifiedPng=$validScreenshots; " +
                "PASS proves readable image files exist, not that the current screen was captured")
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
