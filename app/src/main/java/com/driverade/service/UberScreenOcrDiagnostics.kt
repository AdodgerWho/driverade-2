package com.driverade.service

/**
 * Strictly diagnostic OCR summary: it does not create offers or alter alerts.
 * A screenshot may contain a different foreground app, so extracted values are
 * always UNVERIFIED and never handed to the live offer parser.
 */
internal object UberScreenOcrDiagnostics {
    data class Summary(
        val likelyOfferCard: Boolean,
        val possibleMatch: Boolean,
        val possibleExclusive: Boolean,
        val amounts: List<Double>,
        val legMinutes: List<Double>,
        val legMiles: List<Double>
    ) {
        fun diagnostic(captureSource: String = "UNKNOWN"): String =
            "cardLikely=$likelyOfferCard match=$possibleMatch exclusive=$possibleExclusive " +
            "amounts=${amounts.joinToString(",")} " +
            "legMinutes=${legMinutes.joinToString(",")} legMiles=${legMiles.joinToString(",")} " +
            "source=" + (if (captureSource == "WINDOW") "WINDOW_OCR"
                else if (captureSource == "DISPLAY" || captureSource == "DISPLAY_FALLBACK")
                    "DISPLAY_OCR" else "UNKNOWN_OCR") +
            " status=UNVERIFIED rawText=OMITTED"
    }
    private val money = Regex("""\$\s*(\d{1,3}(?:\.\d{1,2})?)""")
    private val time = Regex("""\b(\d{1,3})\s*min\b""", RegexOption.IGNORE_CASE)
    private val miles = Regex("""\b(\d{1,3}(?:\.\d+)?)\s*mi\b""", RegexOption.IGNORE_CASE)

    fun inspect(text: String): Summary {
        val uber = Regex("""\buber\s*x?\b|\buberx\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val match = Regex("""\bmatch\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val exclusive = Regex("""\bexclusive\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val prices = money.findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 2.0..500.0 }.distinct().take(6).toList()
        val mins = time.findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 1.0..300.0 }.take(6).toList()
        val mi = miles.findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 0.1..500.0 }.take(6).toList()
        val likely = uber && prices.isNotEmpty() && mins.isNotEmpty() && mi.isNotEmpty()
        return Summary(likely, match, exclusive,
            if (likely) prices else emptyList(),
            if (likely) mins else emptyList(),
            if (likely) mi else emptyList())
    }
}
