package com.driverade.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.driverade.service.GuidedSettingsState

/** User-controlled walkthrough. Never changes system settings automatically. */
class AndroidAutoGuideActivity : Activity() {
    private var step = 0
    private val instructions = listOf(
        "1 / 5  ⚙  Open Android Auto settings",
        "2 / 5  👆  Tap Version repeatedly to enable developer settings",
        "3 / 5  ⋮  Open the menu and select Developer settings",
        "4 / 5  Wi-Fi  Find Wireless Android Auto and turn it OFF",
        "5 / 5  USB  Connect the phone with a cable and verify the connection"
    )
    private lateinit var title: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step") ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 64, 40, 40)
        }
        title = TextView(this).apply { textSize = 25f; gravity = Gravity.CENTER; minHeight = 240 }
        val open = Button(this).apply {
            text = "⚙  Open Android Auto app settings"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:com.google.android.projection.gearhead"))
                runCatching { startActivity(intent) }
                    .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }
        val back = Button(this).apply { text = "←"; setOnClickListener { step = (step - 1).coerceAtLeast(0); render() } }
        val next = Button(this).apply { text = "→"; setOnClickListener { step = (step + 1).coerceAtMost(4); render() } }
        val stop = Button(this).apply { text = "✓  Finish / Stop guidance"; setOnClickListener { finish() } }
        root.addView(title)
        root.addView(open)
        root.addView(back)
        root.addView(next)
        root.addView(stop)
        setContentView(root)
        render()
    }

    private fun render() {
        title.text = instructions[step]
        GuidedSettingsState.setStep(this, step)
    }

    override fun onResume() {
        super.onResume()
        GuidedSettingsState.setActive(this, true)
    }

    override fun onDestroy() {
        if (isFinishing) GuidedSettingsState.setActive(this, false)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", step)
        super.onSaveInstanceState(outState)
    }
}
