package com.driverade.service

import android.content.Context
import android.util.Log
import com.driverade.BuildConfig
import com.driverade.data.DriverPreferences
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object InternalDiagnosticsLogger {
    private const val TAG = "InternalDiagnostics"
    private const val FILENAME = "driverade_diagnostics.txt"
    private const val SESSION_PREFS = "diagnostic_work_sessions"

    fun logInteraction(context: Context, category: String, details: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$time] [$category] $details\n"
        
        Log.d(TAG, entry)

        try {
            val file = File(context.filesDir, FILENAME)
            FileWriter(file, true).use { writer ->
                writer.append(entry)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write diagnostic interaction", e)
        }
    }

    /** Reconnecting the accessibility service must never erase previous evidence. */
    fun initializeSession(context: Context, prefs: DriverPreferences) {
        logInteraction(context, "BUILD_ID", "version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} type=${BuildConfig.BUILD_TYPE} device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}")
        logInteraction(context, "SERVICE_CONNECTED", "Diagnostic service attached")
    }

    /** Only call when a platform has a verified online/offline signal. */
    @Synchronized
    fun setPlatformOnline(context: Context, platform: String, online: Boolean) {
        val store = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val active = store.getStringSet("online_platforms", emptySet())!!.toMutableSet()
        val changed = if (online) active.add(platform) else active.remove(platform)
        if (!changed) return
        if (online && store.getString("active_session", null) == null) {
            val id = UUID.randomUUID().toString()
            store.edit().putString("active_session", id).putString("last_session", id).apply()
            logInteraction(context, "SESSION_START", "id=$id first=$platform")
        }
        val id = store.getString("active_session", null)
        logInteraction(context, "PLATFORM_STATE", "session=$id platform=$platform online=$online")
        store.edit().putStringSet("online_platforms", active).apply()
        if (active.isEmpty() && id != null) {
            logInteraction(context, "SESSION_END", "id=$id all platforms confirmed offline")
            store.edit().remove("active_session").apply()
        }
    }

    @Synchronized
    fun exportLog(context: Context, allSessions: Boolean): String {
        val file = File(context.filesDir, FILENAME)
        if (!file.exists()) return ""
        if (allSessions) return file.readText()
        val store = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        val id = store.getString("active_session", null) ?: store.getString("last_session", null)
        if (id == null) return ""
        val lines = file.readLines()
        val start = lines.indexOfLast { it.contains("[SESSION_START]") && it.contains("id=$id") }
        if (start < 0) return ""
        val end = lines.indexOfLast { it.contains("[SESSION_END]") && it.contains("id=$id") }
        return lines.subList(start, if (end >= start) end + 1 else lines.size).joinToString("\n")
    }
}
