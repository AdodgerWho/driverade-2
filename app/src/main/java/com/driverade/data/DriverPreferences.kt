package com.driverade.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriverPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("driverade_timing_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 350L
        const val DEFAULT_TIMED_MS = 1200L
        const val MIN_DELAY_MS = 100L
        const val MAX_DELAY_MS = 2000L
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun todayDiagnosticsFile(): File = File(appContext.filesDir, "offer-diagnostics-${todayKey()}.log")

    private fun checkDayRollover() {
        val today = todayKey()
        val savedDay = prefs.getString("stats_date", "")
        if (savedDay != today) {
            val isRunning = prefs.getLong("active_session_start_ms", 0L) > 0L
            prefs.edit()
                .putString("stats_date", today)
                .putFloat("today_earnings", 0f)
                .putFloat("last_seen_map_total_UBER", -1f)
                .putFloat("last_seen_map_total_LYFT", -1f)
                .putFloat("last_seen_map_total_HICH", -1f)
                .putInt("today_offers", 0)
                .putInt("today_good_offers", 0)
                .putLong("today_active_ms", 0L)
                .putLong("active_session_start_ms", if (isRunning) System.currentTimeMillis() else 0L)
                .apply()
        }
    }

    var customDebounceMs: Long
        get() = prefs.getLong("custom_debounce_ms", DEFAULT_DEBOUNCE_MS)
        set(value) { prefs.edit().putLong("custom_debounce_ms", value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)).apply() }

    var customPollingMs: Long
        get() = prefs.getLong("custom_polling_ms", DEFAULT_TIMED_MS)
        set(value) { prefs.edit().putLong("custom_polling_ms", value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)).apply() }

    var targetRate: Double
        get() = prefs.getFloat("target_rate", 25f).toDouble()
        set(value) { prefs.edit().putFloat("target_rate", value.coerceAtLeast(0.0).toFloat()).apply() }

    var minPayout: Double
        get() = prefs.getFloat("min_payout", 10f).toDouble()
        set(value) { prefs.edit().putFloat("min_payout", value.coerceAtLeast(0.0).toFloat()).apply() }

    // Pill Customization settings
    var showHourlyOnPill: Boolean
        get() = prefs.getBoolean("show_hourly_on_pill", true)
        set(value) { prefs.edit().putBoolean("show_hourly_on_pill", value).apply() }

    var showEarningsOnPill: Boolean
        get() = prefs.getBoolean("show_earnings_on_pill", true)
        set(value) { prefs.edit().putBoolean("show_earnings_on_pill", value).apply() }

    var autoSwitchToHich: Boolean
        get() = prefs.getBoolean("auto_switch_to_hich", true)
        set(value) { prefs.edit().putBoolean("auto_switch_to_hich", value).apply() }

    fun getLastSeenMapTotal(appTag: String): Double = prefs.getFloat("last_seen_map_total_$appTag", -1f).toDouble()
    fun setLastSeenMapTotal(appTag: String, total: Double) {
        prefs.edit().putFloat("last_seen_map_total_$appTag", total.coerceAtLeast(0.0).toFloat()).apply()
    }

    var todayEarnings: Double
        get() {
            checkDayRollover()
            return prefs.getFloat("today_earnings", 0f).toDouble()
        }
        set(value) {
            checkDayRollover()
            prefs.edit().putFloat("today_earnings", value.coerceAtLeast(0.0).toFloat()).apply()
        }

    fun handleMapTotalUpdate(newTotal: Double, appTag: String): Boolean {
        checkDayRollover()
        val cleaned = newTotal.coerceAtLeast(0.0)
        val last = getLastSeenMapTotal(appTag)
        if (last < 0.0) {
            setLastSeenMapTotal(appTag, cleaned)
            return false
        }
        if (cleaned > last) {
            val delta = cleaned - last
            todayEarnings += delta
            setLastSeenMapTotal(appTag, cleaned)
            return true
        } else if (cleaned != last) {
            setLastSeenMapTotal(appTag, cleaned)
        }
        return false
    }

    var todayOffersCount: Int
        get() { checkDayRollover(); return prefs.getInt("today_offers", 0) }
        set(value) { checkDayRollover(); prefs.edit().putInt("today_offers", value.coerceAtLeast(0)).apply() }

    var todayGoodOffersCount: Int
        get() { checkDayRollover(); return prefs.getInt("today_good_offers", 0) }
        set(value) { checkDayRollover(); prefs.edit().putInt("today_good_offers", value.coerceAtLeast(0)).apply() }

    fun recordOffer(
        fare: Double,
        isGood: Boolean,
        autoAddEarnings: Boolean = true,
        isAccepted: Boolean = autoAddEarnings,
        appTag: String = "OTHER",
        hourlyRate: Double = 0.0,
        countAsNewOffer: Boolean = true
    ) {
        checkDayRollover()
        if (countAsNewOffer) {
            todayOffersCount += 1
            if (isGood) todayGoodOffersCount += 1
        }
        if (autoAddEarnings && fare > 0.0) {
            todayEarnings += fare
        }
        val diagnostic = listOf(
            System.currentTimeMillis().toString(),
            appTag,
            String.format(Locale.US, "%.2f", fare),
            String.format(Locale.US, "%.2f", hourlyRate),
            isGood.toString(),
            isAccepted.toString()
        ).joinToString("|")
        try {
            todayDiagnosticsFile().appendText("$diagnostic\n")
        } catch (e: IOException) {
            Log.e("DriverPreferences", "Unable to save offer diagnostic", e)
        }
    }

    fun getOfferDiagnostics(): String {
        checkDayRollover()
        return todayDiagnosticsFile().takeIf { it.exists() }?.readText().orEmpty()
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
            .putFloat("last_seen_map_total_UBER", -1f)
            .putFloat("last_seen_map_total_LYFT", -1f)
            .putFloat("last_seen_map_total_HICH", -1f)
            .putInt("today_offers", 0)
            .putInt("today_good_offers", 0)
            .putLong("today_active_ms", 0L)
            .putLong("active_session_start_ms", if (isRunning) System.currentTimeMillis() else 0L)
            .apply()
        todayDiagnosticsFile().writeText("")
    }
}
