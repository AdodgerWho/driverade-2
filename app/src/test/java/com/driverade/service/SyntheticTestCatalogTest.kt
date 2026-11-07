package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticTestCatalogTest {
    @Test fun catalogHasValidAndNegativeCasesAcrossEnabledRidesharePlatforms() {
        val cases = SyntheticTestCatalog.presets
        assertTrue(cases.size >= 8)
        assertEquals(setOf("UBER", "LYFT", "HICH"), cases.map { it.platform }.toSet())
        assertTrue(cases.any { it.expectedActiveStatus == "GATE_REJECT" })
        assertTrue(cases.any { it.expectedProvenance == "ACCEPT_CONTROL" })
        assertTrue(cases.all { it.nodes.isNotBlank() && it.nodes.length <= 6000 })
    }

    @Test fun comparisonSummaryChecksBothStatusAndFieldBinding() {
        val test = SyntheticTestCatalog.presets.first { it.expectedProvenance == "ACCEPT_CONTROL" }
        val correct = SyntheticTestCatalog.Outcome(test, "PASS", "ACCEPT_CONTROL", "details")
        val wrongBinding = SyntheticTestCatalog.Outcome(test, "PASS", "NO_BOUND_PAYOUT", "details")
        assertTrue(correct.meetsExpectation)
        assertFalse(wrongBinding.meetsExpectation)
        val summary = SyntheticTestCatalog.summary(listOf(correct, wrongBinding), 2)
        assertTrue(summary.contains("1/2 expectations met"))
        assertTrue(summary.contains("CHECK"))
        assertFalse(summary.contains("details"))
    }

    @Test fun noTelemetryImpliedByBatchSummary() {
        val r = SyntheticTestCatalog.summary(emptyList(), 9)
        assertTrue(r.contains("9 not run"))
        assertTrue(r.contains("SYNTHETIC ONLY"))
    }
}
