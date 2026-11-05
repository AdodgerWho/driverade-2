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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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


    /**
     * Explicit, user-initiated export. Raw screenshots may contain rider names and locations.
     * No network transmission occurs. Only files in the app-private visual_debug directory
     * are included, and the existing text-only export remains available separately.
     */
    fun exportVisualDebugBundle(context: Context): Boolean {
        val diagnostic = File(context.filesDir, "driverade_diagnostics.txt")
        val images = File(context.filesDir, "visual_debug").listFiles()
            ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name }.orEmpty()
        if (!diagnostic.isFile && images.isEmpty()) {
            Toast.makeText(context, "No diagnostic log or saved debug screenshots found", Toast.LENGTH_LONG).show()
            return false
        }
        val filename = "driverade_debug_bundle_${System.currentTimeMillis()}.zip"
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Toast.makeText(context, "Bundle export requires Android 10 or newer", Toast.LENGTH_LONG).show()
                return false
            }
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create download")
            try {
                resolver.openOutputStream(uri)?.use { stream ->
                    ZipOutputStream(stream).use { zip ->
                        fun add(file: File, entryName: String) {
                            zip.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                        if (diagnostic.isFile) add(diagnostic, diagnostic.name)
                        images.forEach { add(it, "screenshots/${it.name}") }
                        zip.putNextEntry(ZipEntry("README.txt"))
                        zip.write(("Contains ${images.size} unredacted debug screenshots. " +
                            "Screenshots may show personal rider information, locations, and offers. " +
                            "Review before sharing. No files were uploaded automatically.\n").toByteArray())
                        zip.closeEntry()
                    }
                } ?: error("Unable to open download")
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
                InternalDiagnosticsLogger.logInteraction(context, "VISUAL_DEBUG_EXPORT",
                    "bundle_created screenshots=${images.size} logIncluded=${diagnostic.isFile}")
                Toast.makeText(context, "Saved Downloads/$filename (${images.size} screenshots)", Toast.LENGTH_LONG).show()
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } catch (e: Exception) {
            InternalDiagnosticsLogger.logInteraction(context, "VISUAL_DEBUG_EXPORT_ERROR", e.javaClass.simpleName)
            Toast.makeText(context, "Debug bundle export failed: ${e.message}", Toast.LENGTH_LONG).show()
            false
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
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
