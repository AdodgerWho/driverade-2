package com.driverade.service

object DriveradeState {
    enum class OperatingMode {
        OFF,
        LOGGER_ONLY,
        ALERT_ONLY,
        ALERT_AND_LOGGER
    }

    var currentMode: OperatingMode = OperatingMode.OFF
        private set

    var targetRateSetting: Double = 25.0
    var minPayoutSetting: Double = 10.0

    var onStateChanged: ((OperatingMode) -> Unit)? = null

    fun setMode(mode: OperatingMode) {
        currentMode = mode
        onStateChanged?.invoke(mode)
    }
}
