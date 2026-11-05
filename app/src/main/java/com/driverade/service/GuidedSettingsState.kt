package com.driverade.service

import android.content.Context

/** Local-only opt-in session state; no screen data is persisted. */
object GuidedSettingsState {
    private const val PREFS = "driverade_guidance"
    fun setActive(context: Context, active: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("active", active).apply()
    fun isActive(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("active", false)
    fun setStep(context: Context, step: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("step", step).apply()
    fun step(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("step", 0)
}
