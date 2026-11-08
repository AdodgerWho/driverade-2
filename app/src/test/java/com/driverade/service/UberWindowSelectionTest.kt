package com.driverade.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UberWindowSelectionTest {
    @Test fun tinyUberPillDoesNotHideMainWindow() {
        val frames = listOf(UberWindowSelection.Frame(467, 223L * 223L),
            UberWindowSelection.Frame(360, 1440L * 3120L))
        assertEquals(360, UberWindowSelection.select(467, frames))
    }

    @Test fun significantEventWindowCanWinAndEmptyIsSafe() {
        val frames = listOf(UberWindowSelection.Frame(1, 1000),
            UberWindowSelection.Frame(2, 1800))
        assertEquals(1, UberWindowSelection.select(1, frames))
        assertEquals(2, UberWindowSelection.select(-1, frames))
        assertNull(UberWindowSelection.select(1, emptyList()))
    }
}
