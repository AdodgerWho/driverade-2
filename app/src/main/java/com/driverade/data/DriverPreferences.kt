package com.driverade.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class DriverPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("driverade_prefs_v363", Context.MODE_PRIVATE)

    val ephemeralSessionId: String = UUID.randomUUID().toString()

    var targetHourlyRate: Double
        get() = prefs.getFloat(KEY_TARGET_RATE, 25.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TARGET_RATE, value.toFloat()).apply()

    var minPayout: Double
        get() = prefs.getFloat(KEY_MIN_PAYOUT, 10.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_MIN_PAYOUT, value.toFloat()).apply()

    /** Both modes default off until the driver opts in. */
    var alerterEnabled: Boolean
        get() = prefs.getBoolean("alerter_enabled", false)
        set(value) = prefs.edit().putBoolean("alerter_enabled", value).apply()

    var loggerEnabled: Boolean
        get() = prefs.getBoolean("logger_enabled", false)
        set(value) = prefs.edit().putBoolean("logger_enabled", value).apply()

    var todayOffersCount: Int
        get() = prefs.getInt("today_offers_count", 0)
        set(value) = prefs.edit().putInt("today_offers_count", value.coerceAtLeast(0)).apply()

    var todayGoodOffersCount: Int
        get() = prefs.getInt("today_good_offers_count", 0)
        set(value) = prefs.edit().putInt("today_good_offers_count", value.coerceAtLeast(0)).apply()

    var todayEarnings: Double
        get() = prefs.getFloat(KEY_TODAY_EARNINGS, 0.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TODAY_EARNINGS, value.toFloat()).apply()

    var todayActiveHours: Double
        get() = prefs.getFloat(KEY_TODAY_ACTIVE_HOURS, 0.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TODAY_ACTIVE_HOURS, value.toFloat()).apply()

    var sessionStartMs: Long
        get() = prefs.getLong(KEY_SESSION_START, System.currentTimeMillis())
        set(value) = prefs.edit().putLong(KEY_SESSION_START, value).apply()

    fun isPlatformEnabled(platformKey: String): Boolean =
        prefs.getBoolean("platform_$platformKey", true)

    fun setPlatformEnabled(platformKey: String, enabled: Boolean) {
        prefs.edit().putBoolean("platform_$platformKey", enabled).apply()
    }

    companion object {
        private const val KEY_TARGET_RATE = "target_hourly_rate"
        private const val KEY_MIN_PAYOUT = "min_payout"
        private const val KEY_TODAY_EARNINGS = "today_earnings"
        private const val KEY_TODAY_ACTIVE_HOURS = "today_active_hours"
        private const val KEY_SESSION_START = "session_start_ms"
    }
}
