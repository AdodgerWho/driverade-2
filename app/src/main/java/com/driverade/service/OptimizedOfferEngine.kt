package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
    private var visualDebug: VisualDebugCapture? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val testPillReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TEST_OFFER_CARD) {
                val platform = intent.getStringExtra(EXTRA_TEST_PLATFORM).orEmpty()
                val raw = intent.getStringExtra(EXTRA_TEST_NODES).orEmpty()
                if (platform !in setOf("UBER", "UBER_EATS", "LYFT", "DOORDASH", "GRUBHUB", "HICH") ||
                    raw.isBlank() || raw.length > 6000) {
                    InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine, "SYNTHETIC_TEST_INVALID", "Invalid platform or card length")
                    sendSyntheticResult(intent.getStringExtra(EXTRA_TEST_REQUEST_ID).orEmpty(), "Invalid test platform or card length.")
                    return
                }
                val requestId = intent.getStringExtra(EXTRA_TEST_REQUEST_ID).orEmpty()
                val nodes = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(60)
                val reason = OfferScreenClassifier.rejectionReason(nodes)
                val accepted = OfferScreenClassifier.isActiveOffer(nodes)
                val evaluation = if (accepted) parseOfferNodes(platform, "synthetic.test", nodes) else null
                val status = when {
                    !accepted -> "GATE_REJECT"
                    evaluation == null -> "PARSE_FAIL"
                    else -> "PASS"
                }
                var provenanceStatus: String? = null
                val fieldDiagnostic = if (platform == "UBER" || platform == "UBER_EATS") {
                    UberOfferFieldResolver.resolve(nodes.map { UberOfferFieldResolver.Node(it) }).also {
                        InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine,
                            "UBER_FIELD_PROVENANCE", "source=SYNTHETIC " + it.diagnostic())
                    }.let { provenanceStatus = it.source
                        "\nUber offer field binding: " + it.source +
                        " | payout=" + (it.payout ?: "NA") + "\n" }
                } else ""
                val report = OfferParserComparison.compare(platform, nodes, accepted, evaluation?.let {
                    OfferParserComparison.ActiveSnapshot(it.payout, it.totalMinutes, it.totalMiles, it.hourlyRate)
                })
                InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine, "SYNTHETIC_TEST_RESULT",
                    "source=SYNTHETIC platform=$platform status=$status gate=$reason nodes=" + nodes.size +
                    " payout=" + (evaluation?.payout ?: "NA") + " minutes=" + (evaluation?.totalMinutes ?: "NA") +
                    " miles=" + (evaluation?.totalMiles ?: "NA") + " hourly=" + (evaluation?.hourlyRate ?: "NA") +
                    "; no live counters, overlays, telemetry or alerts")
                InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine, "PARSER_COMPARISON",
                    report.diagnostic("SYNTHETIC", requestId, reason))
                sendSyntheticResult(requestId,
                    "Active path: $status | gate: $reason\n\n" + report.display() + fieldDiagnostic,
                    status, provenanceStatus)
                return
            }
            if (intent?.action != ACTION_TEST_ALERT_PILL) return
            val sample = OfferEvaluation(
                platform = "TEST",
                packageName = packageName,
                payout = 18.50,
                totalMiles = 5.2,
                totalMinutes = 25.0,
                hourlyRate = 44.40,
                dollarsPerMile = 3.56,
                meetsTarget = true,
                rawNodes = emptyList()
            )
            showHourlyRateOverlay(sample)
            InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine, "PILL_TEST", "Sample overlay requested locally")
        }
    }
    private var testReceiverRegistered = false
    private var lastGuideTrace = ""
    private var lastGuideTraceAt = 0L
    private var diagnosticCaptureSequence = 0L
    private var lastVisibilitySignature = ""
    private var lastVisibilityLogAt = 0L

    /** Window metadata only; never capture screen text or infer projection visibility. */
    private fun logWindowVisibility(event: AccessibilityEvent, eventPackage: String) {
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            val activePackage = rootInActiveWindow?.packageName?.toString()
            val accessibleWindows = windows.orEmpty()
            val targetWindows = accessibleWindows.filter { window ->
                val pkg = window.root?.packageName?.toString()
                pkg != null && resolvePlatform(pkg) != null
            }
            val eventWindow = accessibleWindows.firstOrNull { it.id == event.windowId }
            val visibility = when {
                activePackage == eventPackage -> "ACTIVE_ROOT"
                targetWindows.any { it.root?.packageName?.toString() == eventPackage } -> "OTHER_ACCESSIBLE_WINDOW"
                else -> "NOT_OBSERVED"
            }
            val type = when (eventWindow?.type) {
                android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
                android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
                android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
                android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
                else -> "UNKNOWN"
            }
            val signature = "$eventPackage|$activePackage|$visibility|$type|${eventWindow?.isActive}|${eventWindow?.isFocused}|${targetWindows.size}"
            val now = android.os.SystemClock.elapsedRealtime()
            if (signature != lastVisibilitySignature || now - lastVisibilityLogAt >= 15000L) {
                lastVisibilitySignature = signature
                lastVisibilityLogAt = now
                InternalDiagnosticsLogger.logInteraction(this, "WINDOW_VISIBILITY",
                    "eventPkg=$eventPackage activePkg=${activePackage ?: "UNKNOWN"} " +
                    "visibility=$visibility eventWindowType=$type " +
                    "windowActive=${eventWindow?.isActive ?: "UNKNOWN"} " +
                    "windowFocused=${eventWindow?.isFocused ?: "UNKNOWN"} " +
                    "accessibleTargetWindows=${targetWindows.size} androidAutoDisplay=UNKNOWN " +
                    "inspectionMs=${android.os.SystemClock.elapsedRealtime() - started}")
            }
        } catch (e: Exception) {
            InternalDiagnosticsLogger.logInteraction(this, "WINDOW_VISIBILITY_ERROR",
                "Window metadata unavailable: ${e.javaClass.simpleName}")
        }
    }

    private var lastShadowSignature: String = ""
    private var lastShadowTime = 0L
    private var lastOfferSignature: String = ""
    private var lastOfferTimestamp: Long = 0L

    companion object {
        const val ACTION_TEST_ALERT_PILL = "com.driverade.action.TEST_ALERT_PILL"
        const val ACTION_TEST_OFFER_CARD = "com.driverade.action.TEST_OFFER_CARD"
        const val EXTRA_TEST_PLATFORM = "test_platform"
        const val EXTRA_TEST_NODES = "test_nodes"
        const val ACTION_TEST_OFFER_RESULT = "com.driverade.action.TEST_OFFER_RESULT"
        const val EXTRA_TEST_REQUEST_ID = "test_request_id"
        const val EXTRA_TEST_RESULT_TEXT = "test_result_text"
        const val EXTRA_TEST_RESULT_STATUS = "test_result_status"
        const val EXTRA_TEST_RESULT_PROVENANCE = "test_result_provenance"
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
        visualDebug = VisualDebugCapture(this)
        val filter = IntentFilter().apply { addAction(ACTION_TEST_ALERT_PILL); addAction(ACTION_TEST_OFFER_CARD) }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(testPillReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(testPillReceiver, filter)
        }
        testReceiverRegistered = true
        InternalDiagnosticsLogger.initializeSession(this, prefs)
        InternalDiagnosticsLogger.logInteraction(this, "SERVICE", "OptimizedOfferEngine connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: "unknown.package"
        val captureId = "${System.currentTimeMillis()}-${++diagnosticCaptureSequence}"
        visualDebug?.observe(event, pkgName)
        if (GuidedSettingsState.isActive(this) && (pkgName == "com.android.settings" || pkgName == "com.google.android.projection.gearhead")) {
            val guide = GuidedSettingsState.guide(this)
            val step = GuidedSettingsState.step(this)
            val trace = "$guide:$step:$pkgName:${event.eventType}:${event.windowId}"
            val traceAt = android.os.SystemClock.elapsedRealtime()
            if (trace != lastGuideTrace || traceAt - lastGuideTraceAt >= 3000L) {
                lastGuideTrace = trace
                lastGuideTraceAt = traceAt
                InternalDiagnosticsLogger.logInteraction(this, "GUIDE_TRACE",
                    "guide=$guide step=$step package=$pkgName eventType=${event.eventType} windowId=${event.windowId}")
            }
            val seen = guidance?.inspect(rootInActiveWindow, guide, step) == true
            GuidedSettingsState.setTargetSeen(this, seen)
            return
        }
        guidance?.clear()

        InternalDiagnosticsLogger.logRoutine(this, "EVENT_INGEST", "package=$pkgName type=${event.eventType}")

        if (resolvePlatform(pkgName) != null) logWindowVisibility(event, pkgName)

        val platform = resolvePlatform(pkgName)
        if (platform == null) {
            InternalDiagnosticsLogger.logRoutine(this, "FILTER_DROP", "package=$pkgName unrecognized")
            return
        }

        if (!prefs.isPlatformEnabled(platform)) {
            InternalDiagnosticsLogger.logInteraction(this, "FILTER_DROP", "Platform $platform is toggled OFF in driver preferences.")
            return
        }

        InternalDiagnosticsLogger.logInteraction(this, "PLATFORM_MATCH", "capture=$captureId platform=$platform eventType=${event.eventType} eventTime=${event.eventTime} windowId=${event.windowId} class=${event.className}")

        val collectedNodes = LinkedHashSet<String>()
        val fieldNodes = mutableListOf<UberOfferFieldResolver.Node>()

        event.source?.let { src ->
            traverseNodeTree(src, collectedNodes, 0, fieldNodes, event.windowId)
        }

        rootInActiveWindow?.let { root ->
            val rootPkg = root.packageName?.toString()
            if ((platform != "UBER" && platform != "UBER_EATS") || rootPkg == pkgName) {
                traverseNodeTree(root, collectedNodes, 0, fieldNodes, event.windowId)
            }
        }

        try {
            windows?.forEach { windowInfo ->
                windowInfo.root?.let { winRoot ->
                    val winPkg = winRoot.packageName?.toString() ?: pkgName
                    if (if (platform == "UBER" || platform == "UBER_EATS") winPkg == pkgName else resolvePlatform(winPkg) != null) {
                        traverseNodeTree(winRoot, collectedNodes, 0, fieldNodes, windowInfo.id)
                    }
                }
            }
        } catch (e: Exception) {
            InternalDiagnosticsLogger.logInteraction(this, "WARN", "Multi-window inspection warning: ${e.message}")
        }

        InternalDiagnosticsLogger.logInteraction(this, "NODE_SCRAPE", "capture=$captureId nodes=${collectedNodes.size} boundedText=${collectedNodes.take(30).map { it.take(80) }}")

        if (collectedNodes.isEmpty()) {
            InternalDiagnosticsLogger.logInteraction(this, "PARSE_ABORT", "No text nodes found to evaluate.")
            return
        }

        val resolvedUber = if (platform == "UBER" || platform == "UBER_EATS") {
            UberOfferFieldResolver.resolve(fieldNodes).also { resolved ->
                InternalDiagnosticsLogger.logInteraction(this, "UBER_FIELD_PROVENANCE",
                    "capture=$captureId " + resolved.diagnostic())
            }
        } else null
        // A verified payout is tied to a single offer window. Avoid unrelated
        // windows in its calculation; preserve original behavior otherwise.
        val rawList = if (resolvedUber?.payout != null && resolvedUber.windowId != null) {
            fieldNodes.asSequence().filter { it.visible && it.windowId == resolvedUber.windowId }
                .map { it.text }.distinct().toList()
        } else collectedNodes.toList()
        val joinedTextLower = rawList.joinToString(" ").lowercase(Locale.US)
        val hasValidKeyword = OFFER_KEYWORDS.any { joinedTextLower.contains(it) } ||
            joinedTextLower.contains("tip") || joinedTextLower.contains("earnings")

        if (!hasValidKeyword && platform != "UBER" && platform != "UBER_EATS") {
            InternalDiagnosticsLogger.logInteraction(this, "KEYWORD_DROP", "Ignored: Missing mandatory offer validation keywords.")
            return
        }

        // Observe explicitly labelled tip amounts on history screens, independently of offers.
        TipLedger.observe(this, platform, rawList)
        if (platform != "UBER" && platform != "UBER_EATS" && !OfferScreenClassifier.isActiveOffer(rawList)) {
            InternalDiagnosticsLogger.logInteraction(this, "OFFER_GATE_DROP", "Offer screen rejected: ${OfferScreenClassifier.rejectionReason(rawList)}")
            return
        }

        if (platform == "UBER" || platform == "UBER_EATS") {
            val payoutPresent = rawList.any { PAYOUT_REGEX.containsMatchIn(it) }
            val durationPresent = HOURS_MINUTES_REGEX.containsMatchIn(rawList.joinToString(" ")) ||
                rawList.any { MINUTES_REGEX.containsMatchIn(it) }
            val distancePresent = rawList.any { MILES_REGEX.containsMatchIn(it) }
            InternalDiagnosticsLogger.logInteraction(this, "UBER_CANDIDATE",
                "nodes=${rawList.size} keyword=$hasValidKeyword payout=$payoutPresent " +
                    "duration=$durationPresent distance=$distancePresent; raw text withheld")
        }

        // Map pills, earnings and pickup navigation are not incoming offers.
        // Keep candidate telemetry, but only actionable offer screens can alert.
        if ((platform == "UBER" || platform == "UBER_EATS") &&
            !OfferScreenClassifier.isActiveOffer(rawList)) {
            InternalDiagnosticsLogger.logInteraction(this, "UBER_OFFER_GATE_DROP",
                "reason=${OfferScreenClassifier.rejectionReason(rawList)} " +
                    "windowId=${event.windowId} nodes=${rawList.size}; text withheld")
            return
        }

        InternalDiagnosticsLogger.logInteraction(this, "PARSE_INPUT", "capture=$captureId payoutCandidates=${rawList.flatMap { PAYOUT_REGEX.findAll(it).map { m -> m.value }.toList() }.take(15)} durationCandidates=${MINUTES_REGEX.findAll(joinedTextLower).map { it.value }.take(15).toList()} distanceCandidates=${MILES_REGEX.findAll(joinedTextLower).map { it.value }.take(15).toList()}")
        val evaluation = parseOfferNodes(platform, pkgName, rawList, resolvedUber?.payout)
        // Shadow comparison reads the same gated live nodes. Its output is diagnostic only.
        // Do not alter the existing parse result, alerts, dedupe or offer statistics.
        if (platform == "UBER" || platform == "LYFT") {
            val signature = platform + "|" + rawList.joinToString("\u001f").hashCode()
            val nowShadow = android.os.SystemClock.elapsedRealtime()
            if (signature != lastShadowSignature || nowShadow - lastShadowTime >= 60_000L) {
                lastShadowSignature = signature
                lastShadowTime = nowShadow
                try {
                    val report = OfferParserComparison.compare(platform, rawList, true, evaluation?.let {
                        OfferParserComparison.ActiveSnapshot(it.payout, it.totalMinutes, it.totalMiles, it.hourlyRate)
                    })
                    InternalDiagnosticsLogger.logInteraction(this, "PARSER_COMPARISON",
                        report.diagnostic("LIVE_SHADOW", captureId, "none"))
                } catch (e: Exception) {
                    InternalDiagnosticsLogger.logInteraction(this, "PARSER_COMPARISON_ERROR",
                        "capture=$captureId platform=$platform error=" + e.javaClass.simpleName)
                }
            }
        }

        if (evaluation == null) {
            InternalDiagnosticsLogger.logInteraction(this, "PARSE_FAIL", "Offer fields invalid or missing; text content withheld for privacy")
            return
        }

        InternalDiagnosticsLogger.logInteraction(this, "PARSE_RESULT", "capture=$captureId payout=${evaluation.payout} minutes=${evaluation.totalMinutes} miles=${evaluation.totalMiles} hourly=${evaluation.hourlyRate} target=${evaluation.meetsTarget}")
        val signature = "${evaluation.platform}_${evaluation.payout}_${evaluation.totalMinutes}_${evaluation.totalMiles}"
        val now = System.currentTimeMillis()
        if (signature == lastOfferSignature && (now - lastOfferTimestamp) < 45000L) {
            InternalDiagnosticsLogger.logInteraction(this, "DEBOUNCE", "capture=$captureId suppressed=true elapsedMs=${now-lastOfferTimestamp}")
            return
        }
        lastOfferSignature = signature
        lastOfferTimestamp = now

        InternalDiagnosticsLogger.logInteraction(this, "SUCCESS", "Offer evaluated: Payout=$${evaluation.payout}, Hourly=$${evaluation.hourlyRate}/hr, TargetMet=${evaluation.meetsTarget}")

        // Recognition continues for local telemetry, but output channels are independent.
        DriveradeState.recordOffer(evaluation)
        prefs.todayOffersCount += 1
        if (evaluation.meetsTarget) prefs.todayGoodOffersCount += 1
        if (prefs.loggerEnabled) LoggerService.recordOfferDiagnostic(this, prefs, evaluation)
        if (prefs.alerterEnabled) {
            InternalDiagnosticsLogger.logInteraction(this, "ALERT_DISPATCH", "capture=$captureId live offer alert requested for $platform")
            broadcastToAndroidAuto(evaluation)
            showHourlyRateOverlay(evaluation)
        } else {
            InternalDiagnosticsLogger.logInteraction(this, "ALERT_DISABLED", "Live offer evaluated but alerter is disabled")
        }
    }

    private fun sendSyntheticResult(requestId: String, text: String,
                                    status: String? = null, provenance: String? = null) {
        sendBroadcast(Intent(ACTION_TEST_OFFER_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEST_REQUEST_ID, requestId)
            putExtra(EXTRA_TEST_RESULT_TEXT, text.take(1800))
            putExtra(EXTRA_TEST_RESULT_STATUS, status)
            putExtra(EXTRA_TEST_RESULT_PROVENANCE, provenance)
        })
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
        depth: Int,
        fieldNodes: MutableList<UberOfferFieldResolver.Node>,
        windowId: Int
    ) {
        if (node == null || depth > 28) return

        val viewId = node.viewIdResourceName
        val visible = node.isVisibleToUser
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            results.add(it)
            fieldNodes.add(UberOfferFieldResolver.Node(it, viewId, windowId, visible))
        }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            results.add(it)
            fieldNodes.add(UberOfferFieldResolver.Node(it, viewId, windowId, visible))
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            if (child != null) {
                traverseNodeTree(child, results, depth + 1, fieldNodes, windowId)
            }
        }
    }

    fun parseOfferNodes(
        platform: String,
        packageName: String,
        texts: List<String>,
        verifiedPayout: Double? = null
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

        if (verifiedPayout != null && (platform == "UBER" || platform == "UBER_EATS")) {
            payout = verifiedPayout
        }
        if (payout <= 0.0) return null

        if (totalMinutes <= 0.0 || totalMiles <= 0.0) return null
        val effectiveMinutes = totalMinutes
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
        if (testReceiverRegistered) {
            unregisterReceiver(testPillReceiver)
            testReceiverRegistered = false
        }
        guidance?.clear()
        dismissOverlay()
        InternalDiagnosticsLogger.logInteraction(this, "SERVICE", "OptimizedOfferEngine destroyed.")
        super.onDestroy()
    }
}
