package com.driverade.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.driverade.R
import com.driverade.data.DriverPreferences
import com.driverade.service.DriveradeState
import com.driverade.service.LoggerService
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: DriverPreferences
    private var telemetryText: TextView? = null
    private var subtitleText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_dashboard)
        prefs = DriverPreferences(this)
        DriveradeState.targetRateSetting = prefs.targetRate
        DriveradeState.minPayoutSetting = prefs.minPayout

        telemetryText = findViewById(R.id.tvNetEarnings)
        subtitleText = findViewById(R.id.tvActiveHours)

        DriveradeState.onStatsUpdated = {
            refreshTelemetry()
        }

        findViewById<View>(R.id.tvStatusIndicator)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val switchAlert = findViewById<SwitchMaterial>(R.id.switchAlert)
        val switchLogger = findViewById<SwitchMaterial>(R.id.switchLogger)

        val statusPill = findViewById<TextView>(R.id.tvStatusIndicator)
        val refreshSwitches: () -> Unit = {
            val mode = DriveradeState.currentMode
            val isLoggerActive = mode == DriveradeState.OperatingMode.LOGGER_ONLY || mode == DriveradeState.OperatingMode.ALERT_AND_LOGGER
            val isAlertActive = mode == DriveradeState.OperatingMode.ALERT_ONLY || mode == DriveradeState.OperatingMode.ALERT_AND_LOGGER
            
            switchLogger?.isChecked = isLoggerActive
            switchAlert?.isChecked = isAlertActive

            val (label, color) = when {
                isLoggerActive && isAlertActive -> "● ALERT + LOGGER" to 0xFF30A46C.toInt()
                isAlertActive -> "● ALERTER ACTIVE" to 0xFF30A46C.toInt()
                isLoggerActive -> "● LOGGER ACTIVE" to 0xFF3B82F6.toInt()
                else -> "● STANDBY" to 0xFFE5484D.toInt()
            }
            statusPill?.let { pill ->
                pill.text = label
                pill.setTextColor(color)
                pill.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 50f * resources.displayMetrics.density
                    setColor((color and 0x00FFFFFF) or 0x22000000)
                    setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), (color and 0x00FFFFFF) or 0x55000000)
                }
            }
        }

        DriveradeState.addListener(refreshSwitches)
        refreshSwitches()

        switchLogger?.setOnCheckedChangeListener { _, isChecked ->
            val alertActive = switchAlert?.isChecked ?: false
            val newMode = when {
                isChecked && alertActive -> DriveradeState.OperatingMode.ALERT_AND_LOGGER
                isChecked -> DriveradeState.OperatingMode.LOGGER_ONLY
                alertActive -> DriveradeState.OperatingMode.ALERT_ONLY
                else -> DriveradeState.OperatingMode.OFF
            }
            DriveradeState.updateOperatingMode(newMode)
        }

        switchAlert?.setOnCheckedChangeListener { _, isChecked ->
            val loggerActive = switchLogger?.isChecked ?: false
            val newMode = when {
                isChecked && loggerActive -> DriveradeState.OperatingMode.ALERT_AND_LOGGER
                isChecked -> DriveradeState.OperatingMode.ALERT_ONLY
                loggerActive -> DriveradeState.OperatingMode.LOGGER_ONLY
                else -> DriveradeState.OperatingMode.OFF
            }
            DriveradeState.updateOperatingMode(newMode)
        }

        findViewById<View>(R.id.cardEarnings)?.setOnClickListener { showEditEarningsDialog() }
        findViewById<View>(R.id.cardActiveTime)?.setOnClickListener { showEditHoursDialog() }
        findViewById<View>(R.id.btnThresholds)?.setOnClickListener { showThresholdsDialog() }
        findViewById<View>(R.id.btnAdvancedSettings)?.setOnClickListener { showAdvancedTimingsDialog() }
        findViewById<View>(R.id.btnLoggerView)?.setOnClickListener { showDiagnosticsDialog() }

        refreshTelemetry()
        refreshSubtitles()
    }

    private fun refreshTelemetry() {
        val earnings = prefs.todayEarnings
        val hours = prefs.todayActiveHours
        val rate = if (hours > 0) earnings / hours else 0.0

        val formatted = "$${String.format(Locale.US, "%.2f", earnings)}"
        telemetryText?.text = formatted

        val subtitle = "Active Shift: ${String.format(Locale.US, "%.2f", hours)} hrs • Effective Rate: \$${String.format(Locale.US, "%.2f", rate)}/hr"
        subtitleText?.text = subtitle
    }

    private fun refreshSubtitles() {
        findViewById<TextView>(R.id.tvTargetSub)?.text =
            String.format(Locale.US, "Min \$%.1f/hr  •  \$%.1f floor", prefs.targetRate, prefs.minPayout)
        findViewById<TextView>(R.id.tvTimingSub)?.text =
            "Debounce ${prefs.customDebounceMs}ms  •  Poll ${prefs.customPollingMs}ms"
    }

    private fun numberInput(value: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(value)
    }

    private fun twoFieldDialog(title: String, labelA: String, a: EditText, labelB: String, b: EditText, onSave: () -> Unit) {
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(TextView(context).apply { text = labelA })
            addView(a)
            addView(TextView(context).apply { text = labelB })
            addView(b)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ -> onSave(); refreshSubtitles() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditEarningsDialog() {
        val input = EditText(this).apply {
            setText(String.format(Locale.US, "%.2f", prefs.todayEarnings))
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Earnings")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                input.text.toString().toDoubleOrNull()?.let { prefs.todayEarnings = it }
                refreshTelemetry()
            }
            .show()
    }

    private fun showEditHoursDialog() {
        val input = EditText(this).apply {
            setText(String.format(Locale.US, "%.2f", prefs.todayActiveHours))
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Active Hours")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                input.text.toString().toDoubleOrNull()?.let { prefs.todayActiveHours = it }
                refreshTelemetry()
            }
            .show()
    }

    private fun showThresholdsDialog() {
        val rate = numberInput(prefs.targetRate.toString())
        val payout = numberInput(prefs.minPayout.toString())
        twoFieldDialog("Offer Thresholds", "Target rate (\$/hr)", rate, "Minimum payout (\$)", payout) {
            rate.text.toString().toDoubleOrNull()?.let { prefs.targetRate = it }
            payout.text.toString().toDoubleOrNull()?.let { prefs.minPayout = it }
            DriveradeState.targetRateSetting = prefs.targetRate
            DriveradeState.minPayoutSetting = prefs.minPayout
        }
    }

    private fun showAdvancedTimingsDialog() {
        val debounce = numberInput(prefs.customDebounceMs.toString())
        val polling = numberInput(prefs.customPollingMs.toString())
        twoFieldDialog("Advanced Timings", "Debounce (ms)", debounce, "Polling (ms)", polling) {
            debounce.text.toString().toDoubleOrNull()?.let { prefs.customDebounceMs = it.toLong() }
            polling.text.toString().toDoubleOrNull()?.let { prefs.customPollingMs = it.toLong() }
        }
    }

    private fun showDiagnosticsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Diagnostics & Logs")
            .setMessage("Export raw offer logs and screen parsed data for diagnostics?")
            .setPositiveButton("Export Logs") { _, _ ->
                exportLogs()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportLogs() {
        try {
            val logFile = File(cacheDir, "driverade_diagnostics.txt")
            val sb = StringBuilder()
            sb.append("Driverade Diagnostics - ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\n")
            sb.append("Target Rate: $").append(DriveradeState.targetRateSetting).append("\n")
            sb.append("Min Payout: $").append(DriveradeState.minPayoutSetting).append("\n")
            sb.append("Today Earnings: $").append(prefs.todayEarnings).append("\n")
            sb.append("Today Active Hours: ").append(prefs.todayActiveHours).append("\n")
            sb.append("UBER Map Total: ").append(prefs.getLastSeenMapTotal("UBER")).append("\n")
            sb.append("LYFT Map Total: ").append(prefs.getLastSeenMapTotal("LYFT")).append("\n")
            sb.append("HICH Map Total: ").append(prefs.getLastSeenMapTotal("HICH")).append("\n")

            logFile.writeText(sb.toString())

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Diagnostics Log"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
