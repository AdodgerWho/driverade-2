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
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.driverade.data.DriverPreferences
import com.driverade.receiver.AndroidAutoReceiver
import java.util.Locale
import java.security.MessageDigest

class OptimizedOfferEngine : AccessibilityService() {

    private lateinit var prefs: DriverPreferences
    private var windowManager: WindowManager? = null
    private var activeOverlayView: LinearLayout? = null
    private var activeOverlayCaptureId: String? = null
    private val overlayRequestGate = OverlayRequestGate()
    private var guidance: GuideHighlighter? = null
    private var visualDebug: VisualDebugCapture? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val testPillReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CLEAR_VISUAL_BUFFER) {
                visualDebug?.clearRollingBuffer()
                return
            }
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
                val parsed = if (intent.getStringExtra(EXTRA_TEST_SOURCE) == "OCR") {
                    RestoredOfferParser.fromUberOcr(raw)
                } else RestoredOfferParser.fromTree(platform, nodes)
                val reason = parsed.reason
                val accepted = parsed.offer != null
                val evaluation = parsed.offer?.let { evaluationOf(it, "synthetic.test") }
                val status = when {
                    !accepted && (reason.startsWith("missing_") ||
                        reason.startsWith("ocr_") ||
                        reason == "history_or_navigation" ||
                        reason == "unsupported_platform" ||
                        reason == "requires_two_pickup_and_trip_legs") -> "GATE_REJECT"
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
                    status, provenanceStatus, evaluation)
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
            showHourlyRateOverlay(sample, "synthetic-alert-pill")
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

    /**
     * Bounded metadata for background overlays that yield zero text nodes.
     * No view text, content descriptions, rider details or screen pixels are logged.
     */
    private fun logZeroNodeWindowTrace(event: AccessibilityEvent, eventPackage: String, captureId: String) {
        try {
            val all = windows.orEmpty()
            val eventRoot = event.source
            val activeRoot = rootInActiveWindow
            val description = all.take(12).joinToString(";") { window ->
                val root = try { window.root } catch (_: Exception) { null }
                val pkg = root?.packageName?.toString() ?: "UNKNOWN"
                val bounds = android.graphics.Rect()
                try { window.getBoundsInScreen(bounds) } catch (_: Exception) { }
                "id=${window.id},type=${window.type},layer=${window.layer}," +
                    "pkg=$pkg,root=${root != null},children=${root?.childCount ?: -1}," +
                    "focused=${window.isFocused},active=${window.isActive}," +
                    "bounds=${bounds.width()}x${bounds.height()}," +
                    "eventWindow=${window.id == event.windowId}"
            }
            InternalDiagnosticsLogger.logInteraction(this, "UBER_ZERO_NODE_WINDOWS",
                "capture=$captureId pkg=$eventPackage eventWindow=${event.windowId} " +
                    "eventSource=${eventRoot != null} eventChildren=${eventRoot?.childCount ?: -1} " +
                    "activeRootPkg=${activeRoot?.packageName ?: "UNKNOWN"} " +
                    "windowCount=${all.size} inspected=${minOf(all.size, 12)} windows=[$description] " +
                    "text=OMITTED")
        } catch (e: Exception) {
            InternalDiagnosticsLogger.logInteraction(this, "UBER_ZERO_NODE_WINDOWS_ERROR",
                "capture=$captureId exception=${e.javaClass.simpleName}")
        }
    }

    private var lastOfferSignature: String = ""
    private var lastOfferTimestamp: Long = 0L

    companion object {
        const val ACTION_TEST_ALERT_PILL = "com.driverade.action.TEST_ALERT_PILL"
        const val ACTION_CLEAR_VISUAL_BUFFER = "com.driverade.action.CLEAR_VISUAL_BUFFER"
        const val ACTION_TEST_OFFER_CARD = "com.driverade.action.TEST_OFFER_CARD"
        const val EXTRA_TEST_PLATFORM = "test_platform"
        const val EXTRA_TEST_NODES = "test_nodes"
        const val EXTRA_TEST_SOURCE = "test_source"
        const val EXTRA_TEST_PAYOUT = "test_payout"
        const val EXTRA_TEST_MINUTES = "test_minutes"
        const val EXTRA_TEST_MILES = "test_miles"
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
            "com.lyft.android.driver" to "LYFT", // confirmed historical Driverlogger captures
            "com.hich.driver" to "HICH",
            "us.mi.hich.driver" to "HICH", // confirmed historical Driverlogger captures
            "com.doordash.driverapp" to "DOORDASH",
            "com.grubhub.driver" to "GRUBHUB"
        )


    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = DriverPreferences(this)
        lastUberEventElapsed = android.os.SystemClock.elapsedRealtime()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        guidance = GuideHighlighter(this)
        visualDebug = VisualDebugCapture(this)
        visualDebug?.setUberOcrListener(::handleRestoredOcr)
        mainHandler.postDelayed(restoredTick, 1_200L)
        val filter = IntentFilter().apply {
            addAction(ACTION_TEST_ALERT_PILL)
            addAction(ACTION_TEST_OFFER_CARD)
            addAction(ACTION_CLEAR_VISUAL_BUFFER)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(testPillReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(testPillReceiver, filter)
        }
        testReceiverRegistered = true
        InternalDiagnosticsLogger.initializeSession(this, prefs)
        InternalDiagnosticsLogger.logInteraction(this, "RESTORED_DIAGNOSTIC_READY",
            "paths=EVENT_TREE,TIMED_TREE,UBER_WINDOW_OR_DISPLAY_OCR " +
                "captureCorrelation=ENABLED pngOptIn=${visualDebug?.isEnabled() == true} " +
                "privateInspection=${DeveloperInspection.isActive(this)} rawText=OMITTED")
        InternalDiagnosticsLogger.logInteraction(this, "SERVICE", "OptimizedOfferEngine connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: "unknown.package"
        val captureId = "${System.currentTimeMillis()}-${++diagnosticCaptureSequence}"
        visualDebug?.observe(event, pkgName, captureId)
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
        if (pkgName == "com.ubercab.driver" && DeveloperInspection.isActive(this)) {
            try {
                DeveloperInspection.observe(this, event, captureId, windows.orEmpty(), rootInActiveWindow)
            } catch (e: Exception) {
                InternalDiagnosticsLogger.logInteraction(this, "DEV_INSPECTION_ERROR",
                    "capture=$captureId reason=${e.javaClass.simpleName}")
            }
        }

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
        if (platform == "UBER") lastUberEventElapsed = android.os.SystemClock.elapsedRealtime()

        processRestoredTrees(platform, pkgName, captureId, event.source, event.windowId)
    }

    /** Trace all state changes, but limit identical timer/event repetitions to 15s. */
    private val restoredLogState = mutableMapOf<String, Pair<Int, Long>>()
    private fun shouldTraceRestored(key: String, fingerprint: Int): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val previous = restoredLogState[key]
        if (previous?.first == fingerprint && now - previous.second < 15_000L) return false
        if (restoredLogState.size > 200) restoredLogState.clear()
        restoredLogState[key] = fingerprint to now
        return true
    }

    /** Old Driverlogger's event + timed multi-window tree collector. */
    private fun processRestoredTrees(
        platform: String, packageName: String, captureId: String,
        eventSource: AccessibilityNodeInfo?, eventWindowId: Int,
        acquisition: String = "EVENT"
    ) {
        val frames = linkedMapOf<Int, AccessibilityNodeInfo>()
        try {
            windows.orEmpty().forEach { w ->
                val root = runCatching { w.root }.getOrNull()
                if (root?.packageName?.toString() == packageName) {
                    frames[w.id] = root
                }
            }
            val active = runCatching { rootInActiveWindow }.getOrNull()
            if (active?.packageName?.toString() == packageName &&
                !frames.containsKey(active.windowId)) {
                frames[active.windowId] = active
            }
            if (eventSource?.packageName?.toString() == packageName &&
                !frames.containsKey(eventWindowId)) {
                frames[eventWindowId] = eventSource
            }
        } catch (e: Exception) {
            InternalDiagnosticsLogger.logInteraction(this, "RESTORED_WINDOW_ERROR",
                "capture=" + captureId + " exception=" + e.javaClass.simpleName)
        }

        // Correlate an event or timer with the actual app-owned windows available.
        // Metadata only: do not persist window text or raw node contents.
        val sourceOwned = eventSource?.packageName?.toString() == packageName
        val windowFingerprint = frames.size * 31 + (if (sourceOwned) 1 else 0)
        if (shouldTraceRestored("ACQUISITION|" + acquisition + "|" + packageName,
                windowFingerprint)) {
            InternalDiagnosticsLogger.logInteraction(this, "RESTORED_ACQUISITION",
                "capture=" + captureId + " trigger=" + acquisition +
                    " platform=" + platform + " eventWindow=" + eventWindowId +
                    " eventSourceOwned=" + sourceOwned + " ownedRoots=" + frames.size +
                    " rawText=OMITTED")
        }

        if (frames.isEmpty()) {
            if (shouldTraceRestored("NO_ROOT|" + packageName, 0))
                InternalDiagnosticsLogger.logInteraction(this, "RESTORED_TREE_MISS",
                    "capture=" + captureId + " trigger=" + acquisition + " platform=" + platform +
                        " reason=NO_OWNED_ROOT rawText=OMITTED")
            return
        }
        var foundText = false
        frames.forEach { (winId, root) ->
            val texts = mutableListOf<String>()
            val fields = mutableListOf<UberOfferFieldResolver.Node>()
            traverseNodeTree(root, texts, 0, fields, winId)
            if (texts.isEmpty()) {
                if (shouldTraceRestored("NO_TEXT|" + winId, root.childCount))
                    InternalDiagnosticsLogger.logInteraction(this, "RESTORED_TREE_MISS",
                        "capture=" + captureId + " trigger=" + acquisition + " windowId=" + winId +
                            " reason=NO_TEXT nodesMayExist=true childCount=" + root.childCount)
                return@forEach
            }
            foundText = true
            TipLedger.observe(this, platform, texts)
            if (platform == "UBER") UberMapEarnings.observe(this, texts, "TREE_" + acquisition)
            val resolved = if (platform == "UBER" || platform == "UBER_EATS")
                UberOfferFieldResolver.resolve(fields) else null
            val parsed = RestoredOfferParser.fromTree(platform, texts, resolved?.payout)
            val fingerprint = texts.joinToString("\u001f").hashCode() * 31 +
                parsed.reason.hashCode()
            if (shouldTraceRestored("TREE|" + platform + "|" + winId, fingerprint)) {
                if (resolved != null)
                    InternalDiagnosticsLogger.logInteraction(this, "UBER_FIELD_PROVENANCE",
                        "capture=" + captureId + " " + resolved.diagnostic())
                InternalDiagnosticsLogger.logInteraction(this, "RESTORED_TREE_PARSE",
                    "capture=" + captureId + " trigger=" + acquisition + " windowId=" + winId +
                        " platform=" + platform + " textFields=" + texts.size + " " + parsed.diagnostic())
                if (platform == "UBER" || platform == "LYFT") {
                    val report = OfferParserComparison.compare(platform, texts,
                        parsed.offer != null, parsed.offer?.let {
                            OfferParserComparison.ActiveSnapshot(
                                it.payout, it.totalMinutes, it.totalMiles, it.hourlyRate)
                        })
                    InternalDiagnosticsLogger.logInteraction(this, "PARSER_COMPARISON",
                        report.diagnostic("LIVE_RESTORED_" + acquisition, captureId, parsed.reason))
                }
            }
            parsed.offer?.let { publishRestoredCandidate(it, packageName, captureId) }
        }
        if (!foundText && shouldTraceRestored("PARSE_ABORT|" + packageName, 0)) {
            InternalDiagnosticsLogger.logInteraction(this, "PARSE_ABORT",
                "capture=" + captureId + " trigger=" + acquisition +
                    " reason=NO_ACCESSIBILITY_TEXT ocrFallback=ELIGIBLE")
        }
    }

    private fun publishRestoredCandidate(
        candidate: RestoredOfferParser.Candidate, pkg: String, captureId: String
    ) {
        if (candidate.platform == "UBER") visualDebug?.onOfferRecognized(captureId)
        val evaluation = evaluationOf(candidate, pkg)
        val signature = candidate.platform + "|" + candidate.kind + "|" +
            candidate.payout + "|" + candidate.pickupMinutes + "|" +
            candidate.pickupMiles + "|" + candidate.tripMinutes + "|" +
            candidate.tripMiles
        val now = android.os.SystemClock.elapsedRealtime()
        val signatureHash = MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val dedup = getSharedPreferences("offer_deduplication", Context.MODE_PRIVATE)
        val previousAt = dedup.getLong("elapsed", -1L)
        val persistedDuplicate = boot >= 0 && boot == dedup.getInt("boot", -1) &&
            signatureHash == dedup.getString("signature_hash", null) &&
            previousAt >= 0 && now >= previousAt && now - previousAt < 90_000L
        if ((signatureHash == lastOfferSignature && now >= lastOfferTimestamp &&
                now - lastOfferTimestamp < 90_000L) || persistedDuplicate) {
            if (shouldTraceRestored("DEBOUNCE|" + signature, 0))
                InternalDiagnosticsLogger.logInteraction(this, "DEBOUNCE",
                    "capture=" + captureId + " suppressed=true source=" + candidate.source)
            return
        }
        lastOfferSignature = signatureHash
        lastOfferTimestamp = now
        if (boot >= 0) {
            dedup.edit().putString("signature_hash", signatureHash)
                .putLong("elapsed", now).putInt("boot", boot).commit()
        }
        InternalDiagnosticsLogger.logInteraction(this, "PARSE_RESULT",
            "capture=" + captureId + " source=" + candidate.source +
                " kind=" + candidate.kind + " payout=" + candidate.payout +
                " pickupMin=" + candidate.pickupMinutes + " pickupMi=" + candidate.pickupMiles +
                " tripMin=" + candidate.tripMinutes + " tripMi=" + candidate.tripMiles +
                " hourly=" + evaluation.hourlyRate + " target=" + evaluation.meetsTarget)
        DriveradeState.recordOffer(evaluation)
        prefs.todayOffersCount += 1
        if (evaluation.meetsTarget) prefs.todayGoodOffersCount += 1
        if (prefs.loggerEnabled) LoggerService.recordOfferDiagnostic(this, prefs, evaluation)
        if (prefs.alerterEnabled) {
            InternalDiagnosticsLogger.logInteraction(this, "ALERT_DISPATCH",
                "capture=" + captureId + " source=" + candidate.source)
            broadcastToAndroidAuto(evaluation)
            showHourlyRateOverlay(evaluation, captureId)
        } else {
            InternalDiagnosticsLogger.logInteraction(this, "ALERT_DISABLED",
                "capture=" + captureId + " live offer evaluated, alerter disabled")
        }
    }

    private fun evaluationOf(
        c: RestoredOfferParser.Candidate, pkg: String
    ): OfferEvaluation = OfferEvaluation(
        platform = c.platform,
        packageName = pkg,
        payout = c.payout,
        totalMiles = c.totalMiles,
        totalMinutes = c.totalMinutes,
        hourlyRate = c.hourlyRate,
        dollarsPerMile = c.dollarsPerMile,
        meetsTarget = c.hourlyRate >= prefs.targetHourlyRate &&
            c.payout >= prefs.minPayout,
        rawNodes = emptyList()
    )

    private var lastUberEventElapsed = 0L
    private var lastSilenceDiagnosticElapsed = 0L
    private val restoredTick = object : Runnable {
        override fun run() {
            try {
                visualDebug?.expireRollingFrames()
                val power = getSystemService(POWER_SERVICE) as android.os.PowerManager
                if (power.isInteractive && ::prefs.isInitialized) {
                    val present = windows.orEmpty().mapNotNull { w ->
                        val root = runCatching { w.root }.getOrNull()
                        val pkg = root?.packageName?.toString()
                        if (pkg != null && PACKAGE_PLATFORM_MAP.containsKey(pkg)) {
                            pkg to root
                        } else null
                    }.distinctBy { it.first }
                    // Silence is a candidate observation, not a proven accessibility hang.
                    // Only sample when an Uber-owned window is currently inspectable.
                    val elapsed = android.os.SystemClock.elapsedRealtime()
                    if (present.any { it.first == "com.ubercab.driver" } &&
                        elapsed - lastUberEventElapsed > 60_000L &&
                        elapsed - lastSilenceDiagnosticElapsed > 180_000L) {
                        lastSilenceDiagnosticElapsed = elapsed
                        InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine,
                            "EVENT_SILENCE_CANDIDATE",
                            "platform=UBER accessibleWindow=true noUberEventForMs=" +
                                (elapsed - lastUberEventElapsed) + " classification=UNCONFIRMED")
                    }
                    present.forEach { (pkg, _) ->
                        val platform = PACKAGE_PLATFORM_MAP[pkg] ?: return@forEach
                        if (prefs.isPlatformEnabled(platform)) {
                            val id = "timed-" + System.currentTimeMillis()
                            processRestoredTrees(platform, pkg, id, null, -1, "TIMED")
                        }
                    }
                    val recentlyTriggered =
                        android.os.SystemClock.elapsedRealtime() - lastUberEventElapsed < 8_000L
                    val unreadableUberOverlay = windows.orEmpty().any { w ->
                        val root = runCatching { w.root }.getOrNull()
                        w.layer >= 1 && root?.packageName?.toString() == "com.ubercab.driver" &&
                            isLegacyBlankOfferWindow(root)
                    }
                    if ((recentlyTriggered || unreadableUberOverlay) &&
                        prefs.isPlatformEnabled("UBER")) {
                        val ocrCaptureId = "timed-" + System.currentTimeMillis()
                        if (shouldTraceRestored("TIMED_OCR_TRIGGER", 
                                (if (recentlyTriggered) 1 else 0) +
                                    (if (unreadableUberOverlay) 2 else 0))) {
                            InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine,
                                "RESTORED_TIMED_OCR_TRIGGER",
                                "capture=" + ocrCaptureId + " recentEvent=" + recentlyTriggered +
                                    " blankOverlay=" + unreadableUberOverlay +
                                    " pngOptIn=" + (visualDebug?.isEnabled() == true))
                        }
                        visualDebug?.observeTimedUber(ocrCaptureId)
                    } else if (prefs.isPlatformEnabled("UBER") &&
                        visualDebug?.isEnabled() == true) {
                        val uberRootAvailable = windows.orEmpty().any { w ->
                            runCatching { w.root?.packageName?.toString() == "com.ubercab.driver" }
                                .getOrDefault(false)
                        }
                        if (uberRootAvailable || visualDebug?.hasActivePostroll() == true)
                            visualDebug?.observePrerollUber("preroll-" + System.currentTimeMillis())
                    }
                }
            } catch (e: Exception) {
                InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine,
                    "RESTORED_TICK_ERROR", "reason=" + e.javaClass.simpleName)
            } finally {
                mainHandler.postDelayed(this, 1_200L)
            }
        }
    }

    /** Driverlogger3_4 blank RecyclerView overlay heuristic, bounded to 120 nodes. */
    private fun isLegacyBlankOfferWindow(root: AccessibilityNodeInfo): Boolean {
        val rect = android.graphics.Rect()
        root.getBoundsInScreen(rect)
        if (rect.height() < 400 || root.childCount <= 1) return false
        var scanned = 0
        var hasRecycler = false
        var hasText = false
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12 || scanned++ >= 120) return
            if (node.className?.toString()?.contains("RecyclerView") == true) hasRecycler = true
            if (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) hasText = true
            if (hasText) return
            for (i in 0 until node.childCount.coerceAtMost(60)) {
                val child = runCatching { node.getChild(i) }.getOrNull()
                if (child != null) visit(child, depth + 1)
                if (hasText || scanned >= 120) break
            }
        }
        visit(root, 0)
        return hasRecycler && !hasText
    }

    private fun handleRestoredOcr(captureId: String, ocr: String, captureSource: String) {
        if (!::prefs.isInitialized) {
            InternalDiagnosticsLogger.logInteraction(this, "RESTORED_OCR_SKIPPED",
                "capture=" + captureId + " reason=PREFERENCES_NOT_READY")
            return
        }
        if (!prefs.isPlatformEnabled("UBER")) {
            InternalDiagnosticsLogger.logInteraction(this, "RESTORED_OCR_SKIPPED",
                "capture=" + captureId + " reason=UBER_DISABLED")
            return
        }
        if (android.os.SystemClock.elapsedRealtime() - lastUberEventElapsed > 8_000L) {
            InternalDiagnosticsLogger.logInteraction(this, "RESTORED_OCR_SKIPPED",
                "capture=" + captureId + " reason=EVENT_OLDER_THAN_8_SECONDS")
            return
        }
        UberMapEarnings.observe(this, ocr.lines(), "OCR_" + captureSource)
        val parsed = RestoredOfferParser.fromUberOcr(ocr, captureSource)
        // Record every OCR decision so each completed image has a matching parser outcome.
        InternalDiagnosticsLogger.logInteraction(this, "RESTORED_OCR_PARSE",
            "capture=" + captureId + " " + parsed.diagnostic())
        parsed.offer?.let { publishRestoredCandidate(it, "com.ubercab.driver", captureId) }
    }

    private fun sendSyntheticResult(requestId: String, text: String,
                                    status: String? = null, provenance: String? = null,
                                    evaluation: OfferEvaluation? = null) {
        sendBroadcast(Intent(ACTION_TEST_OFFER_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEST_REQUEST_ID, requestId)
            putExtra(EXTRA_TEST_RESULT_TEXT, text.take(1800))
            putExtra(EXTRA_TEST_RESULT_STATUS, status)
            putExtra(EXTRA_TEST_RESULT_PROVENANCE, provenance)
            if (evaluation != null) {
                putExtra(EXTRA_TEST_PAYOUT, evaluation.payout)
                putExtra(EXTRA_TEST_MINUTES, evaluation.totalMinutes)
                putExtra(EXTRA_TEST_MILES, evaluation.totalMiles)
            }
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
        results: MutableCollection<String>,
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
        platform: String, packageName: String, texts: List<String>,
        verifiedPayout: Double? = null
    ): OfferEvaluation? =
        RestoredOfferParser.fromTree(platform, texts, verifiedPayout).offer?.let {
            evaluationOf(it, packageName)
        }

    private fun showHourlyRateOverlay(evaluation: OfferEvaluation, captureId: String) {
        val requestId = overlayRequestGate.begin()
        InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_REQUEST",
            "capture=" + captureId + " platform=" + evaluation.platform +
                " requestId=" + requestId)
        if (!Settings.canDrawOverlays(this)) {
            InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_FAIL",
                "capture=" + captureId + " reason=OVERLAY_PERMISSION_DENIED")
            return
        }

        mainHandler.post {
            if (!overlayRequestGate.isCurrent(requestId)) {
                InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_SUPERSEDED",
                    "capture=" + captureId + " requestId=" + requestId)
                return@post
            }
            dismissOverlay("REPLACED")

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
            container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine,
                        "OVERLAY_ATTACHED", "capture=" + captureId +
                            " requestId=" + requestId)
                }
                override fun onViewDetachedFromWindow(v: View) {
                    InternalDiagnosticsLogger.logInteraction(this@OptimizedOfferEngine,
                        "OVERLAY_DETACHED", "capture=" + captureId +
                            " requestId=" + requestId)
                }
            })
            container.setOnClickListener {
                if (activeOverlayView === container) dismissOverlay("TAP")
            }

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
                val wm = windowManager ?: throw IllegalStateException("WindowManager unavailable")
                wm.addView(container, params)
                activeOverlayView = container
                activeOverlayCaptureId = captureId
                InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_SHOWN",
                    "capture=" + captureId + " platform=" + evaluation.platform +
                        " requestId=" + requestId + " stage=addView_returned")
                mainHandler.post {
                    if (activeOverlayView === container) {
                        InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_WINDOW_STATE",
                            "capture=" + captureId + " requestId=" + requestId +
                                " attached=" + container.isAttachedToWindow +
                                " shown=" + container.isShown)
                    }
                }
                mainHandler.postDelayed({
                    // A prior offer's timeout cannot dismiss the replacement overlay.
                    if (overlayRequestGate.isCurrent(requestId) && activeOverlayView === container)
                        dismissOverlay("TIMEOUT")
                }, OVERLAY_DURATION_MS)
            } catch (e: Exception) {
                InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_ERROR",
                    "capture=" + captureId + " reason=" + e.javaClass.simpleName)
            }
        }
    }

    private fun dismissOverlay(reason: String = "EXTERNAL") {
        activeOverlayView?.let { view ->
            val captureId = activeOverlayCaptureId ?: "UNKNOWN"
            activeOverlayView = null
            activeOverlayCaptureId = null
            try {
                windowManager?.removeView(view)
                InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_DISMISSED",
                    "capture=" + captureId + " reason=" + reason)
            } catch (e: Exception) {
                InternalDiagnosticsLogger.logInteraction(this, "OVERLAY_DISMISS_ERROR",
                    "capture=" + captureId + " reason=" + e.javaClass.simpleName)
            }
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
        overlayRequestGate.invalidate()
        guidance?.clear()
        dismissOverlay("SERVICE_INTERRUPTED")
        InternalDiagnosticsLogger.logInteraction(this, "SERVICE", "OptimizedOfferEngine interrupted.")
    }

    override fun onDestroy() {
        overlayRequestGate.invalidate()
        if (testReceiverRegistered) {
            unregisterReceiver(testPillReceiver)
            testReceiverRegistered = false
        }
        guidance?.clear()
        dismissOverlay("SERVICE_DESTROYED")
        mainHandler.removeCallbacks(restoredTick)
        visualDebug?.close()
        InternalDiagnosticsLogger.logInteraction(this, "SERVICE", "OptimizedOfferEngine destroyed.")
        super.onDestroy()
    }
}
