package com.driverade.service

import java.util.Locale

/**
 * Driverlogger3_4 Offers.java port: tree for Lyft/Hich/Uber reservations,
 * screenshot OCR for visually-rendered Uber live offers. All inputs are
 * bounded, source-labelled and rejected if incomplete or ambiguous.
 */
internal object RestoredOfferParser {
    enum class Source { TREE, OCR }
    data class Candidate(
        val platform: String, val kind: String, val payout: Double,
        val pickupMinutes: Double, val pickupMiles: Double,
        val tripMinutes: Double, val tripMiles: Double, val source: Source,
        val serviceType: String? = null
    ) {
        val totalMinutes get() = pickupMinutes + tripMinutes
        val totalMiles get() = pickupMiles + tripMiles
        val hourlyRate get() = payout * 60.0 / totalMinutes
        val dollarsPerMile get() = payout / totalMiles
    }
    data class Result(val offer: Candidate?, val reason: String) {
        fun diagnostic(): String {
            val o = offer ?: return "decision=REJECT reason=" + reason + " rawText=OMITTED"
            return "decision=ACCEPT source=" + o.source + " kind=" + o.kind +
                " serviceType=" + (o.serviceType ?: "UNKNOWN") +
                " payout=" + o.payout + " pickupMin=" + o.pickupMinutes +
                " pickupMi=" + o.pickupMiles + " tripMin=" + o.tripMinutes +
                " tripMi=" + o.tripMiles + " hourly=" +
                String.format(Locale.US, "%.2f", o.hourlyRate) + " rawText=OMITTED"
        }
    }
    private val money = Regex("""\$\s*(\d{1,4}(?:\.\d{1,2})?)""")
    private val acceptPrice = Regex("""\bAccept\b[^\$\r\n]{0,14}\$\s*(\d{1,4}(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val leg = Regex(
        """\b(\d{1,3})\s*(?:min|mins|minutes)\s*[•·,|\-]?\s*(\d{1,3}(?:\.\d+)?)\s*(?:mi|mile|miles)\b""",
        RegexOption.IGNORE_CASE
    )
    private val hichAway = Regex(
        """(\d{1,3})\s*mins?\s*\(\s*(\d+(?:\.\d+)?)\s*mi\s*\)\s*away""",
        RegexOption.IGNORE_CASE
    )
    private val hichTrip = Regex(
        """(\d{1,3})\s*mins?\s*\(\s*(\d+(?:\.\d+)?)\s*mi\s*\)\s*trip""",
        RegexOption.IGNORE_CASE
    )
    private val history = listOf("your earnings", "earnings activity", "net fare",
        "total earnings", "your balance", "cash out", "ride history", "daily stats",
        "trip in progress", "start navigation", "picking up", "waiting for rider",
        "my reservations", "your reservations", "reservation details", "upcoming reservations",
        "cancel reservation", "edit reservation", "view reservation")
    // A scheduled offer must have both reservation context AND an actionable control.
    // A service label (e.g. "Uber Reserve") is context, not an action button.
    private val reservationContext = Regex(
        """\b(?:reservation|scheduled|advance booking|reserve ride|uber reserve)\b""",
        RegexOption.IGNORE_CASE
    )
    private val reserveButton = Regex(
        """(?im)^\s*(?:reserve(?:\s+(?:ride|now))?|book(?:\s+now)?)\s*$"""
    )
    private val uberAction = Regex("""\b(accept|match)\b""", RegexOption.IGNORE_CASE)
    private val lyftAction = Regex("""\b(accept|decline|request)\b""", RegexOption.IGNORE_CASE)
    // Ride service labels help scope OCR, but an UberX label is never an offer requirement.
    // Longer variants come first so Comfort Electric stays one category.
    private val uberCardLabel = Regex(
        """(?im)^[ \t]*(?:\d[ \t]+)?(?:Comfort[ \t]+Electric|Uber[ \t]+Comfort(?:[ \t]+Electric)?|Comfort|Electric|Uber[ \t]*XL|Uber[ \t]*X(?:[ \t]+Share)?|Uber[ \t]+Black(?:[ \t]+SUV)?|Black(?:[ \t]+SUV)?|Uber[ \t]+Green|Green|Uber[ \t]+Pet|Uber[ \t]+Share|Uber[ \t]+Reserve|Uber[ \t]+Premier|Premier|Uber[ \t]+WAV|Uber[ \t]+Assist)(?=[ \t]*(?:$|Exclusive\b|Match\b))"""
    )
    internal fun uberServiceType(text: String): String? =
        uberCardLabel.findAll(text).lastOrNull()?.value
            ?.trim()?.replace(Regex("""^\d[ \t]+"""), "")
            ?.replace(Regex("""\s+"""), "_")?.uppercase(Locale.US)

    /** Call only with one verified application-owned window at a time. */
    fun fromTree(platform: String, texts: List<String>, verifiedPayout: Double? = null): Result =
        parse(platform, texts.joinToString("\n"), Source.TREE, verifiedPayout)

    /** Known ride labels delimit the card; trusted Uber windows can accept future tiers.
     * Mixed-app display screenshots require a recognized service label for safety.
     * Every path still requires action, unique payout and two complete trip legs.
     */
    fun fromUberOcr(text: String, captureSource: String = "DISPLAY"): Result {
        val match = uberCardLabel.findAll(text).lastOrNull()
        if (match == null && captureSource != "WINDOW")
            return Result(null, "ocr_no_service_context")
        val section = if (captureSource == "WINDOW") text.takeLast(1700)
            else if (match != null) text.substring(match.range.first).take(1700)
            else text.takeLast(1700)
        return parse("UBER", section, Source.OCR, null)
    }

    private fun parse(platform: String, raw: String, source: Source, verifiedPayout: Double?): Result {
        val text = raw.trim()
        val lower = text.lowercase(Locale.US)
        if (history.any { it in lower }) return Result(null, "history_or_navigation")
        val scheduled = reservationContext.containsMatchIn(text)
        val hasReserveButton = reserveButton.containsMatchIn(text)
        val kind = when (platform) {
            "UBER", "UBER_EATS" -> {
                val hasAction = uberAction.containsMatchIn(text)
                if (!hasAction && !(scheduled && hasReserveButton))
                    return Result(null, "missing_uber_action")
                when {
                    scheduled -> "reservation"
                    Regex("""\bmatch\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "match"
                    else -> "live"
                }
            }
            "LYFT" -> {
                val hasAction = lyftAction.containsMatchIn(text)
                if (!hasAction && !(scheduled && hasReserveButton))
                    return Result(null, "missing_lyft_action")
                if (scheduled) "reservation" else "live"
            }
            "HICH" -> {
                if (!Regex("""\baccept\b""", RegexOption.IGNORE_CASE).containsMatchIn(text))
                    return Result(null, "missing_hich_action")
                if ("scheduled" in lower) "scheduled" else "live"
            }
            else -> return Result(null, "unsupported_platform")
        }
        val payout = if (verifiedPayout != null && platform.startsWith("UBER") && source == Source.TREE) {
            verifiedPayout
        } else if (platform.startsWith("UBER") && acceptPrice.containsMatchIn(text)) {
            acceptPrice.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: return Result(null, "unreadable_accept_price")
        } else {
            val found = money.findAll(text).filterNot {
                val following = text.substring(it.range.last + 1).take(8)
                val restOfLine = text.substring(it.range.last + 1).substringBefore('\n')
                Regex("""^\s*/\s*hr""", RegexOption.IGNORE_CASE).containsMatchIn(following) ||
                    (source == Source.OCR && platform.startsWith("UBER") &&
                        Regex("""^\s*Flash\s+Incentive\s+included\b""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(restOfLine))
            }.mapNotNull { it.groupValues[1].toDoubleOrNull() }
                .filter { it in 2.0..500.0 }.distinct().toList()
            if (found.size != 1) return Result(null, "ambiguous_or_missing_payout")
            found.single()
        }
        if (payout == null || !payout.isFinite() || payout !in 2.0..500.0)
            return Result(null, "payout_out_of_range")
        val pairs = if (platform == "HICH") {
            val a = hichAway.find(text)
            val t = hichTrip.find(text)
            if (a != null && t != null) listOf(a, t) else leg.findAll(text).take(3).toList()
        } else leg.findAll(text).take(3).toList()
        if (pairs.size != 2) return Result(null, "requires_two_pickup_and_trip_legs")
        val pMin = pairs[0].groupValues[1].toDoubleOrNull() ?: return Result(null, "pickup_minutes_unreadable")
        val pMi = pairs[0].groupValues[2].toDoubleOrNull() ?: return Result(null, "pickup_miles_unreadable")
        val tMin = pairs[1].groupValues[1].toDoubleOrNull() ?: return Result(null, "trip_minutes_unreadable")
        val tMi = pairs[1].groupValues[2].toDoubleOrNull() ?: return Result(null, "trip_miles_unreadable")
        if (pMin !in 1.0..300.0 || tMin !in 1.0..300.0 ||
            pMi !in 0.1..300.0 || tMi !in 0.1..300.0) return Result(null, "legs_out_of_range")
        return Result(Candidate(platform, kind, payout, pMin, pMi, tMin, tMi, source,
            if (platform.startsWith("UBER")) uberServiceType(text) else null), "complete")
    }
}
