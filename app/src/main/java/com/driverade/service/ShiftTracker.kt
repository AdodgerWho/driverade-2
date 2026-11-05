package com.driverade.service

import com.driverade.data.DriverPreferences
import java.util.Locale

object ShiftTracker {

    /**
     * Returns true if at least one target gig platform is enabled in preferences.
     */
    fun isAnyPlatformActive(prefs: DriverPreferences): Boolean {
        val platforms = listOf("UBER", "UBER_EATS", "LYFT", "HICH", "DOORDASH", "GRUBHUB")
        return platforms.any { prefs.isPlatformEnabled(it) }
    }

    /**
     * Calculates active shift hours formatted as "xx.x" (e.g. "03.5").
     */
    fun getFormattedShiftHours(prefs: DriverPreferences): String {
        val startMs = prefs.sessionStartMs
        val nowMs = System.currentTimeMillis()
        val elapsedMs = maxOf(0L, nowMs - startMs)
        
        val totalHours = elapsedMs / 3600000.0
        return String.format(Locale.US, "%.1f", totalHours)
    }

    fun resetShift(prefs: DriverPreferences) {
        prefs.sessionStartMs = System.currentTimeMillis()
    }
}