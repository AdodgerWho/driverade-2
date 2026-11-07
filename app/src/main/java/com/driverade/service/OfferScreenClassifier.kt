package com.driverade.service

import java.util.Locale

/**
 * Conservative gate: only active, actionable offer cards may reach payout parsing.
 * Earnings history, wallet and map headers are never offers, even when they contain money.
 */
internal object OfferScreenClassifier {
    private val action = Regex("""\b(accept|decline|reject)\b""", RegexOption.IGNORE_CASE)
    private val trip = Regex("""\b(ride|trip|pickup|pick up|dropoff|drop off|delivery|request)\b""", RegexOption.IGNORE_CASE)
    private val time = Regex("""\b\d{1,3}\s*(?:min|mins|minutes)\b|\b\d+\s*hr\s*\d+\s*min\b""", RegexOption.IGNORE_CASE)
    private val distance = Regex("""\b\d+(?:\.\d+)?\s*(?:mi|mile|miles)\b""", RegexOption.IGNORE_CASE)
    private val money = Regex("""\$\s*\d+(?:\.\d{1,2})?""")
    private val history = listOf(
        "your earnings", "earnings activity", "daily stats", "net fare",
        "total earnings", "cash out", "your balance", "balance",
        "see all daily activity", "ride history", "breakdown"
    )

    fun rejectionReason(texts: List<String>): String {
        val screen = texts.joinToString(" ").lowercase(Locale.US)
        if (history.any { screen.contains(it) }) return "history_screen"
        val missing = mutableListOf<String>()
        if (!action.containsMatchIn(screen)) missing.add("action")
        if (!trip.containsMatchIn(screen)) missing.add("trip")
        if (!money.containsMatchIn(screen)) missing.add("payout")
        if (!time.containsMatchIn(screen)) missing.add("duration")
        if (!distance.containsMatchIn(screen)) missing.add("distance")
        return if (missing.isEmpty()) "none" else "missing_" + missing.joinToString("_")
    }

    fun isActiveOffer(texts: List<String>): Boolean {
        val screen = texts.joinToString(" ").lowercase(Locale.US)
        // Historical screens may contain 'accept' in unrelated content.
        if (history.any { screen.contains(it) }) return false
        return action.containsMatchIn(screen) &&
            trip.containsMatchIn(screen) &&
            money.containsMatchIn(screen) &&
            time.containsMatchIn(screen) &&
            distance.containsMatchIn(screen)
    }
}
