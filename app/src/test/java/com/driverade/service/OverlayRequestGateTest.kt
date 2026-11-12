package com.driverade.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRequestGateTest {
    @Test fun olderOfferTimeoutCannotDismissReplacement() {
        val gate = OverlayRequestGate()
        val first = gate.begin()
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test fun teardownInvalidatesPendingOverlayRequests() {
        val gate = OverlayRequestGate()
        val pending = gate.begin()
        gate.invalidate()
        assertFalse(gate.isCurrent(pending))
    }
}
