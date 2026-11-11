package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UberMapEarningsTest {
    @Test fun readsUniqueMapHeaderWithoutCountingOffers() {
        assertEquals(4245L, UberMapEarnings.parseMapTotalCents(
            listOf("Uber", "$42.45", "Finding trips", "Map")))
        assertEquals(4245L, UberMapEarnings.parseMapTotalCents(
            listOf("Finding trips\n$42.45\n▼")))
    }

    @Test fun rejectsOffersAndOtherScreens() {
        assertNull(UberMapEarnings.parseMapTotalCents(
            listOf("Finding trips", "$42.45", "UberX Match", "Accept")))
        assertNull(UberMapEarnings.parseMapTotalCents(
            listOf("Earnings Activity", "$42.45")))
        assertNull(UberMapEarnings.parseMapTotalCents(
            listOf("Finding trips", "$10.00", "$42.45")))
        assertNull(UberMapEarnings.parseMapTotalCents(
            listOf("Finding trips", "Trip payout $42.45")))
        assertNull(UberMapEarnings.parseMapTotalCents(
            listOf("$42.45")))
    }

    @Test fun zeroValueIsAnExplicitValidObservation() {
        assertEquals(0L, UberMapEarnings.parseMapTotalCents(
            listOf("$0.00", "Finding trips")))
    }
}