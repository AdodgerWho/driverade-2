package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.driverade.data.DriverPreferences

class LoggerService : AccessibilityService() {

    private lateinit var prefs: DriverPreferences
    private val engine = OptimizedOfferEngine()
    private var lastRecordedFare: Double = -1.0
    private var lastRecordTimeMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = DriverPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (DriveradeState.currentMode == DriveradeState.OperatingMode.OFF) return
        val root = rootInActiveWindow ?: return
        try {
            val rootText = dumpText(root)
            val isAccepted = rootText.contains("Accepted", ignoreCase = true) ||
                             rootText.contains("En Route", ignoreCase = true) ||
                             rootText.contains("Picked Up", ignoreCase = true) ||
                             rootText.contains("Heading to", ignoreCase = true) ||
                             rootText.contains("Matched", ignoreCase = true) ||
                             rootText.contains("Priority Mode", ignoreCase = true) ||
                             rootText.contains("HICH Driver", ignoreCase = true)

            val metrics = engine.evaluateNode(root)
            if (metrics != null && metrics.fare > 0.0) {
                if (metrics.isAuthoritativeMapTotal) {
                    prefs.handleMapTotalUpdate(metrics.fare)
                    DriveradeState.notifyStatsChanged()
                    return
                }

                val now = System.currentTimeMillis()
                if (metrics.fare == lastRecordedFare && (now - lastRecordTimeMs) < 60000L) {
                    return
                }

                val isGood = (metrics.hourlyRate >= DriveradeState.targetRateSetting) ||
                        (metrics.fare >= DriveradeState.minPayoutSetting && metrics.hourlyRate == 0.0)

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
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }
}
