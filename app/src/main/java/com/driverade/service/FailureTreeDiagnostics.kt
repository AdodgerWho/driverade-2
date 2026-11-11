package com.driverade.service

import android.content.Context
import android.os.SystemClock
import com.driverade.data.DriverPreferences
import java.util.ArrayDeque

/**
 * Optional diagnostic correlation layer. It consumes already-sanitized event categories
 * and never changes the accessibility listener, offer gates, or alerter.
 */
internal object FailureTreeDiagnostics {
    private var database: FailureTreeStore? = null
    private val recentFailures = ArrayDeque<Pair<Long, String>>()
    private val lastAnomalyAt = mutableMapOf<String, Long>()
    private const val WINDOW_MS = 60_000L
    private const val COOLDOWN_MS = 120_000L

    @Synchronized
    fun observe(context: Context, category: String, detail: String, now: Long = System.currentTimeMillis()): String? {
        // Owner-controlled diagnostic capture, independent of routine monitoring.
        if (!DriverPreferences(context).loggerEnabled) return null
        val finding = FailureTreeRules.classify(category, detail) ?: return null
        val db = database ?: FailureTreeStore(context.applicationContext).also { database = it }
        db.record(now, finding, FailureTreeRules.captureId(detail))
        // A single "no root" or disabled-owner observation is not an anomaly.
        if (finding.severity !in setOf("ERROR", "WARNING")) return null
        recentFailures.addLast(now to finding.kind)
        while (recentFailures.isNotEmpty() && now - recentFailures.first.first > WINDOW_MS)
            recentFailures.removeFirst()
        while (recentFailures.size > 200) recentFailures.removeFirst()
        val matching = recentFailures.count { it.second == finding.kind }
        val lastAt = lastAnomalyAt[finding.kind] ?: 0L
        return if (matching >= 4 && now - lastAt >= COOLDOWN_MS) {
            lastAnomalyAt[finding.kind] = now
            "kind=${finding.kind} countWithinMinute=$matching stage=${finding.stage} " +
                "confidence=OBSERVED_REPETITION notRootCause=true"
        } else null
    }

    @Synchronized
    fun summary(context: Context): String {
        if (!DriverPreferences(context).loggerEnabled) return "Failure tree disabled (enable diagnostic logger to collect)"
        return try {
            (database ?: FailureTreeStore(context.applicationContext).also { database = it }).summary()
        } catch (e: Exception) {
            "Failure-tree database unavailable: ${e.javaClass.simpleName}"
        }
    }
}
