package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.driverade.data.DriverPreferences

class LoggerService : AccessibilityService() {

    private lateinit var prefs: DriverPreferences
    private lateinit var engine: OptimizedOfferEngine
    private var lastRecordedFare: Double = -1.0
    private var lastRecordTimeMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = DriverPreferences(this)
        engine = OptimizedOfferEngine(prefs.targetRate, prefs.minPayout)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        try {
            val packageName = event?.packageName?.toString() ?: root.packageName?.toString() ?: ""
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

            // Automatic Online Detection
            val isOnline = rootText.contains("You're online", ignoreCase = true) ||
                           rootText.contains("Finding trips", ignoreCase = true) ||
                           rootText.contains("Searching", ignoreCase = true) ||
                           rootText.contains("Waiting", ignoreCase = true) ||
                           rootText.contains("GO OFFLINE", ignoreCase = true)

            if (isOnline && (isUber || isLyft || isHich)) {
                if (DriveradeState.currentMode == DriveradeState.OperatingMode.OFF) {
                    DriveradeState.currentMode = DriveradeState.OperatingMode.ALERT_AND_LOGGER
                    DriveradeState.notifyStatsChanged()
                }
            }

            if (DriveradeState.currentMode == DriveradeState.OperatingMode.OFF) return

            val metrics = engine.evaluateNode(root)
            if (metrics != null && metrics.fare > 0.0) {
                if (metrics.isAuthoritativeMapTotal) {
                    if (appTag != "OTHER") {
                        prefs.handleMapTotalUpdate(metrics.fare, appTag)
                        DriveradeState.notifyStatsChanged()
                    }
                    return
                }

                if (metrics.isRideOffer) {
                    // Auto-switch to Hich if requested and offer received from Uber/Lyft
                    if (prefs.autoSwitchToHich && (isUber || isLyft)) {
                        try {
                            val hichIntent = packageManager.getLaunchIntentForPackage("com.hich.driver") // example package
                            if (hichIntent != null) {
                                hichIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(hichIntent)
                            }
                        } catch (e: Exception) {}
                    }
                }

                val now = System.currentTimeMillis()
                if (metrics.fare == lastRecordedFare && (now - lastRecordTimeMs) < 60000L) return

                val isAccepted = rootText.contains("Accepted", ignoreCase = true) ||
                                 rootText.contains("En Route", ignoreCase = true) ||
                                 rootText.contains("Picked Up", ignoreCase = true) ||
                                 isHich

                if (isAccepted) {
                    lastRecordedFare = metrics.fare
                    lastRecordTimeMs = now
                    prefs.recordOffer(metrics.fare, isGood = true, autoAddEarnings = true)
                    DriveradeState.notifyStatsChanged()
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun dumpText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun traverse(curr: AccessibilityNodeInfo) {
            curr.text?.toString()?.let { sb.append(it).append(" ") }
            curr.contentDescription?.toString()?.let { sb.append(it).append(" ") }
            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { child ->
                    try { traverse(child) } finally { child.recycle() }
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
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }
}
