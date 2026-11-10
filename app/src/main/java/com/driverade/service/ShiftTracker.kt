package com.driverade.service

import com.driverade.data.DriverPreferences
import java.util.Locale

object ShiftTracker {

    fun isAnyPlatformActive(prefs: DriverPreferences): Boolean {
        val platforms = listOf("UBER", "UBER_EATS", "LYFT", "HICH", "DOORDASH", "GRUBHUB")
        return platforms.any { prefs.isPlatformEnabled(it) }
    }

    fun getFormattedShiftHours(prefs: DriverPreferences): String {
        val startMs: Long = prefs.sessionStartMs
        val nowMs: Long = System.currentTimeMillis()
        val elapsedMs: Long = maxOf(0L, nowMs - startMs)
        
        val totalHours = elapsedMs.toDouble() / 3600000.0
        return String.format(Locale.US, "%.1f", totalHours)
    }

    fun resetShift(prefs: DriverPreferences) {
        prefs.sessionStartMs = System.currentTimeMillis()
    }
}
