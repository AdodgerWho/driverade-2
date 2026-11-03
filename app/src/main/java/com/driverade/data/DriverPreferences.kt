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
            prefs.edit().putString("stats_date", today).apply()
        }
    }

    var customDebounceMs: Long
        get() = prefs.getLong("custom_debounce_ms", DEFAULT_DEBOUNCE_MS)
        set(value) { prefs.edit().putLong("custom_debounce_ms", value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)).apply() }

    var customPollingMs: Long
        get() = prefs.getLong("custom_polling_ms", DEFAULT_TIMED_MS)
        set(value) { prefs.edit().putLong("custom_polling_ms", value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)).apply() }

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

    var todayActiveHours: Double
        get() {
            checkDayRollover()
            return prefs.getLong("today_active_ms", 0L) / 3_600_000.0
        }
        set(value) {
            checkDayRollover()
            val ms = (value.coerceAtLeast(0.0) * 3_600_000.0).toLong()
            prefs.edit().putLong("today_active_ms", ms).apply()
        }

    fun handleMapTotalUpdate(newTotal: Double, appTag: String) {
        checkDayRollover()
        val cleaned = newTotal.coerceAtLeast(0.0)
        val last = getLastSeenMapTotal(appTag)
        if (last < 0.0) {
            setLastSeenMapTotal(appTag, cleaned)
            return
        }
        if (cleaned > last) {
            val delta = cleaned - last
            todayEarnings += delta
            setLastSeenMapTotal(appTag, cleaned)
        } else if (cleaned != last) {
            setLastSeenMapTotal(appTag, cleaned)
        }
    }

    var todayOffersCount: Int
        get() { checkDayRollover(); return prefs.getInt("today_offers", 0) }
        set(value) { checkDayRollover(); prefs.edit().putInt("today_offers", value.coerceAtLeast(0)).apply() }

    var todayGoodOffersCount: Int
        get() { checkDayRollover(); return prefs.getInt("today_good_offers", 0) }
        set(value) { checkDayRollover(); prefs.edit().putInt("today_good_offers", value.coerceAtLeast(0)).apply() }

    fun recordOffer(fare: Double, isGood: Boolean, autoAddEarnings: Boolean = true) {
        checkDayRollover()
        todayOffersCount += 1
        if (isGood) todayGoodOffersCount += 1
        if (autoAddEarnings && fare > 0.0) {
            todayEarnings += fare
        }
    }

    fun resetTodayStats() {
        prefs.edit()
            .putString("stats_date", todayKey())
            .putFloat("today_earnings", 0f)
            .putFloat("last_seen_map_total_UBER", -1f)
            .putFloat("last_seen_map_total_LYFT", -1f)
            .putFloat("last_seen_map_total_HICH", -1f)
            .putInt("today_offers", 0)
            .putInt("today_good_offers", 0)
            .putLong("today_active_ms", 0L)
            .apply()
    }
}

