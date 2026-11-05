package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale

/** Read-only, user-operated guidance. No taps or permission changes. */
class GuideHighlighter(private val service: AccessibilityService) {
    private val wm = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private var marker: TextView? = null
    private var lastBounds: Rect? = null
    private var lastResult = ""

    fun inspect(root: AccessibilityNodeInfo?, guide: String, step: Int): Boolean {
        val targets = when (guide) {
            "accessibility" -> when (step) {
                0 -> listOf("accessibility")
                1 -> listOf("installed apps", "downloaded apps", "installed services")
                2 -> listOf("driverade", "use driverade")
                else -> emptyList()
            }
            else -> when (step) {
                1 -> listOf("version")
                2 -> listOf("developer settings", "developer options")
                3 -> listOf("wireless android auto")
                else -> emptyList()
            }
        }
        if (root == null || targets.isEmpty()) {
            clear()
            return false
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
        val bounds = found
        val result = "$guide:$step:${bounds != null}"
        if (result != lastResult) {
            lastResult = result
            InternalDiagnosticsLogger.logInteraction(service, if (bounds != null) "TARGET_FOUND" else "TARGET_NOT_FOUND", "Guide=$guide step=$step")
        }
        if (bounds == null || !Settings.canDrawOverlays(service) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            clear()
            return bounds != null
        }
        if (bounds == lastBounds) return true
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = bounds.left
            y = bounds.top
        }
        runCatching { wm.addView(view, params) }.onSuccess {
            marker = view
            lastBounds = Rect(bounds)
            InternalDiagnosticsLogger.logInteraction(service, "HIGHLIGHT_SHOWN", "Guide=$guide step=$step")
        }.onFailure {
            InternalDiagnosticsLogger.logInteraction(service, "HIGHLIGHT_FAILED", "Guide=$guide step=$step")
        }
        return true
    }

    fun clear() {
        marker?.let { runCatching { wm.removeView(it) } }
        marker = null
        lastBounds = null
    }
}
