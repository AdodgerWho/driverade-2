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
            append(runCatching { diagFile.readText().takeLast(12000) }.getOrDefault("Unable to read log file."))
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
        val imageDir = File(context.filesDir, "visual_debug")
        val images = imageDir.listFiles()?.filter {
            it.isFile && it.extension.equals("png", true) && it.length() > 8
        }?.sortedBy { it.name }.orEmpty()
        InternalDiagnosticsLogger.logInteraction(context, "VISUAL_DEBUG_EXPORT_PRECHECK",
            "enabled=${context.getSharedPreferences("driverade_visual_debug", 0).getBoolean("enabled", false)} " +
                "imageCount=${images.size} logExists=${diagnostic.isFile}")
        if (images.isEmpty()) {
            InternalDiagnosticsLogger.logInteraction(context, "VISUAL_DEBUG_EXPORT_FAILED",
                "No verified PNG files to export; capture pipeline requires investigation")
            Toast.makeText(context, "No screenshots saved. Check visual debug capture diagnostics.", Toast.LENGTH_LONG).show()
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(context, "Bundle export requires Android 10 or newer", Toast.LENGTH_LONG).show()
            return false
        }
        val filename = "driverade_debug_bundle_${System.currentTimeMillis()}.zip"
        val resolver = context.contentResolver
        var uri: Uri? = null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create download")
            val target = uri ?: error("Missing export URI")
            resolver.openOutputStream(target)?.use { stream ->
                ZipOutputStream(stream).use { zip ->
                    fun add(file: File, name: String) {
                        zip.putNextEntry(ZipEntry(name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    if (diagnostic.isFile) add(diagnostic, diagnostic.name)
                    images.forEach { image ->
                        val valid = image.inputStream().use { input ->
                            val header = ByteArray(8)
                            input.read(header) == 8 && header.contentEquals(
                                byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
                        }
                        check(valid) { "Invalid PNG: ${image.name}" }
                        add(image, "screenshots/${image.name}")
                    }
                    zip.putNextEntry(ZipEntry("README.txt"))
                    zip.write(("Contains ${images.size} unredacted screenshots. " +
                        "These may reveal rider information or locations. Review before sharing. " +
                        "No automatic upload occurs.\\n").toByteArray())
                    zip.closeEntry()
                }
            } ?: error("Cannot open ZIP output stream")
            val actualEntries = resolver.openInputStream(target)?.use { stream ->
                java.util.zip.ZipInputStream(stream).use { zip ->
                    val names = mutableListOf<String>()
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        names.add(entry.name)
                        val buffer = ByteArray(8192)
                        while (zip.read(buffer) != -1) { /* verify readable entry and CRC */ }
                        zip.closeEntry()
                    }
                    names
                }
            } ?: error("Cannot reopen ZIP for verification")
            val included = actualEntries.count { it.startsWith("screenshots/") && it.endsWith(".png") }
            check(included == images.size && actualEntries.contains("README.txt")) {
                "ZIP screenshot count mismatch expected=${images.size} actual=$included"
            }
            resolver.update(target, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            InternalDiagnosticsLogger.logInteraction(context, "VISUAL_DEBUG_EXPORT_VERIFIED",
                "screenshots=$included logIncluded=${diagnostic.isFile} zipEntries=${actualEntries.size}")
            Toast.makeText(context, "Verified Downloads/$filename ($included screenshots)", Toast.LENGTH_LONG).show()
            true
        } catch (error: Exception) {
            uri?.let { resolver.delete(it, null, null) }
            InternalDiagnosticsLogger.logInteraction(context, "VISUAL_DEBUG_EXPORT_FAILED",
                "reason=${error.javaClass.simpleName} message=${error.message}")
            Toast.makeText(context, "Screenshot ZIP export failed: ${error.message}", Toast.LENGTH_LONG).show()
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
