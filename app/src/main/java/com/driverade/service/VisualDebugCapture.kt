package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

/**
 * Live Uber OCR is local and transient even when visual-debug PNG saving is off.
 * Opt-in screenshots are persisted only when explicitly enabled.
 * Capture is not gated on OCR keywords: inaccessible cards must still be read.
 */
internal class VisualDebugCapture(private val service: AccessibilityService) {
    private val preferences = service.getSharedPreferences("driverade_visual_debug", 0)
    private val directory = File(service.filesDir, "visual_debug")
    private val busy = AtomicBoolean(false)
    private val captureExecutor = Executors.newSingleThreadExecutor()
    private var lastCaptureElapsed = 0L
    private var serial = 0L
    private val intervalMs = 1500L
    companion object { const val MAX_FILES = 100 }
    private val maxFiles = MAX_FILES

    fun isEnabled(): Boolean = preferences.getBoolean("enabled", false)

    fun observe(event: AccessibilityEvent, pkg: String, captureId: String) {
        if (!isEnabled() && pkg != "com.ubercab.driver") return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            log("UNSUPPORTED", "api=${Build.VERSION.SDK_INT}")
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        val target = OptimizedOfferEngine.PACKAGE_PLATFORM_MAP.containsKey(pkg) ||
            (GuidedSettingsState.isActive(service) &&
                (pkg == "com.android.settings" || pkg == "com.google.android.projection.gearhead"))
        if (!target) return
        capture(pkg, captureId, "EVENT")
    }

    /** Periodic recovery from missed / too-early Uber accessibility events. */
    fun observeTimedUber(captureId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            capture("com.ubercab.driver", captureId, "TIMED")
    }

    private var uberOcrListener: ((String, String) -> Unit)? = null
    fun setUberOcrListener(listener: (String, String) -> Unit) {
        uberOcrListener = listener
    }
    fun close() {
        uberOcrListener = null
        captureExecutor.shutdownNow()
    }

    private fun capture(pkg: String, captureId: String, trigger: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureElapsed < intervalMs || !busy.compareAndSet(false, true)) return
        lastCaptureElapsed = now
        val saveDebug = isEnabled()
        log("REQUESTED", "capture=" + captureId + " package=" + pkg +
            " trigger=" + trigger + " savePng=" + saveDebug)
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, captureExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onFailure(errorCode: Int) {
                        log("CAPTURE_FAILED", "capture=" + captureId + " package=" + pkg + " code=" + errorCode)
                        busy.set(false)
                    }
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        var bitmap: Bitmap? = null
                        try {
                            val buffer = result.hardwareBuffer
                            try {
                                val hardware = Bitmap.wrapHardwareBuffer(
                                    buffer, result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB))
                                    ?: error("Hardware buffer could not be mapped")
                                try { bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false) }
                                finally { hardware.recycle() }
                            } finally { buffer.close() }
                            val image = bitmap ?: error("Bitmap copy returned null")
                            log("CAPTURE_SUCCESS", "capture=" + captureId +
                                " width=" + image.width + " height=" + image.height +
                                " source=DISPLAY")
                            if (saveDebug) saveAndVerify(image, pkg, captureId)
                            if (pkg == "com.ubercab.driver") inspectUberImage(image, captureId)
                        } catch (error: Exception) {
                            log("CAPTURE_PROCESS_FAILED", "capture=" + captureId +
                                " reason=" + error.javaClass.simpleName)
                        } finally {
                            bitmap?.recycle()
                            busy.set(false)
                        }
                    }
                })
        } catch (error: Exception) {
            log("REQUEST_FAILED", "capture=" + captureId +
                " reason=" + error.javaClass.simpleName)
            busy.set(false)
        }
    }

    /**
     * Optional local-only OCR for diagnostic screenshots. This is a display capture,
     * NOT a verified Uber window crop. No OCR strings are persisted or uploaded.
     * The OCR candidate is never promoted to OfferEvaluation or alert dispatch.
     */
    private fun inspectUberImage(bitmap: Bitmap, captureId: String) {
        val copy = try { bitmap.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { null }
        if (copy == null) {
            log("OCR_FAILED", "capture=$captureId reason=BITMAP_COPY_FAILED")
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            recognizer.process(InputImage.fromBitmap(copy, 0))
                .addOnSuccessListener { result ->
                    val summary = UberScreenOcrDiagnostics.inspect(result.text)
                    InternalDiagnosticsLogger.logInteraction(service, "UBER_OCR_DIAGNOSTIC",
                        "capture=" + captureId + " " + summary.diagnostic())
                    uberOcrListener?.invoke(captureId, result.text)
                }
                .addOnFailureListener { error ->
                    log("OCR_FAILED", "capture=$captureId reason=${error.javaClass.simpleName}")
                }
                .addOnCompleteListener {
                    copy.recycle()
                    recognizer.close()
                }
        } catch (error: Exception) {
            copy.recycle()
            recognizer.close()
            log("OCR_FAILED", "capture=$captureId reason=${error.javaClass.simpleName}")
        }
    }

    private fun saveAndVerify(bitmap: Bitmap, pkg: String, captureId: String) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create image directory" }
        val safePkg = pkg.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val name = "${System.currentTimeMillis()}_${++serial}_${safePkg}.png"
        val temp = File(directory, "$name.tmp")
        val output = File(directory, name)
        try {
            FileOutputStream(temp).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encoder failed" }
                stream.fd.sync()
            }
            check(temp.length() > 8) { "Empty PNG file" }
            check(temp.renameTo(output)) { "Could not finalize PNG" }
            val valid = output.inputStream().use { input ->
                val signature = ByteArray(8)
                input.read(signature) == 8 && signature.contentEquals(
                    byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            }
            check(valid) { "Invalid PNG signature" }
            log("FILE_VERIFIED", "capture=$captureId file=${output.name} bytes=${output.length()}")
            val retained = directory.listFiles()?.filter { it.isFile && it.extension.equals("png", true) }
                ?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name }).orEmpty()
            val excess = retained.drop(maxFiles)
            var removed = 0
            var failed = 0
            excess.forEach { if (it.delete()) removed++ else failed++ }
            if (excess.isNotEmpty()) {
                log("RETENTION", "limit=$maxFiles before=${retained.size} removed=$removed failed=$failed remaining=${retained.size - removed}")
            }
        } catch (error: Exception) {
            temp.delete()
            output.delete()
            throw error
        }
    }

    private fun log(event: String, detail: String) {
        InternalDiagnosticsLogger.logInteraction(service, "VISUAL_DEBUG_$event", detail)
    }
}
