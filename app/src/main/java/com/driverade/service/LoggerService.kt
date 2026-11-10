package com.driverade.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.driverade.data.DriverPreferences
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoggerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LoggerService"
        const val DIAGNOSTICS_FILENAME = "driverade_diagnostics.txt"

        fun getDiagnosticsFile(context: Context): File =
            File(context.filesDir, DIAGNOSTICS_FILENAME)

        fun ensureDiagnosticsHeader(context: Context, prefs: DriverPreferences): File {
            val file = getDiagnosticsFile(context)
            if (!file.exists() || file.length() == 0L) {
                writeDiagnosticsSnapshot(context, prefs, emptyList())
            }
            return file
        }

        fun writeDiagnosticsSnapshot(
            context: Context,
            prefs: DriverPreferences,
            capturedOffers: List<OfferEvaluation>
        ): File {
            val file = getDiagnosticsFile(context)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            try {
                FileWriter(file, false).use { writer ->
                    writer.append("Driverade Diagnostics - $timestamp\n")
                    writer.append("Version: 3.63\n")
                    writer.append("Target Rate: $${prefs.targetHourlyRate}\n")
                    writer.append("Min Payout: $${prefs.minPayout}\n")
                    writer.append("Today Earnings: $${prefs.todayEarnings}\n")
                    writer.append("Today Active Hours: ${ShiftTracker.getFormattedShiftHours(prefs)}\n")
                    writer.append("UBER Map Total: ${DriveradeState.uberMapTotal}\n")
                    writer.append("LYFT Map Total: ${DriveradeState.lyftMapTotal}\n")
                    writer.append("HICH Map Total: ${DriveradeState.hichMapTotal}\n\n")
                    writer.append("Offer Diagnostics:\n")
                    for (offer in capturedOffers) {
                        writer.append(formatOfferEntry(offer))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing diagnostics header", e)
            }
            return file
        }

        fun recordOfferDiagnostic(
            context: Context,
            prefs: DriverPreferences,
            offer: OfferEvaluation
        ) {
            val file = ensureDiagnosticsHeader(context, prefs)
            try {
                FileWriter(file, true).use { writer ->
                    writer.append(formatOfferEntry(offer))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed appending offer diagnostic", e)
            }
        }

        private fun formatOfferEntry(offer: OfferEvaluation): String {
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(offer.timestamp))
            return buildString {
                append("[$time] Platform=${offer.platform} (${offer.packageName}) | ")
                append(String.format(Locale.US, "Payout=$%.2f | Rate=$%.2f/hr | ", offer.payout, offer.hourlyRate))
                append(String.format(Locale.US, "Miles=%.1f | Minutes=%.0f | TargetMet=%b\n", offer.totalMiles, offer.totalMinutes, offer.meetsTarget))
                append("  [RAW_DUMP] Nodes: ${offer.rawNodes.joinToString(" :: ")}\n")
            }
        }

        fun createBugReportEmailIntent(
            context: Context,
            prefs: DriverPreferences,
            userFeedback: String
        ): Intent = BugReportManager.createBugReportEmailIntent(context, prefs, userFeedback)
    }
}
