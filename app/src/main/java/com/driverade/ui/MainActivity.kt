package com.driverade.ui

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.driverade.R
import com.driverade.data.DriverPreferences
import com.driverade.service.DriveradeState
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: DriverPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_dashboard)
        prefs = DriverPreferences(this)

        val switchLogger = findViewById<SwitchMaterial>(R.id.switchLogger)
        val switchAlert = findViewById<SwitchMaterial>(R.id.switchAlert)
        val tvStatusIndicator = findViewById<TextView>(R.id.tvStatusIndicator)
        val tvTargetSub = findViewById<TextView>(R.id.tvTargetSub)
        val tvTimingSub = findViewById<TextView>(R.id.tvTimingSub)

        fun refreshSubtitles() {
            tvTargetSub?.text = "Min $${DriveradeState.targetRateSetting}/hr  •  $${DriveradeState.minPayoutSetting} floor"
            tvTimingSub?.text = "Debounce ${prefs.customDebounceMs}ms  •  Poll ${prefs.customPollingMs}ms"
        }

        fun styleStatusBadge(text: String, textColor: Int, bgColor: Int, strokeColor: Int) {
            if (tvStatusIndicator == null) return
            tvStatusIndicator.text = text
            tvStatusIndicator.setTextColor(textColor)
            val gd = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(bgColor)
                setStroke(2, strokeColor)
            }
            tvStatusIndicator.background = gd
        }

        fun updateUIState() {
            val mode = DriveradeState.currentMode
            val isLoggerActive = mode == DriveradeState.OperatingMode.LOGGER_ONLY || mode == DriveradeState.OperatingMode.ALERT_AND_LOGGER
            val isAlertActive = mode == DriveradeState.OperatingMode.ALERT_ONLY || mode == DriveradeState.OperatingMode.ALERT_AND_LOGGER

            switchLogger?.isChecked = isLoggerActive
            switchAlert?.isChecked = isAlertActive

            when (mode) {
                DriveradeState.OperatingMode.OFF -> {
                    styleStatusBadge("● STANDBY", 0xFFE5484D.toInt(), 0x22E5484D, 0x55E5484D)
                }
                DriveradeState.OperatingMode.LOGGER_ONLY -> {
                    styleStatusBadge("● LOGGER ACTIVE", 0xFF4FC3F7.toInt(), 0x224FC3F7, 0x554FC3F7)
                }
                DriveradeState.OperatingMode.ALERT_ONLY -> {
                    styleStatusBadge("● ALERT ARMED", 0xFFF5A524.toInt(), 0x22F5A524, 0x55F5A524)
                }
                DriveradeState.OperatingMode.ALERT_AND_LOGGER -> {
                    styleStatusBadge("● ACTIVE & ARMED", 0xFF2ECC71.toInt(), 0x222ECC71, 0x552ECC71)
                }
            }
            refreshSubtitles()
        }

        switchLogger?.setOnCheckedChangeListener { _, isChecked ->
            val alertActive = DriveradeState.currentMode == DriveradeState.OperatingMode.ALERT_ONLY || DriveradeState.currentMode == DriveradeState.OperatingMode.ALERT_AND_LOGGER
            val newMode = when {
                isChecked && alertActive -> DriveradeState.OperatingMode.ALERT_AND_LOGGER
                isChecked -> DriveradeState.OperatingMode.LOGGER_ONLY
                alertActive -> DriveradeState.OperatingMode.ALERT_ONLY
                else -> DriveradeState.OperatingMode.OFF
            }
            DriveradeState.setMode(newMode)
            updateUIState()
        }

        switchAlert?.setOnCheckedChangeListener { _, isChecked ->
            val loggerActive = DriveradeState.currentMode == DriveradeState.OperatingMode.LOGGER_ONLY || DriveradeState.currentMode == DriveradeState.OperatingMode.ALERT_AND_LOGGER
            val newMode = when {
                isChecked && loggerActive -> DriveradeState.OperatingMode.ALERT_AND_LOGGER
                isChecked -> DriveradeState.OperatingMode.ALERT_ONLY
                loggerActive -> DriveradeState.OperatingMode.LOGGER_ONLY
                else -> DriveradeState.OperatingMode.OFF
            }
            DriveradeState.setMode(newMode)
            updateUIState()
        }

        updateUIState()

        findViewById<View>(R.id.btnThresholds)?.setOnClickListener {
            showThresholdsDialog { refreshSubtitles() }
        }

        findViewById<View>(R.id.btnAdvancedSettings)?.setOnClickListener {
            showAdvancedTimingsDialog { refreshSubtitles() }
        }

        findViewById<View>(R.id.btnLoggerView)?.setOnClickListener {
            showDiagnosticsDialog()
        }
    }

    private fun showThresholdsDialog(onSaved: () -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 36, 56, 16)
        }

        val rateInput = EditText(this).apply {
            hint = "Target $/hr (e.g. 32.0)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(DriveradeState.targetRateSetting.toString())
        }

        val payoutInput = EditText(this).apply {
            hint = "Min Payout $ (e.g. 10.0)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(DriveradeState.minPayoutSetting.toString())
        }

        layout.addView(TextView(this).apply { text = "Minimum $/hr:" })
        layout.addView(rateInput)
        layout.addView(TextView(this).apply { text = "Minimum Payout ($):" })
        layout.addView(payoutInput)

        AlertDialog.Builder(this)
            .setTitle("Thresholds & Targets")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                DriveradeState.targetRateSetting =
                    rateInput.text.toString().toDoubleOrNull() ?: DriveradeState.targetRateSetting
                DriveradeState.minPayoutSetting =
                    payoutInput.text.toString().toDoubleOrNull() ?: DriveradeState.minPayoutSetting
                onSaved()
                Toast.makeText(this, "Thresholds saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdvancedTimingsDialog(onSaved: () -> Unit) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 36, 56, 16)
        }

        val debounceInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.customDebounceMs.toString())
        }

        val debounceSeek = SeekBar(this).apply {
            max = 1900
            progress = (prefs.customDebounceMs - 100L).toInt().coerceIn(0, 1900)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) debounceInput.setText((p + 100).toString())
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        val pollingInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.customPollingMs.toString())
        }

        val pollingSeek = SeekBar(this).apply {
            max = 1900
            progress = (prefs.customPollingMs - 100L).toInt().coerceIn(0, 1900)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) pollingInput.setText((p + 100).toString())
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        layout.addView(TextView(this).apply { text = "Debounce Delay (ms, 100-2000):" })
        layout.addView(debounceSeek)
        layout.addView(debounceInput)
        layout.addView(TextView(this).apply { text = "Polling Interval (ms, 100-2000):" })
        layout.addView(pollingSeek)
        layout.addView(pollingInput)

        AlertDialog.Builder(this)
            .setTitle("Advanced Timing Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                prefs.customDebounceMs =
                    debounceInput.text.toString().toLongOrNull() ?: DriverPreferences.DEFAULT_DEBOUNCE_MS
                prefs.customPollingMs =
                    pollingInput.text.toString().toLongOrNull() ?: DriverPreferences.DEFAULT_TIMED_MS
                onSaved()
                Toast.makeText(this, "Timings updated", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Accessibility Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDiagnosticsDialog() {
        val msg = "Current Mode: ${DriveradeState.currentMode}\n" +
            "Target Rate: $${DriveradeState.targetRateSetting}/hr\n" +
            "Min Payout: $${DriveradeState.minPayoutSetting}\n" +
            "Debounce: ${prefs.customDebounceMs} ms\n" +
            "Polling: ${prefs.customPollingMs} ms"

        AlertDialog.Builder(this)
            .setTitle("Loggers & Diagnostics")
            .setMessage(msg)
            .setPositiveButton("Open Accessibility") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
