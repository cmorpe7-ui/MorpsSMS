package com.realme.spamblocker.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required component for Default SMS app qualification in Android.
 * Handles WAP_PUSH_DELIVER intents for MMS.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS delivery handling if MMS support is expanded
    }
}
