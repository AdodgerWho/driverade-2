package com.driverade.service

/**
 * Monotonic identity for overlay requests. A delayed callback must not remove a
 * newer offer's overlay; invalidating also blocks posts after service teardown.
 */
internal class OverlayRequestGate {
    private var latest = 0L

    fun begin(): Long = ++latest

    fun isCurrent(requestId: Long): Boolean = requestId == latest

    fun invalidate() { latest++ }
}
