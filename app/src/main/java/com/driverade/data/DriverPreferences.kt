package com.driverade.data

import android.content.Context
import android.content.SharedPreferences

class DriverPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("driverade_prefs_v361", Context.MODE_PRIVATE)

    var targetHourlyRate: Double
        get() = prefs.getFloat("target_hourly_rate", 25.0f).toDouble()
        set(value) = prefs.edit().putFloat("target_hourly_rate", value.toFloat()).apply()

    var minPayout: Double
        get() = prefs.getFloat("min_payout", 10.0f).toDouble()
        set(value) = prefs.edit().putFloat("min_payout", value.toFloat()).apply()

    fun isPlatformEnabled(platformKey: String): Boolean = prefs.getBoolean("platform_$platformKey", true)
}
