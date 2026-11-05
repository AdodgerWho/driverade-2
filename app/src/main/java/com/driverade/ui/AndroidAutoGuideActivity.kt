package com.driverade.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.driverade.service.GuidedSettingsState

/** User-controlled walkthrough; Android Auto developer flags are not publicly queryable. */
class AndroidAutoGuideActivity : Activity() {
    private var step = 0
    private val instructions = listOf(
        "1 / 5  Open Android Auto settings (not App info)",
        "2 / 5  Tap Version repeatedly until developer mode is enabled",
        "3 / 5  Open the menu and select Developer settings",
        "4 / 5  Find Wireless Android Auto and turn it OFF",
        "5 / 5  Connect by USB and verify the connection"
    )
    private lateinit var title: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step") ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 64, 40, 40)
        }
        title = TextView(this).apply { textSize = 22f; gravity = Gravity.CENTER; minHeight = 240 }
        val open = Button(this).apply {
            text = "Open Android Auto settings"
            setOnClickListener {
                // Prefer Android Auto's own settings activity; never open its App info page.
                val direct = Intent("com.google.android.projection.gearhead.settings").apply {
                    setPackage("com.google.android.projection.gearhead")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launched = runCatching { startActivity(direct) }.isSuccess
                if (!launched) {
                    runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    title.text = "Android Auto settings shortcut unavailable. In Settings, search for Android Auto and open its settings.\n\n" + instructions[step]
                } else if (step == 0) {
                    step = 1
                    render()
                }
            }
        }
        val back = Button(this).apply {
            text = "Previous"
            setOnClickListener { step = (step - 1).coerceAtLeast(0); render() }
        }
        val next = Button(this).apply {
            text = "I completed this step"
            setOnClickListener { step = (step + 1).coerceAtMost(4); render() }
        }
        val stop = Button(this).apply {
            text = "Finish / Stop guidance"
            setOnClickListener { finish() }
        }
        root.addView(title)
        root.addView(open)
        root.addView(back)
        root.addView(next)
        root.addView(stop)
        setContentView(root)
        render()
    }

    private fun render() {
        title.text = instructions[step] + if (GuidedSettingsState.targetSeen(this)) {
            "\n\n◎ Target recognized on screen. Complete the action to continue."
        } else {
            "\n\nAdvance manually after completing each action. Android does not expose these developer flags for reliable verification."
        }
        GuidedSettingsState.setGuide(this, "auto")
        GuidedSettingsState.setStep(this, step)
        GuidedSettingsState.setTargetSeen(this, false)
    }

    override fun onResume() {
        super.onResume()
        GuidedSettingsState.setGuide(this, "auto")
        GuidedSettingsState.setActive(this, true)
        if (::title.isInitialized) render()
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
