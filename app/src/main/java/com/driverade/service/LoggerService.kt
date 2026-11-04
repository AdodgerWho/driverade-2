package com.driverade.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.driverade.data.DriverPreferences
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoggerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun recordRawEventDump(context: Context, prefs: DriverPreferences, pkg: String, nodes: List<String>) {
            val file = File(context.filesDir, "driverade_diagnostics.txt")
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            try {
                FileWriter(file, true).use {
                    it.append("[$time] [RAW_DUMP] PKG=$pkg | Count=${nodes.size}\n")
                    if (nodes.isNotEmpty()) it.append("  Preview: ${nodes.take(5).joinToString(" || ")}\n")
                }
            } catch (_: Exception) {}
        }

        fun recordOfferDiagnostic(context: Context, prefs: DriverPreferences, offer: OfferEvaluation) {
            val file = File(context.filesDir, "driverade_diagnostics.txt")
            try {
                FileWriter(file, true).use {
                    it.append("[PARSED] Platform=${offer.platform} Payout=$${offer.payout} Rate=$${offer.hourlyRate}/hr\n")
                }
            } catch (_: Exception) {}
        }
    }
}
