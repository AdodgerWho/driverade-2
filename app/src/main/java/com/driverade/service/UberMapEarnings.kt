package com.driverade.service

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Passive, on-device observation of Uber's accumulated map earnings, NOT ride-offer payouts.
 * Strict screen context prevents turning unrelated dollar amounts into earned income.
 * No raw accessibility text, route, or rider information is persisted.
 *
 * Uber's observed daily reset is approximately 4 AM local. A decreasing reading within
 * the same earning day is held for review, not silently treated as a negative ride.
 */
internal object UberMapEarnings {
    private const val PREFS_NAME = "driverade_uber_map_earnings"
    private const val KEY_DAY = "day"
    private const val KEY_TOTAL_CENTS = "total_cents"
    private const val KEY_DECREASE_NOTED = "decrease_noted_cents"

    private val findingTrips = Regex("""(?i)\bfinding\s+trips\b""")
    private val activeOffer = Regex("""(?i)\b(?:accept|decline|match|exclusive|pickup|dropoff)\b""")
    private val totalLabel = Regex("""^\s*\$\s*(\d{1,4})(?:\.(\d{2}))?\s*[▾⌄˅∨▼]?\s*$""")

    /** Pure parser: only a single amount on the recognizable Uber map idle screen. */
    fun parseMapTotalCents(nodes: List<String>): Long? {
        val text = nodes.joinToString("\n")
        if (!findingTrips.containsMatchIn(text) || activeOffer.containsMatchIn(text)) return null
        val candidates = nodes.flatMap { it.lines() }.mapNotNull { line ->
            val match = totalLabel.matchEntire(line) ?: return@mapNotNull null
            val dollars = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val cents = match.groupValues[2].ifEmpty { "00" }.toLongOrNull() ?: return@mapNotNull null
            dollars * 100L + cents
        }.distinct()
        return candidates.singleOrNull()
    }

    private fun earningDay(): String {
        val clock = Calendar.getInstance()
        clock.add(Calendar.HOUR_OF_DAY, -4)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(clock.time)
    }

    /** Null means no trustworthy observation for the current earning day. */
    fun totalToday(context: Context): Double? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DAY, null) != earningDay() ||
            !prefs.contains(KEY_TOTAL_CENTS)) return null
        return prefs.getLong(KEY_TOTAL_CENTS, 0L) / 100.0
    }

    /** Manual correction removes the previously observed override until another fresh observation. */
    fun clearToday(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_TOTAL_CENTS).remove(KEY_DAY).remove(KEY_DECREASE_NOTED).apply()
    }

    /**
     * Call only for an Uber-owned window or an Uber-triggered OCR capture.
     * Returns true only when a new validated daily total was persisted.
     */
    @Synchronized
    fun observe(context: Context, nodes: List<String>, source: String): Boolean {
        val cents = parseMapTotalCents(nodes) ?: return false
        val day = earningDay()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sameDay = prefs.getString(KEY_DAY, null) == day
        val previous = if (sameDay && prefs.contains(KEY_TOTAL_CENTS))
            prefs.getLong(KEY_TOTAL_CENTS, 0L) else null
        if (previous == cents) return false
        if (previous != null && cents < previous) {
            if (prefs.getLong(KEY_DECREASE_NOTED, -1L) != cents) {
                prefs.edit().putLong(KEY_DECREASE_NOTED, cents).apply()
                InternalDiagnosticsLogger.logInteraction(context, "UBER_EARNINGS_DECREASE",
                    "source=" + source + " previousCents=" + previous +
                        " candidateCents=" + cents + " action=REVIEW noRawText=true")
            }
            return false
        }
        prefs.edit().putString(KEY_DAY, day).putLong(KEY_TOTAL_CENTS, cents)
            .remove(KEY_DECREASE_NOTED).apply()
        InternalDiagnosticsLogger.logInteraction(context, "UBER_MAP_EARNINGS",
            "source=" + source + " day=" + day + " amountCents=" + cents +
                " previousCents=" + (previous ?: "NONE") + " rawText=OMITTED")
        return true
    }

    const val PREFERENCES_NAME = PREFS_NAME
}