package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

class LoggerService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (DriveradeState.currentMode == DriveradeState.OperatingMode.OFF) return
        val root = rootInActiveWindow ?: return
        try {
            val engine = OptimizedOfferEngine(
                DriveradeState.targetRateSetting,
                DriveradeState.minPayoutSetting
            )
            engine.evaluateNode(root)
        } finally {
            root.recycle()
        }
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
