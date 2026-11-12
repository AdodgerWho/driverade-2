package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingEdgeCaptureGateTest {
    @Test fun firstEventWinsWithoutDebouncingSubsequentEvents() {
        val gate = LeadingEdgeCaptureGate()
        assertTrue(gate.start(1_000L))
        assertFalse(gate.start(1_050L))
        assertFalse(gate.start(1_190L))
        assertEquals(200L, gate.delayMs)
        assertEquals(205L, gate.finish(1_205L))
        assertFalse(gate.pending)
        assertTrue(gate.start(1_300L))
    }

    @Test fun canceledGateNeverFires() {
        val gate = LeadingEdgeCaptureGate()
        gate.start(5_000L)
        gate.cancel()
        assertNull(gate.finish(5_200L))
        assertFalse(gate.pending)
    }
}
