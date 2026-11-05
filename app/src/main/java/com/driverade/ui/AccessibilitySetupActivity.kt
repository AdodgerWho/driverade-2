package com.driverade.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.driverade.service.OptimizedOfferEngine

/** Explicit, user-operated Accessibility setup walkthrough. */
class AccessibilitySetupActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private var step = 0
    private val steps = listOf(
        "1 / 4  ⚙  Open Android Accessibility settings",
        "2 / 4  🔎  Find Installed apps or Downloaded apps, then Driverade",
        "3 / 4  👆  Select Driverade and turn on its accessibility service. Confirm the Android permission prompt.",
        "4 / 4  ✓  Return to Driverade to verify that the service is enabled."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step") ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 64, 40, 40)
        }
        status = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER; minHeight = 240 }
        val open = Button(this).apply {
            text = "⚙  Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val back = Button(this).apply {
            text = "← Previous"
            setOnClickListener { step = (step - 1).coerceAtLeast(0); render() }
        }
        val next = Button(this).apply {
            text = "Next →"
            setOnClickListener { step = (step + 1).coerceAtMost(3); render() }
        }
        val finish = Button(this).apply {
            text = "✓  Verify / Finish"
            setOnClickListener {
                if (enabled()) finish()
                else render()
            }
        }
        root.addView(status)
        root.addView(open)
        root.addView(back)
        root.addView(next)
        root.addView(finish)
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) render()
    }

    private fun enabled(): Boolean {
        val expected = ComponentName(this, OptimizedOfferEngine::class.java)
        val raw = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return raw.split(':').any { flattened ->
            ComponentName.unflattenFromString(flattened) == expected
        }
    }

    private fun render() {
        status.text = steps[step] + if (enabled()) "\n\n✓ Driverade accessibility: ENABLED"
            else "\n\n○ Driverade accessibility: NOT ENABLED"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", step)
        super.onSaveInstanceState(outState)
    }
}
