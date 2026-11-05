package com.driverade.service

import android.content.Context

/** Local-only guide state. No screen text or screenshots are persisted here. */
object GuidedSettingsState {
    private const val PREFS = "driverade_guidance"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun setActive(context: Context, active: Boolean) = prefs(context).edit().putBoolean("active", active).apply()
    fun isActive(context: Context) = prefs(context).getBoolean("active", false)
    fun setStep(context: Context, step: Int) = prefs(context).edit().putInt("step", step).apply()
    fun step(context: Context) = prefs(context).getInt("step", 0)
    fun setGuide(context: Context, guide: String) = prefs(context).edit().putString("guide", guide).apply()
    fun guide(context: Context) = prefs(context).getString("guide", "auto") ?: "auto"
    fun setTargetSeen(context: Context, seen: Boolean) = prefs(context).edit().putBoolean("target_seen", seen).apply()
    fun targetSeen(context: Context) = prefs(context).getBoolean("target_seen", false)
}
