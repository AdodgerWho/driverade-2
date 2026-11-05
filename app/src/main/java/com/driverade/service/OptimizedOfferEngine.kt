package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.driverade.data.DriverPreferences
import com.driverade.receiver.AndroidAutoReceiver
import java.util.Locale

class OptimizedOfferEngine : AccessibilityService() {

    private lateinit var prefs: DriverPreferences
    private var windowManager: WindowManager? = null
    private var activeOverlayView: LinearLayout? = null
    private var guidance: GuideHighlighter? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastOfferSignature: String = ""
    private var lastOfferTimestamp: Long = 0L

    companion object {
        private const val TAG = "OptimizedOfferEngine"
        private const val OVERLAY_DURATION_MS = 9000L

        val PACKAGE_PLATFORM_MAP = mapOf(
            "com.ubercab.driver" to "UBER",
            "com.ubercab.eats" to "UBER_EATS",
            "me.lyft.driver" to "LYFT",
            "com.hich.driver" to "HICH",
            "com.doordash.driverapp" to "DOORDASH",
            "com.grubhub.driver" to "GRUBHUB"
        )

        private val PAYOUT_REGEX = Regex("""\$\s*(\d{1,4}(?:\.\d{1,2})?)""")
        private val MINUTES_REGEX = Regex("""(\d{1,3})\s*(?:min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)
        private val HOURS_MINUTES_REGEX = Regex("""(\d+)\s*hr\s*(\d+)\s*min""", RegexOption.IGNORE_CASE)
        private val MILES_REGEX = Regex("""(\d{1,4}(?:\.\d{1,2})?)\s*(?:mi|mile|miles)\b""", RegexOption.IGNORE_CASE)
        private val OFFER_KEYWORDS = listOf("match", "trip", "ride", "offer", "accept")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = DriverPreferences(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        guidance = GuideHighlighter(this)
        InternalDiagnosticsLogger.initializeSession(this, prefs)
        InternalDiagnosticsLogger.logInteraction(this, "SERVICE", "OptimizedOfferEngine connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: "unknown.package"
        if (GuidedSettingsState.isActive(this) && (pkgName == "com.android.settings" || pkgName == "com.google.android.projection.gearhead")) {
            guidance?.inspect(rootInActiveWindow, GuidedSettingsState.step(this))
            return
        }
        guidance?.clear()

        InternalDiagnosticsLogger.logInteraction(this, "EVENT_INGEST", "Received accessibility event from package: $pkgName (Type: ${event.eventType})")

        val platform = resolvePlatform(pkgName)
        if (platform == null) {
            InternalDiagnosticsLogger.logInteraction(this, "FILTER_DROP", "Package $pkgName not recognized as target platform.")
            return
        }

        if (!prefs.isPlatformEnabled(platform)) {
            InternalDiagnosticsLogger.logInteraction(this, "FILTER_DROP", "Platform $platform is toggled OFF in driver preferences.")
            return
        }

        InternalDiagnosticsLogger.logInteraction(this, "PLATFORM_MATCH", "Matched active platform: $platform")

        val collectedNodes = LinkedHashSet<String>()

        event.source?.let { src ->
            traverseNodeTree(src, collectedNodes, 0)
        }

        rootInActiveWindow?.let { root ->
            traverseNodeTree(root, collectedNodes, 0)
        }

        try {
            windows?.forEach { windowInfo ->
                windowInfo.root?.let { winRoot ->
                    val winPkg = winRoot.packageName?.toString() ?: pkgName
                    if (resolvePlatform(winPkg) != null) {
                        traverseNodeTree(winRoot, collectedNodes, 0)
                    }
                }
            }
        } catch (e: Exception) {
            InternalDiagnosticsLogger.logInteraction(this, "WARN", "Multi-window inspection warning: ${e.message}")
        }

        InternalDiagnosticsLogger.logInteraction(this, "NODE_SCRAPE", "Collected ${collectedNodes.size} unique nodes from window hierarchy.")

        if (collectedNodes.isEmpty()) {
            InternalDiagnosticsLogger.logInteraction(this, "PARSE_ABORT", "No text nodes found to evaluate.")
            return
        }

        val rawList = collectedNodes.toList()
        val joinedTextLower = rawList.joinToString(" ").lowercase(Locale.US)
        val hasValidKeyword = OFFER_KEYWORDS.any { joinedTextLower.contains(it) }

        if (!hasValidKeyword) {
            InternalDiagnosticsLogger.logInteraction(this, "KEYWORD_DROP", "Ignored: Missing mandatory offer validation keywords.")
            return
        }

        val evaluation = parseOfferNodes(platform, pkgName, rawList)
        
        if (evaluation == null) {
            InternalDiagnosticsLogger.logInteraction(this, "PARSE_FAIL", "Nodes scraped but regex rejected. Sample nodes: ${rawList.take(5)}")
            return
        }

        val signature = "${evaluation.platform}_${evaluation.payout}_${evaluation.totalMinutes}_${evaluation.totalMiles}"
        val now = System.currentTimeMillis()
        if (signature == lastOfferSignature && (now - lastOfferTimestamp) < 4000L) {
            InternalDiagnosticsLogger.logInteraction(this, "DEBOUNCE", "Duplicate offer suppressed within 4s window.")
            return
        }
        lastOfferSignature = signature
        lastOfferTimestamp = now

        InternalDiagnosticsLogger.logInteraction(this, "SUCCESS", "Offer evaluated: Payout=$${evaluation.payout}, Hourly=$${evaluation.hourlyRate}/hr, TargetMet=${evaluation.meetsTarget}")

        DriveradeState.recordOffer(evaluation)
        broadcastToAndroidAuto(evaluation)
        showHourlyRateOverlay(evaluation)
    }

    private fun resolvePlatform(packageName: String): String? {
        PACKAGE_PLATFORM_MAP[packageName]?.let { return it }
        val lower = packageName.lowercase(Locale.US)
        return when {
            lower.contains("ubercab") -> "UBER"
            lower.contains("lyft") -> "LYFT"
            lower.contains("hich") -> "HICH"
            lower.contains("doordash") -> "DOORDASH"
            lower.contains("grubhub") -> "GRUBHUB"
            else -> null
        }
    }

    private fun traverseNodeTree(
        node: AccessibilityNodeInfo?,
        results: MutableSet<String>,
        depth: Int
    ) {
        if (node == null || depth > 28) return

        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { results.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { results.add(it) }

        val count = node.childCount
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            if (child != null) {
                traverseNodeTree(child, results, depth + 1)
            }
        }
    }

    fun parseOfferNodes(
        platform: String,
        packageName: String,
        texts: List<String>
    ): OfferEvaluation? {
        var payout = -1.0
        var totalMinutes = 0.0
        var totalMiles = 0.0

        val joined = texts.joinToString(" | ")

        for (entry in texts) {
            if (entry.contains("/hr", ignoreCase = true) || entry.contains("per hour", ignoreCase = true)) {
                continue
            }
            PAYOUT_REGEX.findAll(entry).forEach { match ->
                val value = match.groupValues[1].toDoubleOrNull() ?: -1.0
                if (value in 2.0..500.0 && value > payout) {
                    payout = value
                }
            }
        }

        HOURS_MINUTES_REGEX.findAll(joined).forEach { match ->
            val hrs = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val mins = match.groupValues[2].toDoubleOrNull() ?: 0.0
            totalMinutes += (hrs * 60.0) + mins
        }

        if (totalMinutes == 0.0) {
            MINUTES_REGEX.findAll(joined).forEach { match ->
                val mins = match.groupValues[1].toDoubleOrNull() ?: 0.0
                if (mins in 1.0..300.0) {
                    totalMinutes += mins
                }
            }
        }

        MILES_REGEX.findAll(joined).forEach { match ->
            val mi = match.groupValues[1].toDoubleOrNull() ?: 0.0
            if (mi in 0.1..500.0) {
                totalMiles += mi
            }
        }

        if (payout <= 0.0) return null

        val effectiveMinutes = if (totalMinutes > 0.0) totalMinutes else 15.0
        val hourlyRate = (payout / effectiveMinutes) * 60.0
        val dollarsPerMile = if (totalMiles > 0.0) payout / totalMiles else 0.0

        val meetsTarget = hourlyRate >= prefs.targetHourlyRate && payout >= prefs.minPayout

        return OfferEvaluation(
            platform = platform,
            packageName = packageName,
            payout = payout,
            totalMiles = totalMiles,
            totalMinutes = effectiveMinutes,
            hourlyRate = hourlyRate,
            dollarsPerMile = dollarsPerMile,
            meetsTarget = meetsTarget,
            rawNodes = texts
        )
    }

    private fun showHourlyRateOverlay(evaluation: OfferEvaluation) {
        if (!Settings.canDrawOverlays(this)) {
            InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_FAIL", "SYSTEM_ALERT_WINDOW permission not granted.")
            return
        }

        mainHandler.post {
            dismissOverlay()

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 24, 36, 24)
                background = GradientDrawable().apply {
                    cornerRadius = 28f
                    setColor(if (evaluation.meetsTarget) Color.parseColor("#E6115E38") else Color.parseColor("#E67F1D1D"))
                    setStroke(4, if (evaluation.meetsTarget) Color.parseColor("#34D399") else Color.parseColor("#F87171"))
                }
            }

            val titleView = TextView(this).apply {
                text = String.format(
                    Locale.US,
                    "%s • $%.2f/hr",
                    evaluation.platform,
                    evaluation.hourlyRate
                )
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                paint.isFakeBoldText = true
            }

            val detailView = TextView(this).apply {
                text = String.format(
                    Locale.US,
                    "Payout: $%.2f  |  %.1f mi  |  %.0f min ($%.2f/mi)",
                    evaluation.payout,
                    evaluation.totalMiles,
                    evaluation.totalMinutes,
                    evaluation.dollarsPerMile
                )
                setTextColor(Color.parseColor("#E5E7EB"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }

            container.addView(titleView)
            container.addView(detailView)
            container.setOnClickListener { dismissOverlay() }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 140
            }

            try {
                windowManager?.addView(container, params)
                activeOverlayView = container
                InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_SHOWN", "Rendered banner for ${evaluation.platform}")
                mainHandler.postDelayed({ dismissOverlay() }, OVERLAY_DURATION_MS)
            } catch (e: Exception) {
                InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_ERROR", "Failed to render overlay: ${e.message}")
            }
        }
    }

    private fun dismissOverlay() {
        activeOverlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
            activeOverlayView = null
        }
    }

    private fun broadcastToAndroidAuto(evaluation: OfferEvaluation) {
        val intent = Intent(this, AndroidAutoReceiver::class.java).apply {
            action = "com.driverade.ACTION_OFFER_EVALUATED"
            putExtra("platform", evaluation.platform)
            putExtra("payout", evaluation.payout)
            putExtra("hourly_rate", evaluation.hourlyRate)
            putExtra("meets_target", evaluation.meetsTarget)
        }
        sendBroadcast(intent)
        InternalDiagnosticsLogger.logInteraction(this, "ANDROID_AUTO", "Broadcast telemetry dispatched for ${evaluation.platform}")
    }

    override fun onInterrupt() {
        guidance?.clear()
        dismissOverlay()
        InternalDiagnosticsLogger.logInteraction(this, "SERVICE", "OptimizedOfferEngine interrupted.")
    }

    override fun onDestroy() {
        guidance?.clear()
        dismissOverlay()
        InternalDiagnosticsLogger.logInteraction(this, "SERVICE", "OptimizedOfferEngine destroyed.")
        super.onDestroy()
    }
}
