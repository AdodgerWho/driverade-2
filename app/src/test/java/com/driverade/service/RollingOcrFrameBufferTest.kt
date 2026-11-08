package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingOcrFrameBufferTest {
    private fun frame(id: String, time: Long, shade: Int, size: Int = 120) =
        RollingOcrFrameBuffer.Frame(id, time, "WINDOW",
            ByteArray(size) { shade.toByte() }, IntArray(12) { shade })

    @Test fun promotesOnlyLastEightSeconds() {
        val ring = RollingOcrFrameBuffer()
        ring.add(frame("old", 1_000L, 5))
        ring.add(frame("within", 4_000L, 25))
        ring.add(frame("current", 9_500L, 65))
        val promoted = ring.promote(9_501L).map { it.captureId }
        assertEquals(listOf("within", "current"), promoted)
        assertEquals(0, ring.pendingCount)
    }

    @Test fun tenSecondPostrollPersistsNewFramesButNotLaterFrames() {
        val ring = RollingOcrFrameBuffer()
        ring.add(frame("before", 5_000L, 5))
        assertEquals(listOf("before"), ring.promote(10_000L).map { it.captureId })
        assertTrue(ring.postRollActive(20_000L))
        val after = ring.add(frame("post", 19_999L, 35))
        assertEquals(listOf("post"), after.toPersist.map { it.captureId })
        assertTrue(ring.add(frame("too-late", 20_001L, 65)).toPersist.isEmpty())
        assertEquals(1, ring.pendingCount)
        assertFalse(ring.postRollActive(20_001L))
    }

    @Test fun identicalFramesAreNeverSavedTwice() {
        val ring = RollingOcrFrameBuffer()
        assertFalse(ring.add(frame("first", 1_000L, 30)).duplicate)
        assertTrue(ring.add(frame("same", 2_000L, 30)).duplicate)
        assertEquals(listOf("first"), ring.promote(2_001L).map { it.captureId })
        assertTrue(ring.add(frame("same-post", 2_002L, 30)).toPersist.isEmpty())
    }

    @Test fun boundedByFrameCountAndMemory() {
        val ring = RollingOcrFrameBuffer(maxFrames = 2, maxBytes = 250)
        ring.add(frame("first", 1_000L, 1))
        ring.add(frame("second", 2_000L, 20))
        val result = ring.add(frame("third", 3_000L, 40))
        assertEquals(1, result.expiredFrames)
        assertEquals(2, ring.pendingCount)
        assertTrue(ring.pendingBytes <= 250)
        assertEquals(listOf("second", "third"), ring.promote(3_001L).map { it.captureId })
    }

    @Test fun optOutDiscardsPrivateMemoryAndPostroll() {
        val ring = RollingOcrFrameBuffer()
        ring.add(frame("private", 2_000L, 5))
        ring.promote(3_000L)
        ring.add(frame("post", 4_000L, 30))
        ring.clear()
        assertEquals(0, ring.pendingCount)
        assertEquals(0, ring.pendingBytes)
        assertFalse(ring.postRollActive(4_001L))
    }

    @Test fun expiresScreenshotsWithoutReceivingAnotherFrame() {
        val ring = RollingOcrFrameBuffer()
        ring.add(frame("transient", 1_000L, 20))
        assertEquals(1, ring.expire(9_001L))
        assertEquals(0, ring.pendingCount)
        assertEquals(0, ring.pendingBytes)
        assertEquals(null, ring.add(frame("new-session", 15_000L, 20)).meanDifference)
    }

    @Test fun visualDifferenceIsAvailableForOfferTrigger() {
        val ring = RollingOcrFrameBuffer()
        assertEquals(null, ring.add(frame("initial", 1_000L, 5)).meanDifference)
        assertEquals(35.0, ring.add(frame("new-overlay", 2_000L, 40)).meanDifference!!, 0.001)
    }
}
