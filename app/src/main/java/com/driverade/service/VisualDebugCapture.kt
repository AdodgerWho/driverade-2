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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in, on-device diagnostic capture. Capture is not gated on OCR keywords:
 * inaccessible offer cards must still be captured for investigation.
 */
internal class VisualDebugCapture(private val service: AccessibilityService) {
    private val preferences = service.getSharedPreferences("driverade_visual_debug", 0)
    private val directory = File(service.filesDir, "visual_debug")
    private val busy = AtomicBoolean(false)
    private var lastCaptureElapsed = 0L
    private var serial = 0L
    private val intervalMs = 1500L
    private val maxFiles = 40

    fun isEnabled(): Boolean = preferences.getBoolean("enabled", false)

    fun observe(event: AccessibilityEvent, pkg: String) {
        if (!isEnabled()) return
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
        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureElapsed < intervalMs || !busy.compareAndSet(false, true)) return
        lastCaptureElapsed = now
        log("REQUESTED", "package=$pkg eventType=${event.eventType}")
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onFailure(errorCode: Int) {
                        log("CAPTURE_FAILED", "package=$pkg code=$errorCode")
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
                            log("CAPTURE_SUCCESS", "width=${image.width} height=${image.height}")
                            saveAndVerify(image, pkg)
                        } catch (error: Exception) {
                            log("SAVE_FAILED", "reason=${error.javaClass.simpleName}")
                        } finally {
                            bitmap?.recycle()
                            busy.set(false)
                        }
                    }
                })
        } catch (error: Exception) {
            log("REQUEST_FAILED", "reason=${error.javaClass.simpleName}")
            busy.set(false)
        }
    }

    private fun saveAndVerify(bitmap: Bitmap, pkg: String) {
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
            log("FILE_VERIFIED", "file=${output.name} bytes=${output.length()}")
            directory.listFiles()?.filter { it.isFile && it.extension.equals("png", true) }
                ?.sortedByDescending { it.lastModified() }?.drop(maxFiles)?.forEach { it.delete() }
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
