package com.driverade.data

import android.content.Context

class DriverPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("driverade_timing_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 350L
        const val DEFAULT_TIMED_MS = 1200L
        const val MIN_DELAY_MS = 100L
        const val MAX_DELAY_MS = 2000L
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
}
