package com.driverade.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
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
    private val rolling = RollingOcrFrameBuffer()
    private var lastRollingTrigger = -100_000L
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
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val target = OptimizedOfferEngine.PACKAGE_PLATFORM_MAP.containsKey(pkg) ||
            (GuidedSettingsState.isActive(service) &&
                (pkg == "com.android.settings" || pkg == "com.google.android.projection.gearhead"))
        if (!target) return
        if (pkg == "com.ubercab.driver" && isEnabled() &&
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
             event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            signalRollingTrigger(captureId, "UBER_WINDOW_CHANGE")
        }
        capture(pkg, captureId, "EVENT", event.windowId)
    }

    /** Periodic recovery from missed / too-early Uber accessibility events. */
    fun observeTimedUber(captureId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            capture("com.ubercab.driver", captureId, "TIMED", -1)
    }

    /** Opt-in low-frequency history even before an offer event: no OCR until an interesting frame. */
    fun observePrerollUber(captureId: String) {
        if (isEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            capture("com.ubercab.driver", captureId, "PREROLL", -1)
    }

    /** Allows the timer to keep recording ten seconds after a detected offer. */
    fun hasActivePostroll(): Boolean =
        isEnabled() && rolling.postRollActive(SystemClock.elapsedRealtime())

    /** Called by the authoritative tree parser once it recognizes an actual offer. */
    fun onOfferRecognized(captureId: String) {
        signalRollingTrigger(captureId, "VERIFIED_TREE_OFFER")
    }

    private fun signalRollingTrigger(captureId: String, reason: String) {
        if (!isEnabled()) return
        try {
            captureExecutor.execute {
                if (isEnabled()) promoteFrames(captureId, reason)
                else rolling.clear()
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    /** Stops retaining and discards unexported in-memory frames immediately. */
    fun clearRollingBuffer() {
        rolling.clear()
        log("ROLLING_CLEARED", "pendingFrames=0 privateBufferDiscarded=true")
    }

    private fun promoteFrames(captureId: String, reason: String) {
        if (!isEnabled()) {
            rolling.clear()
            return
        }
        val now = SystemClock.elapsedRealtime()
        // Don't extend the ten-second post-roll indefinitely on repeated OCR/events.
        if (now - lastRollingTrigger < 8_000L && rolling.postRollActive(now)) return
        lastRollingTrigger = now
        val recent = rolling.promote(now)
        log("ROLLING_TRIGGER", "capture=" + captureId + " reason=" + reason +
            " preRollFrames=" + recent.size + " preRollMs=8000 postRollMs=10000 rawText=OMITTED")
        persistSelectedFrames(recent, "PRE_ROLL")
    }

    private fun persistSelectedFrames(frames: List<RollingOcrFrameBuffer.Frame>, phase: String) {
        if (!isEnabled()) {
            rolling.clear()
            return
        }
        frames.forEach { f ->
            val bitmap = BitmapFactory.decodeByteArray(f.jpeg, 0, f.jpeg.size)
            if (bitmap == null) {
                log("ROLLING_FRAME_FAILED", "capture=" + f.captureId + " reason=INVALID_BUFFER")
            } else {
                try {
                    saveAndVerify(bitmap, "com.ubercab.driver", f.captureId)
                    log("ROLLING_FRAME_SAVED", "capture=" + f.captureId +
                        " phase=" + phase + " source=" + f.source +
                        " ageMs=" + (SystemClock.elapsedRealtime() - f.timestampMs) +
                        " rawText=OMITTED")
                } catch (e: Exception) {
                    log("ROLLING_FRAME_FAILED", "capture=" + f.captureId +
                        " reason=" + e.javaClass.simpleName)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun stageRollingFrame(bitmap: Bitmap, pkg: String,
                                  captureId: String, source: String): Boolean {
        if (!isEnabled()) {
            rolling.clear()
            return false
        }
        var preview: Bitmap? = null
        try {
            // Keep only bounded compressed previews; run live OCR on the original bitmap.
            val width = bitmap.width.coerceAtMost(1080)
            val height = (bitmap.height.toLong() * width / bitmap.width).toInt().coerceAtLeast(1)
            preview = if (width == bitmap.width) bitmap else
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            val signature = IntArray(24 * 48) { index ->
                val x = index % 24 * preview.width / 24
                val y = index / 24 * preview.height / 48
                val color = preview.getPixel(x, y)
                val red = color shr 16 and 255
                val green = color shr 8 and 255
                val blue = color and 255
                (red * 3 + green * 6 + blue) / 10
            }
            val data = ByteArrayOutputStream()
            check(preview.compress(Bitmap.CompressFormat.JPEG, 85, data)) {
                "Could not encode temporary rolling frame"
            }
            val now = SystemClock.elapsedRealtime()
            val added = rolling.add(RollingOcrFrameBuffer.Frame(
                captureId, now, source, data.toByteArray(), signature))
            if (added.expiredFrames > 0)
                log("ROLLING_EXPIRED", "frames=" + added.expiredFrames + " rawText=OMITTED")
            if (added.toPersist.isNotEmpty())
                persistSelectedFrames(added.toPersist, "POST_ROLL")
            if (!added.duplicate && added.toPersist.isEmpty()) {
                log("ROLLING_STAGED", "capture=" + captureId +
                    " frames=" + rolling.pendingCount + " bufferBytes=" + rolling.pendingBytes +
                    " source=" + source + " rawText=OMITTED")
            }
            val changed = pkg == "com.ubercab.driver" &&
                added.meanDifference != null && added.meanDifference >= 22.0 &&
                (source == "WINDOW" || source == "DISPLAY_FALLBACK")
            if (changed) promoteFrames(captureId, "UBER_VISUAL_CHANGE")
            return changed
        } catch (e: Exception) {
            log("ROLLING_FRAME_FAILED", "capture=" + captureId +
                " reason=" + e.javaClass.simpleName)
            return false
        } finally {
            if (preview !== bitmap) preview?.recycle()
        }
    }

    private var uberOcrListener: ((String, String) -> Unit)? = null
    fun setUberOcrListener(listener: (String, String) -> Unit) {
        uberOcrListener = listener
    }
    fun close() {
        uberOcrListener = null
        rolling.clear()
        captureExecutor.shutdown()
    }

    /**
     * API 34+ prefers a specific Uber window. Older devices and invalidated
     * windows can fall back to display capture with an explicit provenance tag.
     * Never retry a secure-window or access-denied failure via display capture.
     */
    private fun capture(pkg: String, captureId: String, trigger: String, eventWindowId: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureElapsed < intervalMs || !busy.compareAndSet(false, true)) return
        lastCaptureElapsed = now
        val saveDebug = isEnabled()
        val chosen = if (pkg == "com.ubercab.driver" && Build.VERSION.SDK_INT >= 34) {
            val list = runCatching { service.windows.orEmpty() }.getOrDefault(emptyList())
            list.firstOrNull { it.id == eventWindowId &&
                runCatching { it.root?.packageName?.toString() == pkg }.getOrDefault(false) }
                ?: list.filter {
                    runCatching { it.root?.packageName?.toString() == pkg }.getOrDefault(false)
                }.maxByOrNull { it.layer }
        } else null
        log("REQUESTED", "capture=" + captureId + " package=" + pkg +
            " trigger=" + trigger + " savePng=" + saveDebug +
            " targetWindow=" + (chosen?.id ?: "NA"))
        fun callback(source: String): AccessibilityService.TakeScreenshotCallback =
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onFailure(errorCode: Int) {
                    log("CAPTURE_FAILED", "capture=" + captureId + " source=" + source +
                        " package=" + pkg + " code=" + errorCode)
                    if (source == "WINDOW" &&
                        (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW ||
                         errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR)) {
                        log("WINDOW_FALLBACK", "capture=" + captureId +
                            " reason=WINDOW_UNAVAILABLE trying=DISPLAY")
                        try {
                            service.takeScreenshot(Display.DEFAULT_DISPLAY,
                                captureExecutor, callback("DISPLAY_FALLBACK"))
                        } catch (e: Exception) {
                            log("CAPTURE_FAILED", "capture=" + captureId +
                                " source=DISPLAY_FALLBACK reason=" + e.javaClass.simpleName)
                            busy.set(false)
                        }
                    } else busy.set(false)
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
                            " source=" + source + " windowId=" + (chosen?.id ?: "NA") +
                            " pngOptIn=" + saveDebug)
                        val visualChange = if (saveDebug && pkg == "com.ubercab.driver")
                            stageRollingFrame(image, pkg, captureId, source)
                        else {
                            if (!isEnabled()) rolling.clear()
                            if (saveDebug) saveAndVerify(image, pkg, captureId)
                            else log("PNG_SKIPPED", "capture=" + captureId +
                                " reason=VISUAL_DEBUG_DISABLED pngSaved=false " +
                                "transientUberOcr=" + (pkg == "com.ubercab.driver"))
                            false
                        }
                        if (pkg == "com.ubercab.driver" &&
                            (trigger != "PREROLL" || visualChange))
                            inspectUberImage(image, captureId, source)
                    } catch (error: Exception) {
                        log("CAPTURE_PROCESS_FAILED", "capture=" + captureId +
                            " reason=" + error.javaClass.simpleName)
                    } finally {
                        bitmap?.recycle()
                        busy.set(false)
                    }
                }
            }
        try {
            if (chosen != null) {
                service.takeScreenshotOfWindow(chosen.id, captureExecutor, callback("WINDOW"))
            } else {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, captureExecutor, callback("DISPLAY"))
            }
        } catch (error: Exception) {
            log("REQUEST_FAILED", "capture=" + captureId +
                " reason=" + error.javaClass.simpleName)
            busy.set(false)
        }
    }

    /**
     * OCR input is transient and app-local. It may originate from a window
     * screenshot or (if unavailable) the display. The parser must enforce
     * its own UberX/action/complete-leg checks before publishing an offer.
     */
    private fun inspectUberImage(bitmap: Bitmap, captureId: String, captureSource: String) {
        val copy = try { bitmap.copy(Bitmap.Config.ARGB_8888, false) } catch (_: Exception) { null }
        if (copy == null) {
            log("OCR_FAILED", "capture=$captureId reason=BITMAP_COPY_FAILED")
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        log("OCR_REQUESTED", "capture=" + captureId +
            " provider=ML_KIT rawText=TRANSIENT rawOcrTextPersisted=false")
        try {
            recognizer.process(InputImage.fromBitmap(copy, 0))
                .addOnSuccessListener { result ->
                    log("OCR_COMPLETED", "capture=" + captureId +
                        " textBlocks=" + result.textBlocks.size +
                        " listenerConnected=" + (uberOcrListener != null) +
                        " rawText=OMITTED")
                    val summary = UberScreenOcrDiagnostics.inspect(result.text)
                    InternalDiagnosticsLogger.logInteraction(service, "UBER_OCR_DIAGNOSTIC",
                        "capture=" + captureId + " " + summary.diagnostic(captureSource))
                    if (summary.likelyOfferCard) signalRollingTrigger(captureId, "OCR_OFFER_CANDIDATE")
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
