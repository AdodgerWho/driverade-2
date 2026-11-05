package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explicit opt-in debugging only. Captures target-app screen changes to private storage.
 * Screenshots are never automatically attached to reports or transmitted.
 */
internal class VisualDebugCapture(private val service: AccessibilityService) {
    private val preferences = service.getSharedPreferences("driverade_visual_debug", 0)
    private val directory = File(service.filesDir, "visual_debug")
    private var lastCaptureElapsed = 0L
    private var inFlight = false
    private var lastFingerprint = ""
    private val minIntervalMs = 1000L
    private val maxFiles = 100
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val offerWords = Regex("(?i)\\b(offer|trip|ride|delivery|accept|decline|pickup|dropoff|miles|mins|minutes)\\b")
    private val moneyPattern = Regex("\\$\\s*\\d+(?:\\.\\d{1,2})?")

    fun isEnabled(): Boolean = preferences.getBoolean("enabled", false)

    fun observe(event: AccessibilityEvent, pkg: String) {
        if (!isEnabled() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val allowed = pkg in OptimizedOfferEngine.PACKAGE_PLATFORM_MAP ||
            (GuidedSettingsState.isActive(service) &&
                (pkg == "com.android.settings" || pkg == "com.google.android.projection.gearhead"))
        if (!allowed || inFlight) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureElapsed < minIntervalMs) return
        val source = event.source
        val fingerprint = listOf(pkg, event.eventType.toString(),
            event.className?.toString().orEmpty(),
            source?.text?.toString().orEmpty(),
            source?.contentDescription?.toString().orEmpty()).joinToString("|")
        if (fingerprint == lastFingerprint &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        lastFingerprint = fingerprint
        lastCaptureElapsed = now
        inFlight = true
        service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val buffer = result.hardwareBuffer
                        try {
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer,
                                result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
                            val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            try {
                                if (softwareBitmap != null) {
                                    verifyAndSave(softwareBitmap, pkg)
                                } else log("CAPTURE_EMPTY", pkg)
                            } finally {
                                hardwareBitmap?.recycle()
                            }
                        } finally {
                            buffer.close()
                        }
                    } catch (error: Exception) {
                        log("CAPTURE_ERROR", error.javaClass.simpleName)
                    } catch (error: Exception) {
                        log("CAPTURE_ERROR", error.javaClass.simpleName)
                        inFlight = false
                    }
                }
                override fun onFailure(errorCode: Int) {
                    log("CAPTURE_BLOCKED", "$pkg code=$errorCode")
                    inFlight = false
                }
            })
    }

    private fun verifyAndSave(bitmap: Bitmap, pkg: String) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text
                val relevant = offerWords.containsMatchIn(text) && moneyPattern.containsMatchIn(text)
                if (relevant) {
                    try { save(bitmap, pkg) }
                    catch (error: Exception) { log("CAPTURE_ERROR", error.javaClass.simpleName) }
                } else {
                    log("CAPTURE_REJECTED", "OCR did not find an offer keyword and monetary amount")
                }
            }
            .addOnFailureListener { error ->
                log("OCR_ERROR", error.javaClass.simpleName)
            }
            .addOnCompleteListener {
                bitmap.recycle()
                inFlight = false
            }
    }

    private fun save(bitmap: Bitmap, pkg: String) {
        directory.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val safePkg = pkg.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(directory, "${stamp}_${safePkg}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        log("CAPTURE_SAVED", "${file.name} (${bitmap.width}x${bitmap.height})")
        directory.listFiles()?.sortedByDescending { it.lastModified() }
            ?.drop(maxFiles)?.forEach { it.delete() }
    }

    private fun log(event: String, details: String) {
        InternalDiagnosticsLogger.logInteraction(service, "VISUAL_DEBUG_$event", details)
    }
}
