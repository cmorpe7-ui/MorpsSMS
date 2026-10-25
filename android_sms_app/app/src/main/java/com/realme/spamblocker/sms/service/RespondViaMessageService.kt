package com.realme.spamblocker.sms.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Required service for Default SMS app qualification in Android.
 * Handles quick response messages when rejecting phone calls via text.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
}
