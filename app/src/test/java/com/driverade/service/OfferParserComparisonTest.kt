package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserComparisonTest {
    private val lyft = listOf(
        "Ride request", "Accept", "$18.50", "$37/hr est rate for this ride",
        "5 min • 1.0 mi", "25 min • 8.0 mi"
    )

    @Test fun completeLyftCandidateMatchesActiveNumbers() {
        val report = OfferParserComparison.compare("LYFT", lyft, true,
            OfferParserComparison.ActiveSnapshot(18.5, 30.0, 9.0, 37.0))
        assertEquals("MATCH", report.status)
        assertEquals(4, report.fields.count { it.state == "MATCH" })
        assertEquals(37.0, report.reportedEstimatedRate!!, 0.001)
        assertTrue(report.diagnostic("SYNTHETIC", "id", "none").contains("rawText=OMITTED"))
    }

    @Test fun mismatchedPayoutIsReportedWithoutChangingActiveValue() {
        val active = OfferParserComparison.ActiveSnapshot(20.0, 30.0, 9.0, 40.0)
        val report = OfferParserComparison.compare("LYFT", lyft, true, active)
        assertEquals("DIFF", report.status)
        assertEquals("DIFF", report.fields[0].state)
        assertEquals(20.0, active.payout, 0.001)
    }

    @Test fun uberReservationRemainsPartialWithoutPickupLeg() {
        val nodes = listOf("Trip request", "Accept • $24.50",
            "12 min • 3.2 mi", "18 min • 6.4 mi")
        val report = OfferParserComparison.compare("UBER", nodes, true,
            OfferParserComparison.ActiveSnapshot(24.50, 30.0, 9.6, 49.0))
        assertEquals("PARTIAL", report.status)
        assertEquals("MATCH", report.fields[0].state)
        assertEquals("UNKNOWN", report.fields[1].state)
        assertEquals(12.0, report.tripMinutes!!, 0.001)
    }

    @Test fun historyCannotBePromotedToOffer() {
        val nodes = listOf("Your earnings", "Trip", "Accept", "$101.83", "20 min", "4 mi")
        assertTrue(!OfferScreenClassifier.isActiveOffer(nodes))
        val report = OfferParserComparison.compare("UBER", nodes, false, null)
        assertEquals("GATE_REJECT", report.status)
    }

    @Test fun hichDoesNotClaimLegacyCoverage() {
        val report = OfferParserComparison.compare("HICH", listOf("Trip", "Accept"), true,
            OfferParserComparison.ActiveSnapshot(16.0, 24.0, 6.0, 40.0))
        assertEquals("NO_LEGACY_ADAPTER", report.status)
        assertNull(report.legacySource)
    }

    @Test fun missingLegacyCandidateDoesNotBecomeFakeSuccess() {
        val report = OfferParserComparison.compare("UBER", listOf("Trip request", "Accept", "$18.50"), true,
            OfferParserComparison.ActiveSnapshot(18.5, 25.0, 5.2, 44.4))
        assertEquals("NO_LEGACY_CANDIDATE", report.status)
        assertEquals("UNKNOWN", report.fields[0].state)
    }

    @Test fun lyftRateLabelDoesNotBecomePayout() {
        val candidate = LegacyOfferCandidates.lyft(listOf("$37/hr est rate for this ride"))
        assertEquals(37.0, candidate?.estimatedHourlyRate!!, 0.001)
        assertNull(candidate.payout)
        assertNull(candidate.calculatedHourlyRate())
    }
}
