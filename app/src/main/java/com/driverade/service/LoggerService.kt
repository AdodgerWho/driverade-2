package com.driverade.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.FileProvider
import com.driverade.data.DriverPreferences
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoggerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val DIAGNOSTICS_FILENAME = "driverade_diagnostics.txt"

        fun getDiagnosticsFile(context: Context): File = File(context.filesDir, DIAGNOSTICS_FILENAME)

        fun ensureDiagnosticsHeader(context: Context, prefs: DriverPreferences): File {
            val file = getDiagnosticsFile(context)
            if (!file.exists() || file.length() == 0L) {
                try {
                    FileWriter(file, false).use {
                        it.append("Driverade Diagnostics Log\n")
                    }
                } catch (_: Exception) {}
            }
            return file
        }

        fun writeDiagnosticsSnapshot(context: Context, prefs: DriverPreferences, offers: List<OfferEvaluation>): File {
            return ensureDiagnosticsHeader(context, prefs)
        }

        fun recordRawEventDump(context: Context, prefs: DriverPreferences, pkg: String, nodes: List<String>) {
            val file = ensureDiagnosticsHeader(context, prefs)
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            try {
                FileWriter(file, true).use {
                    it.append("[$time] [RAW_DUMP] PKG=$pkg | Count=${nodes.size}\n")
                    if (nodes.isNotEmpty()) it.append("  Preview: ${nodes.take(5).joinToString(" || ")}\n")
                }
            } catch (_: Exception) {}
        }

        fun recordOfferDiagnostic(context: Context, prefs: DriverPreferences, offer: OfferEvaluation) {
            val file = ensureDiagnosticsHeader(context, prefs)
            try {
                FileWriter(file, true).use {
                    it.append("[PARSED] Platform=${offer.platform} Payout=$${offer.payout} Rate=$${offer.hourlyRate}/hr\n")
                }
            } catch (_: Exception) {}
        }

        fun createBugReportEmailIntent(context: Context, prefs: DriverPreferences, feedback: String): Intent {
            val file = ensureDiagnosticsHeader(context, prefs)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            return Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("adodgerwho@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Driverade Bug Report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
