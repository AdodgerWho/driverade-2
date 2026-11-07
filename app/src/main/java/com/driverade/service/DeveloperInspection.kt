package com.driverade.service

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Opt-in, short-lived, app-private capture of Uber accessibility hierarchy.
 * Never shared by normal diagnostic export; does not influence live offers.
 * Developer mode is deliberately NOT a production telemetry feature.
 */
internal object DeveloperInspection {
    private const val PREFS = "driverade_developer_inspection"
    private const val SESSION_MS = 30 * 60 * 1000L
    private const val EVENT_GAP_MS = 1200L
    private const val MAX_BYTES = 2_000_000L
    private const val MAX_NODES = 500
    private const val MAX_WINDOWS = 12
    private var lastEventAt = 0L
    private val lock = Any()

    fun file(context: Context) = File(context.filesDir, "developer_uber_inspection.jsonl")

    fun start(context: Context) {
        // A new session starts with a clean private trace; the user exports the old one explicitly.
        synchronized(lock) {
            file(context).delete()
            lastEventAt = 0L
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("expires_at", System.currentTimeMillis() + SESSION_MS).apply()
        }
        InternalDiagnosticsLogger.logInteraction(context, "DEV_INSPECTION_ENABLED",
            "scope=UBER sessionMinutes=30 rawText=PRIVATE_FILE export=EXPLICIT")
    }

    fun stop(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("expires_at").apply()
        InternalDiagnosticsLogger.logInteraction(context, "DEV_INSPECTION_DISABLED",
            "privateTraceRemainsUntilExplicitDeletionOrNextSession")
    }

    fun isActive(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expiry = p.getLong("expires_at", 0L)
        if (expiry <= 0L) return false
        if (System.currentTimeMillis() >= expiry) {
            p.edit().remove("expires_at").apply()
            InternalDiagnosticsLogger.logInteraction(context, "DEV_INSPECTION_EXPIRED", "sessionEnded=true")
            return false
        }
        return true
    }

    fun remainingMinutes(context: Context): Long {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("expires_at", 0L)
        return ((until - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
    }

    fun clear(context: Context): Boolean = synchronized(lock) { !file(context).exists() || file(context).delete() }

    fun observe(context: Context, event: AccessibilityEvent,
                windows: List<AccessibilityWindowInfo>, activeRoot: AccessibilityNodeInfo?) {
        if (!isActive(context)) return
        val elapsed = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (elapsed - lastEventAt < EVENT_GAP_MS) return
            lastEventAt = elapsed
            try {
                val record = JSONObject()
                    .put("timestamp", System.currentTimeMillis())
                    .put("eventPackage", event.packageName?.toString())
                    .put("eventType", event.eventType)
                    .put("eventWindowId", event.windowId)
                    .put("eventClass", event.className?.toString())
                val entries = JSONArray()
                val seen = HashSet<Int>()
                val eventSource = event.source
                if (eventSource != null && eventSource.packageName?.toString() == "com.ubercab.driver") {
                    entries.put(inspectRoot("eventSource", event.windowId, -1, -1, eventSource))
                    seen.add(event.windowId)
                } else {
                    record.put("eventSourceUnavailableOrNotUber", true)
                }
                for (w in windows.take(MAX_WINDOWS)) {
                    val root = try { w.root } catch (_: Exception) { null }
                    val outer = JSONObject()
                        .put("origin", "window")
                        .put("windowId", w.id)
                        .put("windowType", w.type)
                        .put("layer", w.layer)
                        .put("active", w.isActive)
                        .put("focused", w.isFocused)
                        .put("rootNull", root == null)
                    val b = Rect()
                    runCatching { w.getBoundsInScreen(b) }
                    outer.put("bounds", rectJson(b))
                    if (root != null) {
                        val pkg = root.packageName?.toString()
                        outer.put("package", pkg)
                        // Only dump raw content for the explicitly authorized Uber package.
                        if (pkg == "com.ubercab.driver" && !seen.contains(w.id)) {
                            outer.put("hierarchy", inspectRoot("window", w.id, w.type, w.layer, root))
                            seen.add(w.id)
                        }
                    }
                    entries.put(outer)
                }
                if (activeRoot != null) {
                    record.put("activeRootPackage", activeRoot.packageName?.toString())
                    if (activeRoot.packageName?.toString() == "com.ubercab.driver" &&
                        !seen.contains(activeRoot.windowId)) {
                        entries.put(inspectRoot("activeRoot", activeRoot.windowId, -1, -1, activeRoot))
                    }
                } else record.put("activeRootNull", true)
                record.put("windows", entries)
                val out = file(context)
                // Fixed upper bound: once full, stop capture instead of losing earlier evidence.
                if (out.length() + record.toString().length + 1 > MAX_BYTES) {
                    InternalDiagnosticsLogger.logInteraction(context, "DEV_INSPECTION_FULL",
                        "limitBytes=$MAX_BYTES recordsPreserved=true")
                    stop(context)
                    return
                }
                out.appendText(record.toString() + "\n")
                InternalDiagnosticsLogger.logInteraction(context, "DEV_INSPECTION_CAPTURE",
                    "eventWindow=${event.windowId} inspectedWindows=${windows.size.coerceAtMost(MAX_WINDOWS)} " +
                    "privateFileBytes=${out.length()} text=PRIVATE_ONLY")
            } catch (e: Exception) {
                InternalDiagnosticsLogger.logInteraction(context, "DEV_INSPECTION_ERROR",
                    "reason=${e.javaClass.simpleName}")
            }
        }
    }

    private fun inspectRoot(origin: String, windowId: Int, type: Int, layer: Int,
                            root: AccessibilityNodeInfo): JSONObject {
        val nodes = JSONArray()
        var count = 0
        fun walk(node: AccessibilityNodeInfo, parent: Int, depth: Int) {
            if (depth > 30 || count >= MAX_NODES) return
            val index = count++
            val b = Rect()
            runCatching { node.getBoundsInScreen(b) }
            val n = JSONObject()
                .put("index", index)
                .put("parent", parent)
                .put("depth", depth)
                .put("class", node.className?.toString())
                .put("viewId", node.viewIdResourceName)
                .put("package", node.packageName?.toString())
                .put("text", node.text?.toString()?.take(600))
                .put("description", node.contentDescription?.toString()?.take(600))
                .put("bounds", rectJson(b))
                .put("visible", node.isVisibleToUser)
                .put("enabled", node.isEnabled)
                .put("clickable", node.isClickable)
                .put("childCount", node.childCount)
            nodes.put(n)
            for (i in 0 until node.childCount.coerceAtMost(80)) {
                if (count >= MAX_NODES) break
                val child = runCatching { node.getChild(i) }.getOrNull()
                if (child != null) walk(child, index, depth + 1)
            }
        }
        walk(root, -1, 0)
        return JSONObject().put("origin", origin).put("windowId", windowId)
            .put("windowType", type).put("layer", layer)
            .put("nodeCount", count).put("truncated", count >= MAX_NODES)
            .put("nodes", nodes)
    }

    private fun rectJson(r: Rect): JSONArray =
        JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom)
}
