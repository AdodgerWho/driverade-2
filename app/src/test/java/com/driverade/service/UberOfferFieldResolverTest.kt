package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UberOfferFieldResolverTest {
    private fun node(text: String, id: String? = null, window: Int = 7, visible: Boolean = true) =
        UberOfferFieldResolver.Node(text, id, window, visible)

    @Test fun historicalReservationPayoutIgnoresLargerNotificationAmount() {
        val result = UberOfferFieldResolver.resolve(listOf(
            node("$12.04", "com.ubercab.driver:id/ub__upfront_offer_header_auditable_title"),
            node("Accept • $12.04", "com.ubercab.driver:id/upfront_offer_configurable_details_auditable_accept_button"),
            node("13 min (3.3 mi) trip", "com.ubercab.driver:id/ub__upfront_offer_map_label"),
            node("$13.08", "unrelated_reservation_notification"),
            node("$73.52", "earnings_map_balance")
        ))
        assertEquals(12.04, result.payout!!, 0.001)
        assertEquals("ACCEPT_CONTROL", result.source)
        assertEquals(13.0, result.tripMinutes!!, 0.001)
        assertEquals(3.3, result.tripMiles!!, 0.001)
        assertTrue(result.diagnostic().contains("rawText=OMITTED"))
    }

    @Test fun unrelatedWalletAndMapFieldsNeverBecomePayout() {
        val result = UberOfferFieldResolver.resolve(listOf(
            node("$73.52", "map_balance"),
            node("Start navigation"),
            node("2 min"),
            node("1 mi")))
        assertNull(result.payout)
        assertEquals("NO_BOUND_PAYOUT", result.source)
    }

    @Test fun mismatchedHeaderAndAcceptIsAmbiguous() {
        val result = UberOfferFieldResolver.resolve(listOf(
            node("$12.04", "ub__upfront_offer_header_auditable_title"),
            node("Accept • $13.08", "upfront_offer_configurable_details_auditable_accept_button")))
        assertNull(result.payout)
        assertEquals("CONFLICTING_OFFER_FIELDS", result.source)
    }

    @Test fun neverMixOfferAcrossWindows() {
        val result = UberOfferFieldResolver.resolve(listOf(
            node("Accept • $12.04", window = 7),
            node("Accept • $25.00", window = 8)))
        assertNull(result.payout)
        assertEquals("AMBIGUOUS_WINDOWS", result.source)
    }

    @Test fun invisibleOfferIgnored() {
        val result = UberOfferFieldResolver.resolve(listOf(
            node("Accept • $99.00", visible = false),
            node("$15.00", "map_balance")))
        assertNull(result.payout)
    }

    @Test fun splitAcceptWithoutBoundIdNotAssumed() {
        val result = UberOfferFieldResolver.resolve(listOf(
            node("Accept"), node("$18.50"), node("$55.00", "wallet")))
        assertNull(result.payout)
    }
}
