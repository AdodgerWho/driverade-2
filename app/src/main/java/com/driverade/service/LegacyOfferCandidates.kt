package com.driverade.service

/**
 * Legacy DriverLogger parsing ideas adapted for Driverade's Kotlin architecture.
 *
 * This is deliberately NOT connected to live offer evaluation yet. The current PR27
 * engine remains the source of truth until real capture fixtures validate these
 * platform-specific candidates against its existing offer gates.
 *
 * Only structured candidates are returned; missing values are never invented.
 */
internal object LegacyOfferCandidates {
    data class Candidate(
        val payout: Double? = null,
        val estimatedHourlyRate: Double? = null,
        val pickupMinutes: Double? = null,
        val pickupMiles: Double? = null,
        val tripMinutes: Double? = null,
        val tripMiles: Double? = null,
        val source: String
    ) {
        val completeDurationMinutes: Double?
            get() = if (pickupMinutes != null && tripMinutes != null)
                pickupMinutes + tripMinutes else null

        fun calculatedHourlyRate(): Double? {
            val minutes = completeDurationMinutes ?: return null
            val amount = payout ?: return null
            return if (minutes > 0.0) amount * 60.0 / minutes else null
        }
    }

    private val lyftRate = Regex("""\$(\d+(?:\.\d{1,2})?)/hr\s+est\.?\s+rate\s+for\s+this\s+ride""", RegexOption.IGNORE_CASE)
    private val money = Regex("""\$(\d+(?:\.\d{1,2})?)""")
    private val leg = Regex("""(\d+(?:\.\d+)?)\s*min(?:s|utes?)?\s*[•·|,()]?\s*(\d+(?:\.\d+)?)\s*mi\b""", RegexOption.IGNORE_CASE)
    private val uberAccept = Regex("""Accept\s*[•·]\s*\$(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    /** Recognize a Lyft-provided estimate without confusing it for the payout. */
    fun lyft(texts: List<String>): Candidate? {
        val joined = texts.joinToString(" ")
        val rate = lyftRate.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return null
        val payout = texts.asSequence()
            .filterNot { lyftRate.containsMatchIn(it) }
            .flatMap { money.findAll(it).map { match -> match.groupValues[1].toDoubleOrNull() } }
            .filterNotNull().filter { it in 2.0..500.0 }.firstOrNull()
        val legs = leg.findAll(joined).take(2).toList()
        return Candidate(
            payout = payout,
            estimatedHourlyRate = rate,
            pickupMinutes = legs.getOrNull(0)?.groupValues?.get(1)?.toDoubleOrNull(),
            pickupMiles = legs.getOrNull(0)?.groupValues?.get(2)?.toDoubleOrNull(),
            tripMinutes = legs.getOrNull(1)?.groupValues?.get(1)?.toDoubleOrNull(),
            tripMiles = legs.getOrNull(1)?.groupValues?.get(2)?.toDoubleOrNull(),
            source = "legacy-lyft-tree"
        )
    }

    /** Reservation-specific payout label; never treats arbitrary map dollars as an offer. */
    fun uberReservation(texts: List<String>): Candidate? {
        val joined = texts.joinToString(" ")
        val amount = uberAccept.find(joined)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return null
        if (amount !in 2.0..500.0) return null
        val trip = leg.find(joined)
        return Candidate(
            payout = amount,
            tripMinutes = trip?.groupValues?.get(1)?.toDoubleOrNull(),
            tripMiles = trip?.groupValues?.get(2)?.toDoubleOrNull(),
            source = "legacy-uber-reservation-tree"
        )
    }
}
