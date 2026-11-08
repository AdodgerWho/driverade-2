package com.driverade.service

/**
 * Short-lived diagnostic frame selector. Frames stay in memory until a relevant
 * event promotes the preceding eight seconds and the next ten seconds.
 *
 * Pure Kotlin: deterministic timing, dedupe, and retention can be unit tested
 * without requiring Android screenshots or ML Kit.
 */
internal class RollingOcrFrameBuffer(
    private val preRollMs: Long = 8_000L,
    private val postRollMs: Long = 10_000L,
    private val maxFrames: Int = 10,
    private val maxBytes: Int = 8_000_000
) {
    data class Frame(
        val captureId: String,
        val timestampMs: Long,
        val source: String,
        val jpeg: ByteArray,
        val signature: IntArray
    )
    data class Added(
        val toPersist: List<Frame>,
        val duplicate: Boolean,
        val meanDifference: Double?,
        val expiredFrames: Int
    )

    private val pending = ArrayDeque<Frame>()
    private var bytesInMemory = 0
    private var previousSignature: IntArray? = null
    private var keepUntil = -1L

    val pendingCount: Int get() = pending.size
    val pendingBytes: Int get() = bytesInMemory
    fun postRollActive(nowMs: Long): Boolean = nowMs <= keepUntil

    fun add(frame: Frame): Added {
        val old = previousSignature
        val diff = if (old == null || old.size != frame.signature.size) null else
            old.indices.sumOf { kotlin.math.abs(old[it] - frame.signature[it]).toDouble() } / old.size
        previousSignature = frame.signature.copyOf()
        if (diff != null && diff < 2.0)
            return Added(emptyList(), true, diff, prune(frame.timestampMs))
        val expired = prune(frame.timestampMs)
        if (postRollActive(frame.timestampMs))
            return Added(listOf(frame), false, diff, expired)
        if (frame.jpeg.size > maxBytes)
            return Added(emptyList(), false, diff, expired)
        pending.addLast(frame)
        bytesInMemory += frame.jpeg.size
        var removed = expired
        while (pending.size > maxFrames || bytesInMemory > maxBytes) {
            bytesInMemory -= pending.removeFirst().jpeg.size
            removed++
        }
        return Added(emptyList(), false, diff, removed)
    }

    fun promote(nowMs: Long): List<Frame> {
        keepUntil = maxOf(keepUntil, nowMs + postRollMs)
        prune(nowMs)
        val result = pending.toList()
        pending.clear()
        bytesInMemory = 0
        return result
    }

    fun clear() {
        pending.clear()
        bytesInMemory = 0
        previousSignature = null
        keepUntil = -1L
    }

    private fun prune(nowMs: Long): Int {
        var removed = 0
        while (pending.isNotEmpty() && nowMs - pending.first().timestampMs > preRollMs) {
            bytesInMemory -= pending.removeFirst().jpeg.size
            removed++
        }
        return removed
    }
}
