package com.driverade.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.text.InputType
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
import com.driverade.service.FeatureDiagnostics
import com.driverade.service.DriveradeState
import com.driverade.service.DeveloperInspection
import com.driverade.service.LoggerService
import com.driverade.service.InternalDiagnosticsLogger
import com.driverade.service.OptimizedOfferEngine
import com.driverade.service.ShiftTracker
import com.driverade.service.SyntheticTestCatalog
import com.driverade.service.TipLedger
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: FragmentDashboardBinding
    private lateinit var prefs: DriverPreferences

    private var comparisonResultReceiverRegistered = false
    private var pendingComparisonRequestId: String? = null
    private var activeBatch: List<SyntheticTestCatalog.Case>? = null
    private val batchOutcomes = mutableListOf<SyntheticTestCatalog.Outcome>()
    private var batchIndex = 0
    private val batchHandler = Handler(Looper.getMainLooper())
    private var batchTimeout: Runnable? = null
    private val batchTimeoutMillis = 8_000L
    private val comparisonResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != OptimizedOfferEngine.ACTION_TEST_OFFER_RESULT) return
            val id = intent.getStringExtra(OptimizedOfferEngine.EXTRA_TEST_REQUEST_ID)
            if (id == null || id != pendingComparisonRequestId) return
            pendingComparisonRequestId = null
            batchTimeout?.let { batchHandler.removeCallbacks(it) }
            batchTimeout = null
            val result = intent.getStringExtra(OptimizedOfferEngine.EXTRA_TEST_RESULT_TEXT)
                ?: "No comparison details were returned."
            val activeCases = activeBatch
            if (activeCases != null) {
                val testCase = activeCases[batchIndex]
                batchOutcomes.add(SyntheticTestCatalog.Outcome(
                    testCase,
                    intent.getStringExtra(OptimizedOfferEngine.EXTRA_TEST_RESULT_STATUS) ?: "UNKNOWN",
                    intent.getStringExtra(OptimizedOfferEngine.EXTRA_TEST_RESULT_PROVENANCE),
                    result,
                    if (intent.hasExtra(OptimizedOfferEngine.EXTRA_TEST_PAYOUT))
                        intent.getDoubleExtra(OptimizedOfferEngine.EXTRA_TEST_PAYOUT, Double.NaN) else null,
                    if (intent.hasExtra(OptimizedOfferEngine.EXTRA_TEST_MINUTES))
                        intent.getDoubleExtra(OptimizedOfferEngine.EXTRA_TEST_MINUTES, Double.NaN) else null,
                    if (intent.hasExtra(OptimizedOfferEngine.EXTRA_TEST_MILES))
                        intent.getDoubleExtra(OptimizedOfferEngine.EXTRA_TEST_MILES, Double.NaN) else null
                ))
                batchIndex += 1
                batchHandler.post { runNextSyntheticCase() }
            } else {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Parser comparison (synthetic)")
                    .setMessage(result)
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }

    private val shiftHandler = Handler(Looper.getMainLooper())
    private val shiftUpdateRunnable = object : Runnable {
        override fun run() {
            updateDashboardShiftAndStatus()
            shiftHandler.postDelayed(this, 300_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = DriverPreferences(this)
        LoggerService.ensureDiagnosticsHeader(this, prefs)

        setupHeaderAndStatusPills()
        setupModularNavigation()
        setupOperatingModesAndStats()
        setupAlertPillTest()
        setupSyntheticOfferTest()
        setupThresholdControls()
        setupPlatformFilterBottomSheet()
        setupDiagnosticsAndBugReport()
        setupVisualDebugCapture()
        setupDeveloperInspection()
        observeRealtimeOffers()
    }

    override fun onResume() {
        super.onResume()
        if (!comparisonResultReceiverRegistered) {
            val filter = IntentFilter(OptimizedOfferEngine.ACTION_TEST_OFFER_RESULT)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(comparisonResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(comparisonResultReceiver, filter)
            }
            comparisonResultReceiverRegistered = true
        }
        updatePermissionStatusPills()
        updateDashboardShiftAndStatus()
        refreshTodayStats()
        updateDeveloperInspectionStatus()
        shiftHandler.post(shiftUpdateRunnable)
    }

    override fun onPause() {
        if (comparisonResultReceiverRegistered) {
            unregisterReceiver(comparisonResultReceiver)
            comparisonResultReceiverRegistered = false
        }
        pendingComparisonRequestId = null
        cancelSyntheticBatch()
        super.onPause()
        shiftHandler.removeCallbacks(shiftUpdateRunnable)
    }

    private fun setupHeaderAndStatusPills() {
        binding.tvVersionBadge?.text = "Driverade"
        updatePermissionStatusPills()

        binding.pillAccessibilityStatus?.setOnClickListener {
            startActivity(Intent(this, AccessibilitySetupActivity::class.java))
        }

        binding.pillOverlayStatus?.setOnClickListener {
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

        binding.pillAccessibilityStatus?.text =
            if (accessibilityEnabled) "Accessibility: ON" else "Accessibility: OFF (Tap)"
        binding.pillOverlayStatus?.text =
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
        binding.navBtnDashboard?.setOnClickListener { showSection(Section.DASHBOARD) }
        binding.navBtnHeatmaps?.setOnClickListener {
            refreshHeatmapSummary()
            showSection(Section.HEATMAPS)
        }
        binding.navBtnSettings?.setOnClickListener { showSection(Section.SETTINGS) }
        binding.btnAccessibilityGuide?.setOnClickListener { startActivity(Intent(this, AccessibilitySetupActivity::class.java)) }
        binding.btnAndroidAutoGuide?.setOnClickListener { startActivity(Intent(this, AndroidAutoGuideActivity::class.java)) }
        showSection(Section.DASHBOARD)
    }

    private enum class Section { DASHBOARD, HEATMAPS, SETTINGS }

    private fun showSection(section: Section) {
        binding.sectionDashboard?.visibility = if (section == Section.DASHBOARD) View.VISIBLE else View.GONE
        binding.sectionHeatmaps?.visibility = if (section == Section.HEATMAPS) View.VISIBLE else View.GONE
        binding.sectionSettings?.visibility = if (section == Section.SETTINGS) View.VISIBLE else View.GONE
        listOf(
            binding.navBtnDashboard to (section == Section.DASHBOARD),
            binding.navBtnHeatmaps to (section == Section.HEATMAPS),
            binding.navBtnSettings to (section == Section.SETTINGS)
        ).forEach { (tab, selected) ->
            tab?.setBackgroundResource(
                if (selected) com.driverade.R.drawable.bg_nav_active
                else com.driverade.R.drawable.bg_nav_inactive
            )
            tab?.setTextColor(Color.parseColor(if (selected) "#E5FAFF" else "#B8C9DE"))
            tab?.contentDescription = "${tab?.text}, ${if (selected) "selected" else "not selected"}"
        }
    }

    private fun setupOperatingModesAndStats() {
        binding.switchAlert?.isChecked = prefs.alerterEnabled
        binding.switchAlert?.setOnCheckedChangeListener { _, checked ->
            prefs.alerterEnabled = checked
            refreshOperatingMode()
        }
        binding.btnEditTodayStats?.setOnClickListener { showEditTodayStats() }
        refreshOperatingMode()
        refreshTodayStats()
    }

    private fun refreshOperatingMode() {
        val mode = if (prefs.alerterEnabled) "ALERTS ACTIVE" else "MONITORING PAUSED"
        binding.tvOperatingMode?.text = mode
    }

    private fun refreshTodayStats() {
        val earnings = prefs.todayEarnings
        val hours = prefs.todayActiveHours
        val rate = if (hours > 0.0) earnings / hours else 0.0
        binding.tvTodayEarnings?.text = String.format(Locale.US, "$%.2f", earnings)
        binding.tvTodayActiveHours?.text = String.format(Locale.US, "%.2f active hours · $%.2f/hr", hours, rate)
        binding.tvTodayOffers?.text = "${prefs.todayOffersCount} offers · ${prefs.todayGoodOffersCount} qualified"
        binding.tvTodayTips?.text = String.format(Locale.US, "Tips observed today: $%.2f", TipLedger.totalToday(this))
        binding.tvTodayTipCount?.text = "${TipLedger.countToday(this)} explicit tips detected (provisional)"
    }

    private fun showEditTodayStats() {
        val earnings = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", prefs.todayEarnings))
            hint = "Today's earnings"
        }
        val hours = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", prefs.todayActiveHours))
            hint = "Active hours"
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 12, 40, 0)
            addView(TextView(this@MainActivity).apply { text = "Earnings ($)" })
            addView(earnings)
            addView(TextView(this@MainActivity).apply { text = "Active hours" })
            addView(hours)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit today's totals")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val amount = earnings.text.toString().toDoubleOrNull()
                val time = hours.text.toString().toDoubleOrNull()
                if (amount == null || time == null || !amount.isFinite() || !time.isFinite() || amount < 0 || time < 0) {
                    Toast.makeText(this, "Enter valid non-negative totals", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.todayEarnings = amount
                    prefs.todayActiveHours = time
                    refreshTodayStats()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupAlertPillTest() {
        binding.btnTestAlertPill?.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Enable Driverade Accessibility first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Enable overlay permission to test the pill", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            sendBroadcast(Intent(OptimizedOfferEngine.ACTION_TEST_ALERT_PILL).apply {
                setPackage(packageName)
            })
            Toast.makeText(this, "Sample alert requested", Toast.LENGTH_SHORT).show()
        }
    }


    private fun sendSyntheticTest(platform: String, nodes: String, requestId: String,
                                  source: String = "TREE") {
        pendingComparisonRequestId = requestId
        sendBroadcast(Intent(OptimizedOfferEngine.ACTION_TEST_OFFER_CARD).apply {
            setPackage(packageName)
            putExtra(OptimizedOfferEngine.EXTRA_TEST_PLATFORM, platform)
            putExtra(OptimizedOfferEngine.EXTRA_TEST_NODES, nodes)
            putExtra(OptimizedOfferEngine.EXTRA_TEST_REQUEST_ID, requestId)
            putExtra(OptimizedOfferEngine.EXTRA_TEST_SOURCE, source)
        })
    }

    private fun cancelSyntheticBatch() {
        batchTimeout?.let { batchHandler.removeCallbacks(it) }
        batchTimeout = null
        activeBatch = null
        batchOutcomes.clear()
        batchIndex = 0
    }

    private fun startSyntheticBatch() {
        if (activeBatch != null || pendingComparisonRequestId != null) {
            Toast.makeText(this, "A parser test is already running", Toast.LENGTH_SHORT).show()
            return
        }
        activeBatch = SyntheticTestCatalog.presets
        batchOutcomes.clear()
        batchIndex = 0
        runNextSyntheticCase()
    }

    private fun runNextSyntheticCase() {
        val cases = activeBatch ?: return
        if (batchIndex >= cases.size) {
            val summary = SyntheticTestCatalog.summary(batchOutcomes.toList(), cases.size)
            InternalDiagnosticsLogger.logInteraction(this, "SYNTHETIC_BATCH_SUMMARY",
                "total=${cases.size} expected=${batchOutcomes.count { it.meetsExpectation }} " +
                    "unexpected=${batchOutcomes.count { !it.meetsExpectation }} rawText=OMITTED")
            cancelSyntheticBatch()
            AlertDialog.Builder(this)
                .setTitle("Batch parser results (synthetic)")
                .setMessage(summary)
                .setPositiveButton("Close", null)
                .setNeutralButton("Share summary") { _, _ ->
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, summary)
                    }, "Share synthetic test summary"))
                }
                .show()
            return
        }
        val test = cases[batchIndex]
        val id = UUID.randomUUID().toString()
        sendSyntheticTest(test.platform, test.nodes, id, test.source)
        val timeout = Runnable {
            if (activeBatch !== cases || pendingComparisonRequestId != id) return@Runnable
            pendingComparisonRequestId = null
            batchOutcomes.add(SyntheticTestCatalog.Outcome(test, "TIMEOUT", null,
                "Service did not return a result within the timeout"))
            InternalDiagnosticsLogger.logInteraction(this, "SYNTHETIC_BATCH_TIMEOUT",
                "index=${batchIndex + 1} platform=${test.platform} rawText=OMITTED")
            batchIndex += 1
            batchTimeout = null
            runNextSyntheticCase()
        }
        batchTimeout = timeout
        batchHandler.postDelayed(timeout, batchTimeoutMillis)
    }

    private fun setupSyntheticOfferTest() {
        binding.btnTestOfferCard?.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Enable Driverade Accessibility to run parser tests", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val platforms = arrayOf("UBER", "LYFT", "HICH")
            val cases = SyntheticTestCatalog.presets
            var platform = "UBER"
            var selectedCase = 0
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 12, 36, 0)
            }
            val input = EditText(this).apply {
                minLines = 5
                maxLines = 12
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setText(cases[selectedCase].nodes)
                hint = "One captured text node per line"
            }
            lateinit var platformButton: android.widget.Button
            val caseButton = android.widget.Button(this).apply {
                text = "Scenario: " + cases[selectedCase].name
                setOnClickListener {
                    val choices = cases.map { it.name } + "Custom card text"
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Test card scenario")
                        .setItems(choices.toTypedArray()) { _, index ->
                            selectedCase = index
                            if (index < cases.size) {
                                input.setText(cases[index].nodes)
                                platform = cases[index].platform
                                platformButton.text = "Platform: $platform"
                            } else {
                                input.setText("")
                            }
                            text = "Scenario: " + choices[index]
                        }.show()
                }
            }
            platformButton = android.widget.Button(this).apply {
                text = "Platform: $platform"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Synthetic platform")
                        .setItems(platforms) { _, index ->
                            platform = platforms[index]
                            text = "Platform: $platform"
                        }.show()
                }
            }
            container.addView(TextView(this).apply {
                text = "SYNTHETIC ONLY: run one editable card or all built-in scenarios " +
                    "sequentially. No live offers, counters, overlays or alerts."
            })
            container.addView(platformButton)
            container.addView(caseButton)
            container.addView(input)
            AlertDialog.Builder(this)
                .setTitle("Inject and compare offer cards")
                .setView(container)
                .setPositiveButton("Run one") { _, _ ->
                    val raw = input.text.toString()
                    if (raw.isBlank() || raw.length > 6000) {
                        Toast.makeText(this, "Enter 1–6000 characters", Toast.LENGTH_LONG).show()
                    } else if (activeBatch != null || pendingComparisonRequestId != null) {
                        Toast.makeText(this, "A parser test is already running", Toast.LENGTH_SHORT).show()
                    } else {
                        sendSyntheticTest(platform, raw, UUID.randomUUID().toString(),
                            if (selectedCase < cases.size && raw == cases[selectedCase].nodes)
                                cases[selectedCase].source else "TREE")
                        Toast.makeText(this, "Comparing parsers; results saved to diagnostics", Toast.LENGTH_LONG).show()
                    }
                }
                .setNeutralButton("Run batch") { _, _ -> startSyntheticBatch() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupThresholdControls() {
        binding.etTargetHourlyRate?.setText(String.format(Locale.US, "%.2f", prefs.targetHourlyRate))
        binding.etMinPayout?.setText(String.format(Locale.US, "%.2f", prefs.minPayout))

        binding.btnSaveThresholds?.setOnClickListener {
            val target = binding.etTargetHourlyRate?.text.toString().toDoubleOrNull() ?: 25.0
            val minPay = binding.etMinPayout?.text.toString().toDoubleOrNull() ?: 10.0
            prefs.targetHourlyRate = target
            prefs.minPayout = minPay
            LoggerService.writeDiagnosticsSnapshot(
                this,
                prefs,
                listOfNotNull(DriveradeState.latestOffer.value)
            )
            Toast.makeText(this, "Saved thresholds ($target/hr, min $$minPay)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPlatformFilterBottomSheet() {
        binding.btnOpenPlatformSheet?.setOnClickListener {
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
                        updateDashboardShiftAndStatus()
                    }
                }
                container.addView(sw)
            }

            sheet.setContentView(container)
            sheet.show()
        }
    }

    private fun updateDashboardShiftAndStatus() {
        val shiftHours = ShiftTracker.getFormattedShiftHours(prefs)
        val hasActivePlatforms = ShiftTracker.isAnyPlatformActive(prefs)

        val statusText = if (hasActivePlatforms) {
            "Shift Time: $shiftHours hrs • Status: Monitoring Active Apps"
        } else {
            "Shift Time: $shiftHours hrs • Status: All Platforms Paused"
        }

        binding.tvShiftTracker?.text = statusText
        refreshTodayStats()
    }

    private fun refreshHeatmapSummary() {
        val grid = DriveradeState.getRollingGridDensity()
        binding.tvHeatmapStatus?.text = if (grid.isEmpty()) {
            "Rolling 5-Min Geohash Pool: 0 active cells (On-device coordinate blurring active)"
        } else {
            grid.entries.joinToString("\n") { "${it.key}: ${it.value} validated offers" }
        }
    }

    private fun setupVisualDebugCapture() {
        val debugPrefs = getSharedPreferences("driverade_visual_debug", MODE_PRIVATE)
        binding.switchVisualDebug?.apply {
            isChecked = debugPrefs.getBoolean("enabled", false)
            setOnCheckedChangeListener { _, enabled ->
                if (enabled) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Enable local screenshots?")
                        .setMessage("Captures gig-app screens during debugging. Screens may contain names, addresses and other private information. Screenshots stay on this device, are not automatically shared, and only the newest 40 are retained.")
                        .setPositiveButton("Enable") { _, _ ->
                            debugPrefs.edit().putBoolean("enabled", true).apply()
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            debugPrefs.edit().putBoolean("enabled", false).apply()
                            isChecked = false
                        }
                        .show()
                } else {
                    debugPrefs.edit().putBoolean("enabled", false).apply()
                }
            }
        }
    }

    private fun updateDeveloperInspectionStatus() {
        val enabled = DeveloperInspection.isActive(this)
        binding.tvDevInspectionWarning?.text = if (enabled) {
            "WARNING: DEVELOPER INSPECTION ACTIVE (${DeveloperInspection.remainingMinutes(this)} min left). " +
                "Raw accessibility text and descriptions may contain unintended sensitive information, " +
                "including rider names, addresses, and other visible app content. Private file on device."
        } else {
            "DEVELOPER INSPECTION: OFF. When enabled, captures raw Uber window hierarchies. " +
                "May expose unintended sensitive information. Not for routine operation."
        }
        binding.btnDevInspectionToggle?.text =
            if (enabled) "Stop Developer Inspection" else "Start Developer Inspection (30 min)"
    }

    private fun setupDeveloperInspection() {
        updateDeveloperInspectionStatus()
        binding.btnDevInspectionToggle?.setOnClickListener {
            if (DeveloperInspection.isActive(this)) {
                DeveloperInspection.stop(this)
                updateDeveloperInspectionStatus()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("WARNING: Sensitive developer inspection")
                    .setMessage(
                        "This diagnostic tool records raw Uber accessibility view text, " +
                        "descriptions, identifiers, hierarchy, screen bounds and window metadata. " +
                        "It may expose unintended personal information such as rider names, " +
                        "pickup addresses, notifications, or details visible inside Uber. " +
                        "Only use on your own device while actively debugging.\\n\\n" +
                        "The session ends automatically after 30 minutes, with a 2 MB trace limit. " +
                        "Records remain in app-private storage until you delete them or begin a " +
                        "new session. They are NOT included in normal log or screenshot exports. " +
                        "Exporting makes an unencrypted copy in Downloads that other apps " +
                        "or people with access to the device may be able to read. " +
                        "No automatic uploads or parser changes occur."
                    )
                    .setPositiveButton("I understand — start") { _, _ ->
                        DeveloperInspection.start(this)
                        updateDeveloperInspectionStatus()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        binding.btnExportDevInspection?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("EXPORT RAW PRIVATE INFORMATION?")
                .setMessage(
                    "This exports UNREDACTED Uber accessibility text, descriptions, " +
                    "window hierarchies and bounds as a file in Downloads. " +
                    "It may expose rider information or other unintended private content. " +
                    "No automatic upload occurs; inspect and redact it before sharing."
                )
                .setPositiveButton("Export unredacted trace") { _, _ ->
                    BugReportManager.exportDeveloperInspection(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.btnClearDevInspection?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete private developer trace?")
                .setMessage(
                    "Stop the active inspection session and permanently delete its " +
                    "app-private raw trace. Previously exported Downloads files are not deleted."
                )
                .setPositiveButton("Stop and delete") { _, _ ->
                    DeveloperInspection.stop(this)
                    val deleted = DeveloperInspection.clear(this)
                    updateDeveloperInspectionStatus()
                    Toast.makeText(this,
                        if (deleted) "Developer trace deleted" else "Unable to delete developer trace",
                        Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupDiagnosticsAndBugReport() {
        binding.btnRunFeatureDiagnostics?.setOnClickListener {
            val checks = FeatureDiagnostics.run(this)
            AlertDialog.Builder(this)
                .setTitle("Feature Diagnostics")
                .setMessage(FeatureDiagnostics.format(checks))
                .setPositiveButton("Close", null)
                .show()
        }
        binding.btnSendBugReport?.setOnClickListener {
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
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnExportLogsLocally?.setOnClickListener {
            BugReportManager.exportLogsLocally(this, prefs)
        }

        binding.btnExportVisualDebugBundle?.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Export private screenshots?")
                .setMessage("Saved screenshots may contain rider names, addresses, and other sensitive details. The ZIP is saved locally to Downloads. Review it before sharing.")
                .setPositiveButton("Export ZIP") { _, _ ->
                    BugReportManager.exportVisualDebugBundle(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeRealtimeOffers() {
        lifecycleScope.launch {
            DriveradeState.latestOffer.collectLatest { offer ->
                if (offer != null) {
                    binding.tvLatestOfferHeadline?.text = String.format(
                        Locale.US,
                        "%s: $%.2f/hr ($%.2f)",
                        offer.platform,
                        offer.hourlyRate,
                        offer.payout
                    )
                    binding.tvLatestOfferSubtext?.text = String.format(
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
