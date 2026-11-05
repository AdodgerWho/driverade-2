package com.driverade.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.driverade.data.DriverPreferences
import java.io.File
import java.io.FileInputStream

object BugReportManager {

    private const val SUPPORT_EMAIL = "adodgerwho@gmail.com"

    fun createBugReportEmailIntent(
        context: Context,
        prefs: DriverPreferences,
        userFeedback: String
    ): Intent {
        val diagFile = File(context.filesDir, "driverade_diagnostics.txt")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            diagFile
        )
        
        val body = buildString {
            append("Driver Feedback:\n")
            append(userFeedback.ifBlank { "(No custom note entered)" })
            append("\n\n--- Inline Diagnostics Preview ---\n")
            append(runCatching { diagFile.readText() }.getOrDefault("Unable to read log file."))
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Driverade Bug Report & Diagnostics")
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportLogsLocally(context: Context, prefs: DriverPreferences): Boolean {
        val sourceFile = File(context.filesDir, "driverade_diagnostics.txt")
        val fileName = "driverade_diagnostics_${System.currentTimeMillis()}.txt"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, "Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                    true
                } else {
                    false
                }
            } else {
                val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, fileName)
                sourceFile.copyTo(targetFile, overwrite = true)
                Toast.makeText(context, "Exported to ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.SHORT).show()
            false
        }
    }
}
