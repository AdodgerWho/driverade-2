package com.driverade.service

/**
 * One pending inspection at a fixed deadline from the first qualifying event.
 * Additional events do not postpone the deadline. Pure Kotlin for deterministic tests.
 */
internal class LeadingEdgeCaptureGate(val delayMs: Long = 200L) {
    private var startedAt: Long? = null
    val pending: Boolean get() = startedAt != null

    fun start(nowMs: Long): Boolean {
        if (pending) return false
        startedAt = nowMs
        return true
    }

    fun finish(nowMs: Long): Long? {
        val start = startedAt ?: return null
        startedAt = null
        return (nowMs - start).coerceAtLeast(0L)
    }

    fun cancel() {
        startedAt = null
    }
}
