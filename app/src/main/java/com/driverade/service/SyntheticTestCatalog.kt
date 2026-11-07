package com.driverade.service

/** Repeatable, side-effect-free injector fixtures; never routed into live offer metrics. */
internal object SyntheticTestCatalog {
    data class Case(
        val name: String,
        val platform: String,
        val nodes: String,
        val expectedActiveStatus: String,
        val expectedProvenance: String? = null,
        val expectedPayout: Double? = null,
        val expectedMinutes: Double? = null,
        val expectedMiles: Double? = null,
        val source: String = "TREE"
    )

    val presets = listOf(
        Case("Uber actionable ride", "UBER",
            "Trip request\nAccept\n\$18.50\n5 min • 1.0 mi\n20 min • 4.2 mi", "PASS", "NO_BOUND_PAYOUT",
            18.50, 25.0, 5.2),
        Case("Uber reservation", "UBER",
            "Trip request\nAccept • \$24.50\n12 min • 3.2 mi\n18 min • 6.4 mi",
            "PASS", "ACCEPT_CONTROL", 24.50, 30.0, 9.6),
        Case("Lyft estimated rate", "LYFT",
            "Ride request\nAccept\n\$18.50\n\$37/hr est rate for this ride\n5 min • 1.0 mi\n25 min • 8.0 mi", "PASS", null, 18.50, 30.0, 9.0),
        Case("HICH basic offer", "HICH",
            "Ride request\nAccept\n\$16.00\n5 mins (1.0 mi) away\n19 mins (5.0 mi) trip",
            "PASS", null, 16.00, 24.0, 6.0),
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
            "GATE_REJECT", "ACCEPT_CONTROL"),
        Case("Map balance navigation (reject)", "UBER",
            "Home\n\$73.52\n2 min\n1 mi\nStart navigation",
            "GATE_REJECT", "NO_BOUND_PAYOUT"),
        Case("Historical payout conflict complete", "UBER",
            "Trip request\nAccept • \$12.04\n\$13.08\n6 min • 2.0 mi\n7 min • 1.3 mi",
            "PASS", "ACCEPT_CONTROL", 12.04, 13.0, 3.3),
        Case("UberX Exclusive real OCR", "UBER",
            "UberX Exclusive\n\$5.53\n4.94 Verified\n5% Pro Perk included\n3 min • 0.7 mi\n" +
            "Pillsbury Ave\n7 mins • 2.1 mi\nAccept",
            "PASS", "NO_BOUND_PAYOUT", 5.53, 10.0, 2.8, "OCR"),
        Case("UberX Match real OCR", "UBER",
            "UberX\n\$6.74\n4.90 Verified\n5% Pro Perk included\n6 min • 2.4 mi\n" +
            "Beard Ave\n7 mins • 2.3 mi\nMatch",
            "PASS", "NO_BOUND_PAYOUT", 6.74, 13.0, 4.7, "OCR")
    )

    data class Outcome(
        val testCase: Case,
        val actualStatus: String,
        val provenance: String?,
        val details: String,
        val payout: Double? = null,
        val minutes: Double? = null,
        val miles: Double? = null
    ) {
        val meetsExpectation: Boolean
            get() = actualStatus == testCase.expectedActiveStatus &&
                (testCase.expectedProvenance == null || provenance == testCase.expectedProvenance) &&
                (testCase.expectedPayout == null ||
                    kotlin.math.abs((payout ?: Double.NaN) - testCase.expectedPayout) < 0.02) &&
                (testCase.expectedMinutes == null ||
                    kotlin.math.abs((minutes ?: Double.NaN) - testCase.expectedMinutes) < 0.02) &&
                (testCase.expectedMiles == null ||
                    kotlin.math.abs((miles ?: Double.NaN) - testCase.expectedMiles) < 0.02)
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
            if (o.testCase.expectedPayout != null) {
                text.append(" | payout=").append(o.payout)
                    .append(" expected=").append(o.testCase.expectedPayout)
                text.append(" | minutes=").append(o.minutes)
                    .append(" expected=").append(o.testCase.expectedMinutes)
                text.append(" | miles=").append(o.miles)
                    .append(" expected=").append(o.testCase.expectedMiles)
            }
            if (o.testCase.expectedProvenance != null) {
                text.append(" | binding=").append(o.provenance ?: "NA")
                    .append(" expected=").append(o.testCase.expectedProvenance)
            }
        }
        text.append("\n\nSYNTHETIC ONLY. No live offers, alerts or statistics.")
        return text.toString()
    }
}
