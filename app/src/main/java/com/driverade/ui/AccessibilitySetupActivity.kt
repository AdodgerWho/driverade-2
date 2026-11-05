package com.driverade.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.driverade.service.GuidedSettingsState
import com.driverade.service.OptimizedOfferEngine

/** User-operated setup with automatic advancement only for verifiable permission state. */
class AccessibilitySetupActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private var step = 0
    private val handler = Handler(Looper.getMainLooper())
    private val steps = listOf(
        "1 / 4  Open Android Accessibility settings",
        "2 / 4  Find Installed apps / Downloaded apps, then Driverade",
        "3 / 4  Enable Driverade's accessibility service and confirm the Android prompt",
        "4 / 4  Verify the service is enabled"
    )
    private val poll = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (enabled() && step < 3) {
                step = 3
                GuidedSettingsState.setStep(this@AccessibilitySetupActivity, step)
            }
            render()
            handler.postDelayed(this, 1200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step") ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 64, 40, 40)
        }
        status = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER; minHeight = 240 }
        val open = Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener {
                if (step == 0) step = 1
                updateGuide()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val back = Button(this).apply {
            text = "Previous"
            setOnClickListener { step = (step - 1).coerceAtLeast(0); updateGuide() }
        }
        val next = Button(this).apply {
            text = "Next (manual)"
            setOnClickListener { step = (step + 1).coerceAtMost(3); updateGuide() }
        }
        val finish = Button(this).apply {
            text = "Verify / Finish"
            setOnClickListener {
                if (enabled()) finish() else render()
            }
        }
        root.addView(status)
        root.addView(open)
        root.addView(back)
        root.addView(next)
        root.addView(finish)
        setContentView(root)
        updateGuide()
    }

    override fun onResume() {
        super.onResume()
        GuidedSettingsState.setGuide(this, "accessibility")
        GuidedSettingsState.setActive(this, true)
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        if (isFinishing) GuidedSettingsState.setActive(this, false)
        super.onDestroy()
    }

    private fun enabled(): Boolean {
        val expected = ComponentName(this, OptimizedOfferEngine::class.java)
        val raw = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return raw.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun updateGuide() {
        GuidedSettingsState.setGuide(this, "accessibility")
        GuidedSettingsState.setStep(this, step)
        GuidedSettingsState.setTargetSeen(this, false)
        render()
    }

    private fun render() {
        if (!::status.isInitialized) return
        val active = enabled()
        status.text = steps[step] + when {
            active -> "\n\n✓ Driverade accessibility: ENABLED"
            GuidedSettingsState.targetSeen(this) -> "\n\n◎ Target located. Follow the highlighted control."
            else -> "\n\n○ Waiting for the required change. You can use Next manually."
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", step)
        super.onSaveInstanceState(outState)
    }
}
