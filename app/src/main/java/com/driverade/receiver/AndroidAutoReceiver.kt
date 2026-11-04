package com.driverade.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AndroidAutoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.driverade.ACTION_OFFER_EVALUATED") {
            Log.d("AndroidAutoReceiver", "Offer broadcast received.")
        }
    }
}
