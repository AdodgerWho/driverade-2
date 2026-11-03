package com.driverade.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log

class AndroidAutoReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "DriveradeAutoReceiver"
        private const val ACTION_CAR_CONNECTION_CHANGED = "androidx.car.app.connection.action.CAR_CONNECTION_STATE"
        private const val EXTRA_CAR_CONNECTION_STATE = "androidx.car.app.connection.extra.CAR_CONNECTION_STATE"
        private const val CONNECTION_STATE_CONNECTED = 2
        private const val CONNECTION_STATE_DISCONNECTED = 1
        private const val ACTION_PROJECTION_STATE = "android.car.projection.ACTION_PROJECTION_STATE"
        private const val EXTRA_PROJECTION_ACTIVE = "android.car.projection.EXTRA_PROJECTION_ACTIVE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // Apps can't toggle Wi-Fi on Android 10+; setWifiEnabled is a no-op there.
        if (action != Intent.ACTION_BOOT_COMPLETED && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) return
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        val prefs = context.getSharedPreferences("driverade_wifi_prefs", Context.MODE_PRIVATE)

        when (action) {
            ACTION_CAR_CONNECTION_CHANGED -> {
                val state = intent.getIntExtra(EXTRA_CAR_CONNECTION_STATE, -1)
                if (state == CONNECTION_STATE_CONNECTED) handleCarConnected(wifiManager, prefs)
                else if (state == CONNECTION_STATE_DISCONNECTED) handleCarDisconnected(wifiManager, prefs)
            }
            ACTION_PROJECTION_STATE -> {
                val active = intent.getBooleanExtra(EXTRA_PROJECTION_ACTIVE, false)
                if (active) handleCarConnected(wifiManager, prefs)
                else handleCarDisconnected(wifiManager, prefs)
            }
            Intent.ACTION_BOOT_COMPLETED -> prefs.edit().putBoolean("wifi_toggled_by_auto", false).apply()
        }
    }

    private fun handleCarConnected(wifiManager: WifiManager, prefs: android.content.SharedPreferences) {
        try {
            @Suppress("DEPRECATION")
            val isWifiEnabled = wifiManager.isWifiEnabled
            val alreadyToggled = prefs.getBoolean("wifi_toggled_by_auto", false)
            if (isWifiEnabled && !alreadyToggled) {
                prefs.edit().putBoolean("previous_wifi_state", true).putBoolean("wifi_toggled_by_auto", true).apply()
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = false
                Log.i(TAG, "Android Auto connected: Disabled Wi-Fi to stabilize stream.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Wi-Fi", e)
        }
    }

    private fun handleCarDisconnected(wifiManager: WifiManager, prefs: android.content.SharedPreferences) {
        try {
            val alreadyToggled = prefs.getBoolean("wifi_toggled_by_auto", false)
            val previousState = prefs.getBoolean("previous_wifi_state", false)
            if (alreadyToggled) {
                if (previousState) {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                    Log.i(TAG, "Android Auto disconnected: Reverted Wi-Fi state.")
                }
                prefs.edit().putBoolean("wifi_toggled_by_auto", false).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring Wi-Fi", e)
        }
    }
}
