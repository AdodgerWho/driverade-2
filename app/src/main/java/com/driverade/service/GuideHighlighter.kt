package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale

/** Highlights a matched accessibility node. No clicks, gestures or screen uploads. */
class GuideHighlighter(private val service: AccessibilityService) {
    private val wm = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private var marker: TextView? = null
    private var lastBounds: Rect? = null

    fun inspect(root: AccessibilityNodeInfo?, step: Int) {
        val targets = when (step) {
            1 -> listOf("version")
            2 -> listOf("developer settings", "developer options")
            3 -> listOf("wireless android auto")
            else -> emptyList()
        }
        if (root == null || targets.isEmpty() || !Settings.canDrawOverlays(service)) {
            clear()
            return
        }
        var found: Rect? = null
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 28 || found != null) return
            val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .joinToString(" ").lowercase(Locale.ROOT)
            if (targets.any { label.contains(it) }) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) found = bounds
            }
            for (i in 0 until node.childCount) {
                walk(runCatching { node.getChild(i) }.getOrNull(), depth + 1)
            }
        }
        walk(root, 0)
        val bounds = found ?: run { clear(); return }
        if (bounds == lastBounds) return
        clear()
        val view = TextView(service).apply {
            text = "◎"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.YELLOW)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(5, Color.YELLOW)
                cornerRadius = 20f
            }
        }
        val params = WindowManager.LayoutParams(
            bounds.width().coerceAtLeast(48),
            bounds.height().coerceAtLeast(48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = bounds.left
            y = bounds.top
        }
        runCatching { wm.addView(view, params) }.onSuccess {
            marker = view
            lastBounds = Rect(bounds)
        }
    }

    fun clear() {
        marker?.let { runCatching { wm.removeView(it) } }
        marker = null
        lastBounds = null
    }
}
