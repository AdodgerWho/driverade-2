package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoredOfferParserTest {
    private fun expected(c: RestoredOfferParser.Candidate?, price: Double, min: Double, mi: Double) {
        requireNotNull(c)
        assertEquals(price, c.payout, 0.001)
        assertEquals(min, c.totalMinutes, 0.001)
        assertEquals(mi, c.totalMiles, 0.001)
    }

    @Test fun realUberExclusiveOcrScreenshot() {
        val o = RestoredOfferParser.fromUberOcr(
            "Foreground chat 99 min, $111.00\nUberX Exclusive\n$5.53\n4.94 Verified\n" +
            "5% Pro Perk included\n3 min • 0.7 mi\nPillsbury Ave\n" +
            "7 mins • 2.1 mi\nAccept").offer
        expected(o, 5.53, 10.0, 2.8)
        assertEquals("live", o?.kind)
        assertEquals(RestoredOfferParser.Source.OCR, o?.source)
        assertEquals(33.18, o!!.hourlyRate, 0.01)
    }

    @Test fun realUberMatchOcrScreenshot() {
        val o = RestoredOfferParser.fromUberOcr(
            "Another app\nUberX\n$6.74\n4.90 Verified\n5% Pro Perk included\n" +
            "6 min • 2.4 mi\nBeard Ave\n7 mins • 2.3 mi\nMatch").offer
        expected(o, 6.74, 13.0, 4.7)
        assertEquals("match", o?.kind)
        assertEquals(31.1076, o!!.hourlyRate, 0.01)
    }


    @Test fun comfortElectricAndElectricCardsUseStandardizedMetrics() {
        val cases = listOf(
            Triple("Comfort\n\$20.80\n5 min • 1.2 mi\n20 min • 7.5 mi\nAccept", 20.8, "COMFORT"),
            Triple("Electric\n\$18.95\n4 min • 1.0 mi\n14 min • 5.6 mi\nAccept", 18.95, "ELECTRIC"),
            Triple("Comfort Electric\n\$23.40\n6 min • 1.5 mi\n14 min • 4.0 mi\nMatch", 23.4, "COMFORT_ELECTRIC")
        )
        cases.forEach { (card, payout, service) ->
            val offer = RestoredOfferParser.fromUberOcr(card).offer
            requireNotNull(offer)
            assertEquals(payout, offer.payout, 0.001)
            assertEquals(service, offer.serviceType)
            assertTrue(offer.totalMinutes > 0)
            assertTrue(offer.totalMiles > 0)
        }
    }

    @Test fun unknownFutureServiceAcceptedOnlyFromTrustedUberWindow() {
        val raw = "Future tier\n\$21.25\n3 min • 0.8 mi\n12 min • 4.2 mi\nAccept"
        val r = RestoredOfferParser.fromUberOcr(raw, "WINDOW")
        expected(r.offer, 21.25, 15.0, 5.0)
        assertNull(r.offer?.serviceType)
        assertEquals("ocr_no_service_context", RestoredOfferParser.fromUberOcr(raw, "DISPLAY").reason)
    }

    @Test fun reservationPreviewAndMapPillDoNotBecomeOffers() {
        val preview = "Reservation request\nComfort Electric\n\$15.40\nPickup Fri at 6:30pm\nView trip\nDismiss"
        assertNull(RestoredOfferParser.fromUberOcr(preview, "WINDOW").offer)
        assertNull(RestoredOfferParser.fromUberOcr(
            "Comfort Electric\n\$4.36\nPicking up\nStart navigation", "WINDOW").offer)
    }

    @Test fun largerUnrelatedAmountBeforeCardIsIgnored() {
        val r = RestoredOfferParser.fromUberOcr(
            "Other app balance \$73.52\nComfort Electric\n\$20.80\n5 min • 1.2 mi\n20 min • 7.5 mi\nAccept")
        expected(r.offer, 20.80, 25.0, 8.7)
    }

    @Test fun ambiguousAmountsWithinOfferRemainRejected() {
        val r = RestoredOfferParser.fromUberOcr(
            "Comfort Electric\n\$20.80\n\$28.00\n5 min • 1.2 mi\n20 min • 7.5 mi\nAccept", "WINDOW")
        assertNull(r.offer)
        assertEquals("ambiguous_or_missing_payout", r.reason)
    }

    @Test fun historicalUberAcceptPriceBeatsUnrelatedLargerNumber() {
        val o = RestoredOfferParser.fromTree("UBER", listOf(
            "Trip request", "Accept • $12.04", "$13.08",
            "6 min • 2.0 mi", "7 min • 1.3 mi")).offer
        expected(o, 12.04, 13.0, 3.3)
    }

    @Test fun historicalUberBindingOverridesOtherAmountsOnlyInTree() {
        val o = RestoredOfferParser.fromTree("UBER", listOf(
            "Trip request", "$12.04", "$13.08", "Accept",
            "6 min • 2.0 mi", "7 min • 1.3 mi"), 12.04).offer
        expected(o, 12.04, 13.0, 3.3)
    }

    @Test fun lyftRateNotConfusedWithActualPayout() {
        val o = RestoredOfferParser.fromTree("LYFT", listOf(
            "Ride request", "Accept", "$18.50", "$37/hr est rate for this ride",
            "5 min • 1.0 mi", "25 min • 8.0 mi")).offer
        expected(o, 18.50, 30.0, 9.0)
    }

    @Test fun hichAwayAndTripAreDistinct() {
        val o = RestoredOfferParser.fromTree("HICH", listOf(
            "Ride request", "ACCEPT", "$16.00",
            "5 mins (1.0 mi) away", "19 mins (5.0 mi) trip")).offer
        expected(o, 16.0, 24.0, 6.0)
    }

    @Test fun missingLegIsRejected() {
        val r = RestoredOfferParser.fromUberOcr(
            "UberX\n$6.74\n6 min • 2.4 mi\nMatch")
        assertNull(r.offer)
        assertEquals("requires_two_pickup_and_trip_legs", r.reason)
    }

    @Test fun unrelatedForegroundAmountsCannotCreateUberOffer() {
        assertNull(RestoredOfferParser.fromUberOcr(
            "Driverade earnings $73.52\n6 min • 2.4 mi\n7 min • 2.3 mi\nMatch").offer)
    }

    @Test fun earningsAndMapPillsAreRejected() {
        assertNull(RestoredOfferParser.fromTree("UBER", listOf(
            "Your earnings", "Accept", "$101.83", "6 min • 2 mi", "7 min • 2 mi")).offer)
        assertNull(RestoredOfferParser.fromTree("UBER", listOf(
            "Trip in progress", "$12", "6 min • 2 mi", "7 min • 2 mi")).offer)
    }

    @Test fun multipleUnboundAmountsAreRejectedInsteadOfPickingLargest() {
        val r = RestoredOfferParser.fromTree("UBER", listOf(
            "Trip request", "Accept", "$12.04", "$13.08",
            "6 min • 2 mi", "7 min • 3 mi"))
        assertNull(r.offer)
        assertEquals("ambiguous_or_missing_payout", r.reason)
    }

    @Test fun outputsDoNotIncludeRawScreenText() {
        val r = RestoredOfferParser.fromUberOcr(
            "UberX\n$6.74\n6 min • 2.4 mi\nVery Private Rider Address\n7 min • 2.3 mi\nMatch")
        assertTrue(r.offer != null)
        assertTrue(!r.diagnostic().contains("Private Rider"))
        assertTrue(r.diagnostic().contains("rawText=OMITTED"))
    }
}
