package com.driverade.service

import android.content.Context
import android.util.Log
import com.driverade.BuildConfig
import com.driverade.data.DriverPreferences
import java.io.File
import java.io.FileWriter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InternalDiagnosticsLogger {
    private const val TAG = "InternalDiagnostics"
    private const val FILENAME = "driverade_diagnostics.txt"
    private val worker = Executors.newSingleThreadExecutor()
    private val queued = AtomicInteger(0)
    private var store: DiagnosticSqliteStore? = null

    fun logInteraction(context: Context, category: String, details: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val entry = "[$time] [$category] $details\n"
        
        Log.d(TAG, entry)

        if (queued.incrementAndGet() > 2000) {
            queued.decrementAndGet()
            return
        }
        val appContext = context.applicationContext
        worker.execute {
            try {
                val database = store ?: DiagnosticSqliteStore(appContext).also { store = it }
                database.add(System.currentTimeMillis(), category, details.take(1000))
                FileWriter(File(appContext.filesDir, FILENAME), true).use { it.append(entry) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist diagnostic interaction", e)
            } finally {
                queued.decrementAndGet()
            }
        }
    }

    fun initializeSession(context: Context, prefs: DriverPreferences) {
        logInteraction(context, "BUILD_ID", "version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} type=${BuildConfig.BUILD_TYPE} sha=${BuildConfig.SOURCE_SHA} branch=${BuildConfig.SOURCE_BRANCH} run=${BuildConfig.CI_RUN_ID} android=${android.os.Build.VERSION.SDK_INT} device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}")
        val file = File(context.filesDir, FILENAME)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        
        val header = buildString {
            append("=== Driverade Diagnostics & Interaction Log ===\n")
            append("Timestamp: $timestamp\n")
            append("Target Rate: $${prefs.targetHourlyRate} | Min Payout: $${prefs.minPayout}\n")
            append("--------------------------------------------------\n")
        }

        try {
            FileWriter(file, true).use { writer -> writer.append("\n").append(header) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize diagnostic session", e)
        }
    }
}
