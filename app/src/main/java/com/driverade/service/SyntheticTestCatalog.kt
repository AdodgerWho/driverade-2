package com.driverade.service

/** Repeatable, side-effect-free injector fixtures; never routed into live offer metrics. */
internal object SyntheticTestCatalog {
    data class Case(
        val name: String,
        val platform: String,
        val nodes: String,
        val expectedActiveStatus: String,
        val expectedProvenance: String? = null
    )

    val presets = listOf(
        Case("Uber actionable ride", "UBER",
            "Trip request\nAccept\n\$18.50\n25 min\n5.2 mi", "PASS", "NO_BOUND_PAYOUT"),
        Case("Uber reservation", "UBER",
            "Trip request\nAccept • \$24.50\n12 min • 3.2 mi\n18 min • 6.4 mi",
            "PASS", "ACCEPT_CONTROL"),
        Case("Lyft estimated rate", "LYFT",
            "Ride request\nAccept\n\$18.50\n\$37/hr est rate for this ride\n5 min • 1.0 mi\n25 min • 8.0 mi", "PASS"),
        Case("HICH basic offer", "HICH",
            "Ride request\nAccept\n\$16.00\n24 min\n6.0 mi", "PASS"),
        Case("Earnings history (reject)", "UBER",
            "Your earnings\nTrip\nAccept\n\$101.83\n20 min\n4 mi",
            "GATE_REJECT", "NO_BOUND_PAYOUT"),
        Case("Map pill (reject)", "UBER",
            "Trip in progress\n\$12.00\n15 min\n2.5 mi",
            "GATE_REJECT", "NO_BOUND_PAYOUT"),
        Case("Missing distance (reject)", "UBER",
            "Trip request\nAccept\n\$18.50\n25 min",
            "GATE_REJECT", "NO_BOUND_PAYOUT"),
        Case("Unrelated larger dollar (diagnostic)", "UBER",
            "Trip request\nAccept • \$12.04\n\$13.08\n13 min (3.3 mi) trip",
            "PASS", "ACCEPT_CONTROL"),
        Case("Map balance navigation (reject)", "UBER",
            "Home\n\$73.52\n2 min\n1 mi\nStart navigation",
            "GATE_REJECT", "NO_BOUND_PAYOUT")
    )

    data class Outcome(
        val testCase: Case,
        val actualStatus: String,
        val provenance: String?,
        val details: String
    ) {
        val meetsExpectation: Boolean
            get() = actualStatus == testCase.expectedActiveStatus &&
                (testCase.expectedProvenance == null || provenance == testCase.expectedProvenance)
    }

    fun summary(outcomes: List<Outcome>, total: Int = presets.size): String {
        val passed = outcomes.count { it.meetsExpectation }
        val text = StringBuilder("Driverade synthetic batch: $passed/${outcomes.size} expectations met")
        if (outcomes.size < total) text.append(" (").append(total - outcomes.size).append(" not run)")
        outcomes.forEachIndexed { index, o ->
            text.append("\n").append(index + 1).append(". ")
                .append(if (o.meetsExpectation) "PASS" else "CHECK")
                .append(" [").append(o.testCase.platform).append("] ")
                .append(o.testCase.name)
                .append(" | active=").append(o.actualStatus)
                .append(" expected=").append(o.testCase.expectedActiveStatus)
            if (o.testCase.expectedProvenance != null) {
                text.append(" | binding=").append(o.provenance ?: "NA")
                    .append(" expected=").append(o.testCase.expectedProvenance)
            }
        }
        text.append("\n\nSYNTHETIC ONLY. No live offers, alerts or statistics.")
        return text.toString()
    }
}
