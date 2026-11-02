package com.driverade.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriverPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("driverade_timing_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 350L
        const val DEFAULT_TIMED_MS = 1200L
        const val MIN_DELAY_MS = 100L
        const val MAX_DELAY_MS = 2000L
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun checkDayRollover() {
        val today = todayKey()
        val savedDay = prefs.getString("stats_date", "")
        if (savedDay != today) {
            // Keep active session running across calendar days if user is actively driving,
            // but update stats date marker to avoid hard resets while online.
            prefs.edit()
                .putString("stats_date", today)
                .apply()
        }
    }

    var customDebounceMs: Long
        get() = prefs.getLong("custom_debounce_ms", DEFAULT_DEBOUNCE_MS)
        set(value) {
            val clamped = value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
            prefs.edit().putLong("custom_debounce_ms", clamped).apply()
        }

    var customPollingMs: Long
        get() = prefs.getLong("custom_polling_ms", DEFAULT_TIMED_MS)
        set(value) {
            val clamped = value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
            prefs.edit().putLong("custom_polling_ms", clamped).apply()
        }

    var lastSeenMapTotal: Double
        get() = prefs.getFloat("last_seen_map_total", -1f).toDouble()
        set(value) {
            prefs.edit().putFloat("last_seen_map_total", value.coerceAtLeast(0.0).toFloat()).apply()
        }

    var todayEarnings: Double
        get() {
            checkDayRollover()
            return prefs.getFloat("today_earnings", 0f).toDouble()
        }
        set(value) {
            checkDayRollover()
            val finalVal = value.coerceAtLeast(0.0)
            prefs.edit().putFloat("today_earnings", finalVal.toFloat()).apply()
        }

    fun handleMapTotalUpdate(newTotal: Double) {
        checkDayRollover()
        val cleaned = newTotal.coerceAtLeast(0.0)
        val last = lastSeenMapTotal

        if (last < 0.0) {
            // First initialization
            prefs.edit().putFloat("last_seen_map_total", cleaned.toFloat()).apply()
            return
        }

        if (cleaned > last) {
            // Positive delta: new fare or tip added! Add directly to today's net earnings.
            val delta = cleaned - last
            val currentEarnings = todayEarnings
            prefs.edit()
                .putFloat("today_earnings", (currentEarnings + delta).toFloat())
                .putFloat("last_seen_map_total", cleaned.toFloat())
                .apply()
        } else if (cleaned < last) {
            // Total dropped/reset (e.g., app rolled over to 0.0 or weekly/daily reset).
            // Instead of losing earnings or breaking, we treat the new lower number as a fresh baseline
            // OR if it's a rollover (drop to 0 or near 0), we preserve accumulated earnings and just re-anchor the baseline.
            if (cleaned == 0.0 || cleaned < (last * 0.1)) {
                // App rolled over to 0: keep accumulated todayEarnings, reset baseline to 0
                prefs.edit()
                    .putFloat("last_seen_map_total", cleaned.toFloat())
                    .apply()
            } else {
                // Minor fluctuation or shift adjustment: re-anchor baseline without dropping earnings
                prefs.edit()
                    .putFloat("last_seen_map_total", cleaned.toFloat())
                    .apply()
            }
        }
        // If cleaned == last, ignore completely as requested.
    }

    var todayOffersCount: Int
        get() {
            checkDayRollover()
            return prefs.getInt("today_offers", 0)
        }
        set(value) {
            checkDayRollover()
            prefs.edit().putInt("today_offers", value.coerceAtLeast(0)).apply()
        }

    var todayGoodOffersCount: Int
        get() {
            checkDayRollover()
            return prefs.getInt("today_good_offers", 0)
        }
        set(value) {
            checkDayRollover()
            prefs.edit().putInt("today_good_offers", value.coerceAtLeast(0)).apply()
        }

    fun recordOffer(fare: Double, isGood: Boolean, autoAddEarnings: Boolean = true) {
        checkDayRollover()
        val newOffers = todayOffersCount + 1
        val newGood = if (isGood) todayGoodOffersCount + 1 else todayGoodOffersCount
        prefs.edit()
            .putInt("today_offers", newOffers)
            .putInt("today_good_offers", newGood)
            .apply()

        if (autoAddEarnings && fare > 0.0) {
            val current = prefs.getFloat("today_earnings", 0f).toDouble()
            prefs.edit().putFloat("today_earnings", (current + fare).toFloat()).apply()
        }
    }

    fun onModeChanged(isActive: Boolean) {
        checkDayRollover()
        val now = System.currentTimeMillis()
        val start = prefs.getLong("active_session_start_ms", 0L)
        if (isActive) {
            if (start == 0L) {
                prefs.edit().putLong("active_session_start_ms", now).apply()
            }
        } else {
            if (start > 0L) {
                val elapsed = (now - start).coerceAtLeast(0L)
                val total = prefs.getLong("today_active_ms", 0L) + elapsed
                prefs.edit()
                    .putLong("today_active_ms", total)
                    .putLong("active_session_start_ms", 0L)
                    .apply()
            }
        }
    }

    var todayActiveHours: Double
        get() {
            checkDayRollover()
            val baseMs = prefs.getLong("today_active_ms", 0L)
            val start = prefs.getLong("active_session_start_ms", 0L)
            val liveMs = if (start > 0L) (System.currentTimeMillis() - start).coerceAtLeast(0L) else 0L
            return (baseMs + liveMs) / 3600000.0
        }
        set(hours) {
            checkDayRollover()
            val ms = (hours.coerceAtLeast(0.0) * 3600000.0).toLong()
            val isRunning = prefs.getLong("active_session_start_ms", 0L) > 0L
            val editor = prefs.edit().putLong("today_active_ms", ms)
            if (isRunning) {
                editor.putLong("active_session_start_ms", System.currentTimeMillis())
            }
            editor.apply()
        }

    fun resetTodayStats() {
        val isRunning = prefs.getLong("active_session_start_ms", 0L) > 0L
        prefs.edit()
            .putString("stats_date", todayKey())
            .putFloat("today_earnings", 0f)
            .putFloat("last_seen_map_total", -1f)
            .putInt("today_offers", 0)
            .putInt("today_good_offers", 0)
            .putLong("today_active_ms", 0L)
            .putLong("active_session_start_ms", if (isRunning) System.currentTimeMillis() else 0L)
            .apply()
    }
}
