package com.driverade.service

import java.util.Locale
import kotlin.math.abs

/**
 * Observation-only comparison of active parsing and isolated legacy candidates.
 * This never returns an OfferEvaluation, changes live gating, or exposes raw screen text.
 */
internal object OfferParserComparison {
    data class ActiveSnapshot(
        val payout: Double,
        val totalMinutes: Double,
        val totalMiles: Double,
        val hourlyRate: Double
    )

    data class FieldResult(
        val label: String,
        val active: Double?,
        val legacy: Double?,
        val state: String
    ) {
        fun line(): String = label + ": active=" + number(active) +
            " legacy=" + number(legacy) + " [" + state + "]"
    }

    data class Report(
        val platform: String,
        val gateAccepted: Boolean,
        val status: String,
        val legacySource: String?,
        val fields: List<FieldResult>,
        val reportedEstimatedRate: Double?,
        val pickupMinutes: Double?,
        val pickupMiles: Double?,
        val tripMinutes: Double?,
        val tripMiles: Double?,
        val nodeCount: Int
    ) {
        fun diagnostic(source: String, captureId: String, gateReason: String): String =
            "source=$source capture=$captureId platform=$platform gate=$gateReason " +
                "status=$status nodes=$nodeCount legacy=" + (legacySource ?: "NONE") + " " +
                fields.joinToString(" ") {
                    it.label + "=" + number(it.active) + "/" + number(it.legacy) + "/" + it.state
                } +
                " lyftProvidedRate=" + number(reportedEstimatedRate) +
                " pickupMin=" + number(pickupMinutes) + " pickupMi=" + number(pickupMiles) +
                " tripMin=" + number(tripMinutes) + " tripMi=" + number(tripMiles) +
                "; rawText=OMITTED"

        fun display(): String = buildString {
            append(platform + " | " + status + "\n")
            append("Active offer gate: " + (if (gateAccepted) "ACCEPT" else "REJECT") + "\n")
            append("Legacy adapter: " + (legacySource ?: "none / unsupported") + "\n\n")
            fields.forEach { append(it.line()).append('\n') }
            if (reportedEstimatedRate != null) {
                append("\nLyft displayed estimated rate: $" + number(reportedEstimatedRate) + "/hr")
                append("\n(Not directly comparable to Driverade's calculated hourly rate.)\n")
            }
            if (tripMinutes != null || tripMiles != null) {
                append("\nLegacy trip leg: " + number(tripMinutes) + " min, " +
                    number(tripMiles) + " mi")
                if (pickupMinutes == null || pickupMiles == null) {
                    append("\nPickup leg unavailable: total time/distance cannot be compared.")
                }
            }
            if (!gateAccepted) {
                append("\nGate rejected this screen; candidate data is not an actionable offer.")
            }
            append("\n\nComparison is observation-only; it cannot create offers or alerts.")
        }
    }

    fun compare(
        platform: String,
        texts: List<String>,
        gateAccepted: Boolean,
        active: ActiveSnapshot?
    ): Report {
        val candidate = when (platform) {
            "LYFT" -> LegacyOfferCandidates.lyft(texts)
            "UBER" -> LegacyOfferCandidates.uberReservation(texts)
            else -> null
        }
        val completeMiles = candidate?.let { c ->
            if (c.pickupMiles != null && c.tripMiles != null) c.pickupMiles + c.tripMiles else null
        }
        val fields = listOf(
            field("payout", active?.payout, candidate?.payout, 0.01),
            field("minutes", active?.totalMinutes, candidate?.completeDurationMinutes, 0.01),
            field("miles", active?.totalMiles, completeMiles, 0.01),
            field("hourly", active?.hourlyRate, candidate?.calculatedHourlyRate(), 0.05)
        )
        val status = when {
            !gateAccepted -> "GATE_REJECT"
            platform != "LYFT" && platform != "UBER" -> "NO_LEGACY_ADAPTER"
            candidate == null -> "NO_LEGACY_CANDIDATE"
            active == null -> "ACTIVE_PARSE_FAIL"
            fields.any { it.state == "DIFF" } -> "DIFF"
            fields.all { it.state == "MATCH" } -> "MATCH"
            else -> "PARTIAL"
        }
        return Report(platform, gateAccepted, status, candidate?.source, fields,
            candidate?.estimatedHourlyRate, candidate?.pickupMinutes, candidate?.pickupMiles,
            candidate?.tripMinutes, candidate?.tripMiles, texts.size)
    }

    private fun field(label: String, active: Double?, legacy: Double?, tolerance: Double) =
        FieldResult(label, active, legacy, when {
            active == null || legacy == null -> "UNKNOWN"
            abs(active - legacy) <= tolerance -> "MATCH"
            else -> "DIFF"
        })

    private fun number(value: Double?): String =
        if (value == null || !value.isFinite()) "NA" else String.format(Locale.US, "%.2f", value)
}
