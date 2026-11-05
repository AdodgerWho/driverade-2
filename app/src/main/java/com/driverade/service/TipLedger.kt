package com.driverade.service

import android.content.Context
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only, conservative observations of explicitly labelled tips.
 * Never infer a tip from a wallet or earnings balance change.
 * Counts are provisional: a stable trip identifier is not yet available from every app.
 */
internal object TipLedger {
    private val labelled = Regex(
        """(?:incl\.?\s*)?\$\s*(\d+(?:\.\d{1,2})?)\s*tip\b|\btip\s*[:]?\s*\$\s*(\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private const val NAME = "driverade_tip_observations"

    @Synchronized
    fun observe(context: Context, platform: String, nodes: List<String>) {
        if (platform != "LYFT" && platform != "UBER") return
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val today = dateFormat.format(Date())
        if (prefs.getString("date", "") != today) {
            prefs.edit().clear().putString("date", today).apply()
        }
        // Restrict collection to earnings/activity views, never live offer cards.
        val page = nodes.joinToString(" ").lowercase(Locale.US)
        if (!listOf("earnings", "activity", "breakdown", "net fare", "daily stats").any { page.contains(it) }) return

        nodes.forEachIndexed { index, node ->
            labelled.findAll(node).forEach { match ->
                val amount = (match.groupValues[1].ifEmpty { match.groupValues[2] }).toDoubleOrNull()
                    ?: return@forEach
                if (amount <= 0.0 || amount > 500.0) return@forEach
                // Adjacent text helps distinguish identically sized tips from different trips.
                val contextText = nodes.subList((index - 2).coerceAtLeast(0), (index + 3).coerceAtMost(nodes.size))
                    .joinToString("|").trim().lowercase(Locale.US)
                val rawKey = "$today|$platform|$contextText"
                val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray())
                    .take(12).joinToString("") { "%02x".format(it) }
                if (prefs.getBoolean("seen_$digest", false)) return@forEach
                val total = prefs.getFloat("total", 0f).toDouble() + amount
                val count = prefs.getInt("count", 0) + 1
                prefs.edit()
                    .putBoolean("seen_$digest", true)
                    .putFloat("total", total.toFloat())
                    .putInt("count", count)
                    .apply()
                InternalDiagnosticsLogger.logInteraction(
                    context, "TIP_OBSERVED",
                    "$platform explicit tip detected; amount=$amount; provisional=true"
                )
            }
        }
    }

    fun totalToday(context: Context): Double {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return if (prefs.getString("date", "") == dateFormat.format(Date())) prefs.getFloat("total", 0f).toDouble() else 0.0
    }

    fun countToday(context: Context): Int {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return if (prefs.getString("date", "") == dateFormat.format(Date())) prefs.getInt("count", 0) else 0
    }
}
