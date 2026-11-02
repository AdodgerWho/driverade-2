package com.driverade.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
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

        fun updateUIState() {
            val mode = DriveradeState.currentMode
            val isLoggerActive = mode == DriveradeState.OperatingMode.LOGGER_ONLY || mode == DriveradeState.OperatingMode.ALERT_AND_LOGGER
            val isAlertActive = mode == DriveradeState.OperatingMode.ALERT_ONLY || mode == DriveradeState.OperatingMode.ALERT_AND_LOGGER

            switchLogger?.isChecked = isLoggerActive
            switchAlert?.isChecked = isAlertActive

            if (tvStatusIndicator != null) {
                when (mode) {
                    DriveradeState.OperatingMode.OFF -> {
                        tvStatusIndicator.text = "● STANDBY"
                        tvStatusIndicator.setTextColor(0xFFE5484D.toInt())
                    }
                    DriveradeState.OperatingMode.LOGGER_ONLY -> {
                        tvStatusIndicator.text = "● LOGGER ACTIVE"
                        tvStatusIndicator.setTextColor(0xFF4FC3F7.toInt())
                    }
                    DriveradeState.OperatingMode.ALERT_ONLY -> {
                        tvStatusIndicator.text = "● ALERT ARMED"
                        tvStatusIndicator.setTextColor(0xFFF5A524.toInt())
                    }
                    DriveradeState.OperatingMode.ALERT_AND_LOGGER -> {
                        tvStatusIndicator.text = "● ACTIVE & ARMED"
                        tvStatusIndicator.setTextColor(0xFF2ECC71.toInt())
                    }
                }
            }
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

        findViewById<Button>(R.id.btnThresholds)?.setOnClickListener {
            showThresholdsDialog()
        }

        findViewById<Button>(R.id.btnAdvancedSettings)?.setOnClickListener {
            showAdvancedTimingsDialog()
        }

        findViewById<Button>(R.id.btnLoggerView)?.setOnClickListener {
            showDiagnosticsDialog()
        }
    }

    private fun showThresholdsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
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
                Toast.makeText(this, "Thresholds saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdvancedTimingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
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
