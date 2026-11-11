package com.driverade.service

import org.junit.Assert.*
import org.junit.Test

class FailureTreeRulesTest {
    @Test fun classifiesKnownErrorsWithoutRawFields() {
        val x = FailureTreeRules.classify("RESTORED_TICK_ERROR", "reason=IllegalStateException")
        assertEquals("TIMER_EXCEPTION", x?.kind)
        assertEquals("acquisition", x?.stage)
        assertNull(FailureTreeRules.classify("PARSE_RESULT", "payout=99.00"))
        assertEquals("disabled_by_owner", FailureTreeRules.classify(
            "RESTORED_OCR_SKIPPED", "reason=UBER_DISABLED")?.cause)
    }
    @Test fun captureIdsAreBounded() {
        assertEquals("timed-123_456", FailureTreeRules.captureId("capture=timed-123_456"))
        assertNull(FailureTreeRules.captureId("source=TREE"))
    }
    @Test fun ordinaryParserRejectionIsNotAnAnomaly() {
        assertNull(FailureTreeRules.classify("RESTORED_TREE_PARSE", "reason=history_or_navigation"))
    }
}
