package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UberScreenOcrDiagnosticsTest {
    @Test fun exclusiveCardReportsBoundedUnverifiedCandidates() {
        val summary = UberScreenOcrDiagnostics.inspect(
            "UberX Exclusive\n\$5.53\n3 min 0.7 mi\n7 min 2.1 mi\nAccept"
        )
        assertTrue(summary.likelyOfferCard)
        assertTrue(summary.possibleExclusive)
        assertFalse(summary.possibleMatch)
        assertEquals(listOf(5.53), summary.amounts)
        assertEquals(listOf(3.0, 7.0), summary.legMinutes)
        assertEquals(listOf(0.7, 2.1), summary.legMiles)
        assertTrue(summary.diagnostic().contains("status=UNVERIFIED"))
    }

    @Test fun matchCardRecognizedButNotPromotedToOffer() {
        val summary = UberScreenOcrDiagnostics.inspect(
            "UberX Match\n\$6.74\n6 min 2.4 mi\n7 min 2.3 mi"
        )
        assertTrue(summary.likelyOfferCard)
        assertTrue(summary.possibleMatch)
        assertEquals(listOf(6.74), summary.amounts)
    }

    @Test fun unrelatedForegroundScreenDoesNotLogRawAmounts() {
        val summary = UberScreenOcrDiagnostics.inspect(
            "Messages\n\$73.52\n2 min 1 mi\nPrivate conversation"
        )
        assertFalse(summary.likelyOfferCard)
        assertTrue(summary.amounts.isEmpty())
        assertTrue(summary.diagnostic().contains("rawText=OMITTED"))
        assertFalse(summary.diagnostic().contains("73.52"))
    }

    @Test fun noValuesMeansNoOfferClassification() {
        val summary = UberScreenOcrDiagnostics.inspect("Uber Driver\nYou're online")
        assertFalse(summary.likelyOfferCard)
        assertEquals(emptyList<Double>(), summary.amounts)
    }


    @Test fun comfortElectricIsRecognizedAndRejectedCardsRemainObservable() {
        val card = UberScreenOcrDiagnostics.inspect(
            "Comfort Electric\n\$20.80\n5 min 1.2 mi\n20 min 7.5 mi\nAccept", "WINDOW")
        assertTrue(card.likelyOfferCard)
        assertEquals("COMFORT_ELECTRIC", card.serviceType)
        assertTrue(card.actionPresent)
        val preview = UberScreenOcrDiagnostics.inspect("Comfort Electric\n\$15.40\nView trip")
        assertFalse(preview.likelyOfferCard)
        assertEquals(1, preview.amountCandidateCount)
        assertTrue(preview.amounts.isEmpty())
        assertTrue(preview.diagnostic().contains("amountCount=1"))
    }

    @Test fun categoryUnknownCanOnlyBeConsideredInTrustedWindow() {
        val raw = "Future tier\n\$19.80\n5 min 1.2 mi\n12 min 5.1 mi\nAccept"
        assertTrue(UberScreenOcrDiagnostics.inspect(raw, "WINDOW").likelyOfferCard)
        assertFalse(UberScreenOcrDiagnostics.inspect(raw, "DISPLAY").likelyOfferCard)
    }

    @Test fun captureProvenanceReflectsImageSource() {
        val summary = UberScreenOcrDiagnostics.inspect("Uber Driver online")
        assertTrue(summary.diagnostic("WINDOW").contains("source=WINDOW_OCR"))
        assertTrue(summary.diagnostic("DISPLAY").contains("source=DISPLAY_OCR"))
        assertTrue(summary.diagnostic("DISPLAY_FALLBACK").contains("source=DISPLAY_OCR"))
        assertTrue(summary.diagnostic().contains("source=UNKNOWN_OCR"))
        assertTrue(summary.diagnostic("WINDOW").contains("rawText=OMITTED"))
    }
}
