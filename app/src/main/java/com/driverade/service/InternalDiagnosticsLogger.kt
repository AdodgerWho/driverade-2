package com.driverade.service

import android.content.Context
import android.util.Log
import com.driverade.data.DriverPreferences
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InternalDiagnosticsLogger {
    private const val TAG = "InternalDiagnostics"
    private const val FILENAME = "driverade_diagnostics.txt"

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

    fun initializeSession(context: Context, prefs: DriverPreferences) {
        val file = File(context.filesDir, FILENAME)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        
        val header = buildString {
            append("=== Driverade Diagnostics & Interaction Log ===\n")
            append("Timestamp: $timestamp\n")
            append("Target Rate: $${prefs.targetHourlyRate} | Min Payout: $${prefs.minPayout}\n")
            append("--------------------------------------------------\n")
        }

        try {
            file.writeText(header)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize diagnostic session", e)
        }
    }
}
