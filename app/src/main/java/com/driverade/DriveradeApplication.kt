package com.driverade

import android.app.Application
import com.driverade.service.InternalDiagnosticsLogger
import java.io.File
import java.io.FileWriter

/** Captures minimal local crash metadata without exporting screen text or rider details. */
class DriveradeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val cause = generateSequence(error) { it.cause }.take(6)
                    .joinToString(" -> ") { it.javaClass.simpleName }
                val location = error.stackTrace.firstOrNull { it.className.startsWith("com.driverade.") }
                val frame = location?.let { "${it.className.substringAfterLast('.') }:${it.lineNumber}" } ?: "unknown"
                // Avoid Throwable.message and stack trace: they may contain sensitive app content.
                val record = "[FATAL_CRASH] time=${System.currentTimeMillis()} thread=${thread.name.take(40)} causes=$cause frame=$frame\n"
                FileWriter(File(filesDir, "driverade_crashes.txt"), true).use { it.append(record) }
                InternalDiagnosticsLogger.logInteraction(this, "FATAL_CRASH", "type=${error.javaClass.simpleName} frame=$frame; details withheld")
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }
}
