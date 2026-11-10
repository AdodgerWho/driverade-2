package com.driverade.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.driverade.data.DriverPreferences
import com.driverade.databinding.FragmentDashboardBinding
import com.driverade.service.BugReportManager
import com.driverade.service.DriveradeState
import com.driverade.service.LoggerService
import com.driverade.service.OptimizedOfferEngine
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: FragmentDashboardBinding
    private lateinit var prefs: DriverPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = DriverPreferences(this)
        LoggerService.ensureDiagnosticsHeader(this, prefs)

        setupHeaderAndStatusPills()
        setupModularNavigation()
        setupThresholdControls()
        setupPlatformFilterBottomSheet()
        setupDiagnosticsAndBugReport()
        observeRealtimeOffers()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatusPills()
    }

    private fun setupHeaderAndStatusPills() {
        binding.tvVersionBadge.text = "Driverade v3.63"
        updatePermissionStatusPills()

        binding.pillAccessibilityStatus.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.pillOverlayStatus.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission active", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePermissionStatusPills() {
        val overlayEnabled = Settings.canDrawOverlays(this)
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        binding.pillAccessibilityStatus.text =
            if (accessibilityEnabled) "Accessibility: ON" else "Accessibility: OFF (Tap)"
        binding.pillOverlayStatus.text =
            if (overlayEnabled) "Overlay: ON" else "Overlay: OFF (Tap)"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${OptimizedOfferEngine::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedComponent)
    }

    private fun setupModularNavigation() {
        binding.navBtnDashboard.setOnClickListener { showSection(Section.DASHBOARD) }
        binding.navBtnHeatmaps.setOnClickListener {
            refreshHeatmapSummary()
            showSection(Section.HEATMAPS)
        }
        binding.navBtnSettings.setOnClickListener { showSection(Section.SETTINGS) }
        showSection(Section.DASHBOARD)
    }

    private enum class Section { DASHBOARD, HEATMAPS, SETTINGS }

    private fun showSection(section: Section) {
        binding.sectionDashboard.visibility = if (section == Section.DASHBOARD) View.VISIBLE else View.GONE
        binding.sectionHeatmaps.visibility = if (section == Section.HEATMAPS) View.VISIBLE else View.GONE
        binding.sectionSettings.visibility = if (section == Section.SETTINGS) View.VISIBLE else View.GONE
    }

    private fun setupThresholdControls() {
        binding.etTargetHourlyRate.setText(String.format(Locale.US, "%.2f", prefs.targetHourlyRate))
        binding.etMinPayout.setText(String.format(Locale.US, "%.2f", prefs.minPayout))

        binding.btnSaveThresholds.setOnClickListener {
            val target = binding.etTargetHourlyRate.text.toString().toDoubleOrNull() ?: 25.0
            val minPay = binding.etMinPayout.text.toString().toDoubleOrNull() ?: 10.0
            prefs.targetHourlyRate = target
            prefs.minPayout = minPay
            LoggerService.writeDiagnosticsSnapshot(
                this,
                prefs,
                listOfNotNull(DriveradeState.latestOffer.value)
            )
            Toast.makeText(this, "Saved v3.63 thresholds ($target/hr, min $$minPay)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPlatformFilterBottomSheet() {
        binding.btnOpenPlatformSheet.setOnClickListener {
            val sheet = BottomSheetDialog(this)
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 64)
            }

            val header = TextView(this).apply {
                text = "Active Rideshare & Delivery Platforms"
                textSize = 18f
                setPadding(0, 0, 0, 24)
            }
            container.addView(header)

            val platforms = listOf(
                "UBER" to "Uber Driver (Rideshare)",
                "UBER_EATS" to "Uber Eats (Delivery)",
                "LYFT" to "Lyft Driver",
                "HICH" to "Hich",
                "DOORDASH" to "DoorDash Dasher",
                "GRUBHUB" to "Grubhub for Drivers"
            )

            platforms.forEach { (key, label) ->
                val sw = Switch(this).apply {
                    text = label
                    isChecked = prefs.isPlatformEnabled(key)
                    setPadding(0, 20, 0, 20)
                    setOnCheckedChangeListener { _, isChecked ->
                        prefs.setPlatformEnabled(key, isChecked)
                    }
                }
                container.addView(sw)
            }

            sheet.setContentView(container)
            sheet.show()
        }
    }

    private fun refreshHeatmapSummary() {
        val grid = DriveradeState.getRollingGridDensity()
        binding.tvHeatmapStatus.text = if (grid.isEmpty()) {
            "Rolling 5-Min Geohash Pool: 0 active cells (On-device coordinate blurring active)"
        } else {
            grid.entries.joinToString("\n") { "${it.key}: ${it.value} validated offers" }
        }
    }

    private fun setupDiagnosticsAndBugReport() {
        binding.btnSendBugReport.setOnClickListener {
            val feedbackInput = EditText(this).apply {
                hint = "Describe what happened (e.g., Uber card missed)..."
            }
            AlertDialog.Builder(this)
                .setTitle("Send Diagnostics to Support")
                .setMessage("Attaches driverade_diagnostics.txt and emails adodgerwho@gmail.com")
                .setView(feedbackInput)
                .setPositiveButton("Send Email") { _, _ ->
                    val intent = BugReportManager.createBugReportEmailIntent(
                        this,
                        prefs,
                        feedbackInput.text.toString()
                    )
                    startActivity(Intent.createChooser(intent, "Send Diagnostics Bug Report"))
                }
                .setNeutralButton("Export Locally") { _, _ ->
                    BugReportManager.exportLogsLocally(this, prefs)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeRealtimeOffers() {
        lifecycleScope.launch {
            DriveradeState.latestOffer.collectLatest { offer ->
                if (offer != null) {
                    binding.tvLatestOfferHeadline.text = String.format(
                        Locale.US,
                        "%s: $%.2f/hr ($%.2f)",
                        offer.platform,
                        offer.hourlyRate,
                        offer.payout
                    )
                    binding.tvLatestOfferSubtext.text = String.format(
                        Locale.US,
                        "%.1f mi • %.0f min • $%.2f/mi",
                        offer.totalMiles,
                        offer.totalMinutes,
                        offer.dollarsPerMile
                    )
                }
            }
        }
    }
}
