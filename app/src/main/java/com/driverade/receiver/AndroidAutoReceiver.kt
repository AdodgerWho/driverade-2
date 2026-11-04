package com.driverade.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AndroidAutoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.driverade.ACTION_OFFER_EVALUATED") {
            val platform = intent.getStringExtra("platform") ?: "UNKNOWN"
            val payout = intent.getDoubleExtra("payout", 0.0)
            val hourlyRate = intent.getDoubleExtra("hourly_rate", 0.0)
            Log.d("AndroidAutoReceiver", "Offer synced [$platform]: $$payout ($${hourlyRate}/hr)")
        }
    }
}
