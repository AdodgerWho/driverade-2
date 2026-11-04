package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.driverade.data.DriverPreferences

class OptimizedOfferEngine : AccessibilityService() {
    private lateinit var prefs: DriverPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = DriverPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: "UNKNOWN"
        val nodes = linkedSetOf<String>()
        event.source?.let { traverse(it, nodes) }
        rootInActiveWindow?.let { traverse(it, nodes) }
        try {
            windows?.forEach { it.root?.let { r -> traverse(r, nodes) } }
        } catch (_: Exception) {}

        LoggerService.recordRawEventDump(this, prefs, pkg, nodes.toList())
    }

    private fun traverse(node: AccessibilityNodeInfo, results: MutableSet<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { results.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { try { traverse(it, results) } finally { it.recycle() } }
        }
    }

    override fun onInterrupt() {}
}
