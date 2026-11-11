package com.driverade.service

/** Observation-only categories; does not alter offer decisions. */
internal object FailureTreeRules {
    data class Finding(val stage: String, val kind: String, val cause: String, val severity: String)
    private val capture = Regex("""\bcapture=([A-Za-z0-9_-]{1,70})""")
    fun captureId(detail: String): String? = capture.find(detail)?.groupValues?.get(1)

    fun classify(category: String, detail: String): Finding? = when (category) {
        "RESTORED_TICK_ERROR" -> Finding("acquisition", "TIMER_EXCEPTION", "tick_exception", "ERROR")
        "RESTORED_WINDOW_ERROR", "UBER_ZERO_NODE_WINDOWS_ERROR", "WINDOW_VISIBILITY_ERROR" ->
            Finding("acquisition", "WINDOW_INSPECTION_FAILURE", category, "WARNING")
        "RESTORED_TREE_MISS" -> Finding("acquisition", "MISSING_ACCESSIBILITY_ROOT",
            if ("NO_OWNED_ROOT" in detail) "no_owned_root" else "no_text", "OBSERVATION")
        "RESTORED_OCR_SKIPPED" -> Finding("ocr", "OCR_NOT_PROCESSED",
            when {
                "EVENT_OLDER_THAN_8_SECONDS" in detail -> "stale_event"
                "PREFERENCES_NOT_READY" in detail -> "preferences_not_ready"
                "UBER_DISABLED" in detail -> "disabled_by_owner"
                else -> "other"
            }, "OBSERVATION")
        "OVERLAY_ERROR", "OVERLAY_FAIL" -> Finding("alert", "OVERLAY_FAILURE", category, "ERROR")
        "UBER_EARNINGS_DECREASE" -> Finding("earnings", "EARNINGS_RECONCILIATION", "decrease_needs_review", "WARNING")
        "VISUAL_DEBUG_EXPORT_FAILED" -> Finding("diagnostics", "EXPORT_FAILURE", "visual_export", "ERROR")
        "DEV_INSPECTION_ERROR" -> Finding("diagnostics", "CAPTURE_FAILURE", "developer_inspection", "ERROR")
        else -> null
    }
}
