package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import com.driverade.data.DriverPreferences
import java.util.Locale

class LoggerService : AccessibilityService() {

    companion object {
        private const val TAG = "DriveradeLogger"
        private const val HICH_PACKAGE = "com.hich.driver"
        private const val FLASH_DURATION_MS = 500L
        private const val PILL_DURATION_MS = 3_000L
    }

    private lateinit var prefs: DriverPreferences
    private lateinit var engine: OptimizedOfferEngine
    private val handler = Handler(Looper.getMainLooper())
    private var pendingEventScan: Runnable? = null
    private var overlay: FrameLayout? = null
    private var overlayFlashReset: Runnable? = null
    private var overlayRemoval: Runnable? = null
    private var lastOfferKey: String? = null
    private var lastLoggedOfferKey: String? = null
    private var lastAcceptedOfferKey: String? = null
    private var lastAlertedOfferKey: String? = null
    private var lastHichSwitchOfferKey: String? = null

    private val pollingScan = object : Runnable {
        override fun run() {
            scanActiveWindow()
            handler.postDelayed(this, prefs.customPollingMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = DriverPreferences(this)
        engine = OptimizedOfferEngine(prefs.targetRate, prefs.minPayout)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        pendingEventScan?.let { handler.removeCallbacks(it) }
        val scan = Runnable { scanActiveWindow() }
        pendingEventScan = scan
        handler.postDelayed(scan, prefs.customDebounceMs)
    }

    private fun scanActiveWindow() {
        val root = rootInActiveWindow
        if (root == null) {
            clearOfferTracking()
            return
        }
        try {
            val packageName = root.packageName?.toString() ?: ""
            val isUber = packageName.contains("uber", ignoreCase = true)
            val isLyft = packageName.contains("lyft", ignoreCase = true)
            val isHich = packageName.contains("hich", ignoreCase = true)
            val appTag = when {
                isUber -> "UBER"
                isLyft -> "LYFT"
                isHich -> "HICH"
                else -> "OTHER"
            }
            val rootText = dumpText(root)

            val isOnline = rootText.contains("You're online", ignoreCase = true) ||
                rootText.contains("Finding trips", ignoreCase = true) ||
                rootText.contains("Searching", ignoreCase = true) ||
                rootText.contains("Waiting", ignoreCase = true) ||
                rootText.contains("GO OFFLINE", ignoreCase = true)

            if (isOnline && (isUber || isLyft || isHich) &&
                DriveradeState.currentMode == DriveradeState.OperatingMode.OFF
            ) {
                DriveradeState.updateOperatingMode(DriveradeState.OperatingMode.ALERT_AND_LOGGER)
                prefs.onModeChanged(isActive = true)
            }

            if (DriveradeState.currentMode == DriveradeState.OperatingMode.OFF) {
                clearOfferTracking()
                return
            }

            engine.updateThresholds(prefs.targetRate, prefs.minPayout)
            val metrics = engine.evaluateNode(root)
            if (metrics == null) {
                clearOfferTracking()
                return
            }
            if (metrics.isAuthoritativeMapTotal) {
                if (appTag != "OTHER") {
                    if (prefs.handleMapTotalUpdate(metrics.fare, appTag)) {
                        DriveradeState.notifyStatsChanged()
                    }
                }
                clearOfferTracking()
                return
            }
            if (!metrics.isRideOffer || (metrics.fare <= 0.0 && metrics.hourlyRate <= 0.0)) {
                clearOfferTracking()
                return
            }

            val offerKey = buildOfferKey(appTag, metrics)
            val isNewOffer = offerKey != lastOfferKey
            if (isNewOffer) {
                lastOfferKey = offerKey
                lastLoggedOfferKey = null
                lastAcceptedOfferKey = null
                lastAlertedOfferKey = null
                lastHichSwitchOfferKey = null
            }

            val mode = DriveradeState.currentMode
            val alertEnabled = mode == DriveradeState.OperatingMode.ALERT_ONLY ||
                mode == DriveradeState.OperatingMode.ALERT_AND_LOGGER
            val loggerEnabled = mode == DriveradeState.OperatingMode.LOGGER_ONLY ||
                mode == DriveradeState.OperatingMode.ALERT_AND_LOGGER

            if (prefs.autoSwitchToHich && (isUber || isLyft) && lastHichSwitchOfferKey != offerKey) {
                launchHich()
                lastHichSwitchOfferKey = offerKey
            }
            if (alertEnabled && lastAlertedOfferKey != offerKey) {
                showOfferPill(metrics.hourlyRate, metrics.isGoodOffer)
                lastAlertedOfferKey = offerKey
            }
            if (!loggerEnabled) return

            val isAccepted = rootText.contains("Accepted", ignoreCase = true) ||
                rootText.contains("En Route", ignoreCase = true) ||
                rootText.contains("Picked Up", ignoreCase = true)

            if (lastLoggedOfferKey != offerKey) {
                lastLoggedOfferKey = offerKey
                lastAcceptedOfferKey = if (isAccepted) offerKey else null
                prefs.recordOffer(
                    fare = metrics.fare,
                    isGood = metrics.isGoodOffer,
                    autoAddEarnings = isAccepted && prefs.getLastSeenMapTotal(appTag) < 0.0,
                    isAccepted = isAccepted,
                    appTag = appTag,
                    hourlyRate = metrics.hourlyRate
                )
                DriveradeState.notifyStatsChanged()
            } else if (isAccepted && lastAcceptedOfferKey != offerKey) {
                lastAcceptedOfferKey = offerKey
                prefs.recordOffer(
                    fare = metrics.fare,
                    isGood = metrics.isGoodOffer,
                    autoAddEarnings = prefs.getLastSeenMapTotal(appTag) < 0.0,
                    isAccepted = true,
                    appTag = appTag,
                    hourlyRate = metrics.hourlyRate,
                    countAsNewOffer = false
                )
                DriveradeState.notifyStatsChanged()
            }
        } finally {
            root.recycle()
        }
    }

    private fun launchHich() {
        val launchIntent = packageManager.getLaunchIntentForPackage(HICH_PACKAGE)
        if (launchIntent == null) {
            Log.w(TAG, "Hich app is not installed or has no launch activity: $HICH_PACKAGE")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open Hich", e)
        }
    }

    private fun buildOfferKey(appTag: String, metrics: OptimizedOfferEngine.Metrics): String {
        val stableText = metrics.signature
            .replace(Regex("""\b(?:accept(?:ed)?|en route|picked up)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d+\s*(?:secs?|seconds?)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return "$appTag|${metrics.fare}|${metrics.hourlyRate}|$stableText"
    }

    private fun clearOfferTracking() {
        lastOfferKey = null
        lastLoggedOfferKey = null
        lastAcceptedOfferKey = null
        lastAlertedOfferKey = null
        lastHichSwitchOfferKey = null
    }

    private fun showOfferPill(hourlyRate: Double, isGoodOffer: Boolean) {
        removeOfferOverlay()

        val flashColor = if (isGoodOffer) Color.rgb(0, 170, 80) else Color.rgb(220, 30, 45)
        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor((flashColor and 0x00FFFFFF) or 0x44000000)
        }
        val pillLines = mutableListOf<String>()
        if (prefs.showHourlyOnPill) {
            pillLines.add(
                if (hourlyRate > 0.0) String.format(Locale.US, "$%.2f/hr", hourlyRate)
                else "Hourly rate unavailable"
            )
        }
        if (prefs.showEarningsOnPill) {
            pillLines.add(String.format(Locale.US, "Today $%.2f", prefs.todayEarnings))
        }
        if (pillLines.isEmpty()) pillLines.add(if (isGoodOffer) "GOOD OFFER" else "BELOW THRESHOLD")
        val pill = TextView(this).apply {
            text = pillLines.joinToString("  •  ")
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((18 * density).toInt(), (10 * density).toInt(), (18 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 32 * density
                setColor(if (isGoodOffer) Color.rgb(35, 145, 85) else Color.rgb(190, 45, 50))
            }
        }
        root.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = (48 * density).toInt()
            }
        )

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(root, layoutParams)
            overlay = root
            val resetFlash = Runnable { root.setBackgroundColor(Color.TRANSPARENT) }
            val removeOverlay = Runnable { removeOfferOverlay() }
            overlayFlashReset = resetFlash
            overlayRemoval = removeOverlay
            handler.postDelayed(resetFlash, FLASH_DURATION_MS)
            handler.postDelayed(removeOverlay, PILL_DURATION_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to display offer alert", e)
        }
    }

    private fun removeOfferOverlay() {
        overlayFlashReset?.let { handler.removeCallbacks(it) }
        overlayRemoval?.let { handler.removeCallbacks(it) }
        overlayFlashReset = null
        overlayRemoval = null
        overlay?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Offer alert overlay was already removed", e)
            }
        }
        overlay = null
    }

    private fun dumpText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun traverse(curr: AccessibilityNodeInfo) {
            curr.text?.toString()?.let { sb.append(it).append(" ") }
            curr.contentDescription?.toString()?.let { sb.append(it).append(" ") }
            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { child ->
                    try {
                        traverse(child)
                    } finally {
                        child.recycle()
                    }
                }
            }
        }
        traverse(node)
        return sb.toString()
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        handler.removeCallbacks(pollingScan)
        handler.postDelayed(pollingScan, prefs.customPollingMs)
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollingScan)
        pendingEventScan?.let { handler.removeCallbacks(it) }
        removeOfferOverlay()
        if (DriveradeState.currentMode != DriveradeState.OperatingMode.OFF) {
            prefs.onModeChanged(isActive = false)
            DriveradeState.updateOperatingMode(DriveradeState.OperatingMode.OFF)
        }
        super.onDestroy()
    }
}
