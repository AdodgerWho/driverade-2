package com.driverade.service

import android.os.Handler
import android.os.Looper

object DriveradeState {
    enum class OperatingMode {
        OFF,
        ALERT_ONLY,
        LOGGER_ONLY,
        ALERT_AND_LOGGER
    }

    private var _currentMode: OperatingMode = OperatingMode.OFF
    var currentMode: OperatingMode
        get() = _currentMode
        set(value) {
            _currentMode = value
            notifyStatsChanged()
        }

    fun updateOperatingMode(mode: OperatingMode) {
        _currentMode = mode
        notifyStatsChanged()
    }

    var targetRateSetting: Double = 25.0
    var minPayoutSetting: Double = 10.0

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyStatsChanged() {
        Handler(Looper.getMainLooper()).post {
            for (listener in listeners) {
                try {
                    listener()
                } catch (e: Exception) {}
            }
        }
    }
}
