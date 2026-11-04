package com.driverade.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.driverade.data.DriverPreferences
import com.driverade.databinding.FragmentDashboardBinding
import com.driverade.service.LoggerService

class MainActivity : AppCompatActivity() {
    private lateinit var binding: FragmentDashboardBinding
    private lateinit var prefs: DriverPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = DriverPreferences(this)
        LoggerService.ensureDiagnosticsHeader(this, prefs)

        binding.tvVersionBadge.text = "Driverade v3.62"
        binding.pillAccessibilityStatus.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
