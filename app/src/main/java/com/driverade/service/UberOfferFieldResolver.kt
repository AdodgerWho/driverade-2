package com.driverade.service

/**
 * Field-aware Uber offer diagnostics. No OCR, no arbitrary largest-dollar selection:
 * only a payout bound to the visible offer header/Accept control is considered verified.
 * This resolver never creates offers, alerts, or fills in missing pickup legs.
 */
internal object UberOfferFieldResolver {
    data class Node(
        val text: String,
        val viewId: String? = null,
        val windowId: Int = -1,
        val visible: Boolean = true
    )
    data class Result(
        val payout: Double?,
        val source: String,
        val tripMinutes: Double?,
        val tripMiles: Double?,
        val windowId: Int?,
        val conflictingAmounts: Boolean,
        val nodeCount: Int
    ) {
        fun diagnostic(): String =
            "verifiedPayout=" + (payout?.toString() ?: "NA") +
            " payoutSource=$source tripMinutes=" + (tripMinutes ?: "NA") +
            " tripMiles=" + (tripMiles ?: "NA") +
            " window=" + (windowId ?: "NA") +
            " conflictingAmounts=$conflictingAmounts nodes=$nodeCount rawText=OMITTED"
    }

    private val dollars = Regex("""\$\s*(\d{1,4}(?:\.\d{1,2})?)""")
    private val acceptAmount = Regex("""\bAccept\s*[•·:]?\s*\$\s*(\d{1,4}(?:\.\d{1,2})?)\b""", RegexOption.IGNORE_CASE)
    private val tripLeg = Regex("""(\d{1,3})\s*min(?:s|utes?)?\s*[•·|,()]?\s*\(?\s*(\d+(?:\.\d+)?)\s*mi\b""", RegexOption.IGNORE_CASE)
    private val acceptId = "upfront_offer_configurable_details_auditable_accept_button"
    private val headerId = "ub__upfront_offer_header_auditable_title"
    private val tripId = "ub__upfront_offer_map_label"

    fun resolve(nodes: List<Node>): Result {
        val visible = nodes.filter { it.visible && it.text.isNotBlank() }
        val windows = visible.groupBy { it.windowId }
        val choices = windows.map { (windowId, group) ->
            val accepted = group.mapNotNull { node ->
                if (node.viewId?.contains(acceptId, true) == true || acceptAmount.containsMatchIn(node.text))
                    amount(acceptAmount.find(node.text)?.groupValues?.getOrNull(1))
                        ?: if (node.viewId?.contains(acceptId, true) == true) amount(dollars.find(node.text)?.groupValues?.getOrNull(1)) else null
                else null
            }
            val headers = group.filter { it.viewId?.contains(headerId, true) == true }
                .mapNotNull { amount(dollars.find(it.text)?.groupValues?.getOrNull(1)) }
            val selected = accepted.firstOrNull() ?: headers.firstOrNull()
            val conflicting = (accepted + headers).distinct().size > 1
            val legNode = group.firstOrNull { it.viewId?.contains(tripId, true) == true && tripLeg.containsMatchIn(it.text) }
            val leg = legNode?.let { tripLeg.find(it.text) }
            Triple(windowId, selected?.let { it to (if (accepted.isNotEmpty()) "ACCEPT_CONTROL" else "OFFER_HEADER") },
                Pair(conflicting,leg))
        }.filter { it.second != null }
        // Never combine fields from different windows. Ambiguous multiple offers are diagnostic only.
        val winning = choices.singleOrNull()
        val window = winning?.first
        val payout = winning?.second?.first
        val source = when {
            choices.size > 1 -> "AMBIGUOUS_WINDOWS"
            winning == null -> "NO_BOUND_PAYOUT"
            winning.third.first -> "CONFLICTING_OFFER_FIELDS"
            else -> winning.second?.second ?: "NO_BOUND_PAYOUT"
        }
        val leg = winning?.third?.second
        return Result(if (source == "AMBIGUOUS_WINDOWS" || source == "CONFLICTING_OFFER_FIELDS") null else payout,
            source,
            leg?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
            leg?.groupValues?.getOrNull(2)?.toDoubleOrNull(),
            window, winning?.third?.first == true || choices.size > 1, visible.size)
    }

    private fun amount(raw: String?): Double? =
        raw?.toDoubleOrNull()?.takeIf { it in 2.0..500.0 }
}
