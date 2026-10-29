package com.realme.spamblocker.sms.receiver

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.realme.spamblocker.sms.SpamBlockerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SpamBlockerApp
        val repository = app.repository
        val sender = intent.getStringExtra(EXTRA_SENDER) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, sender.hashCode())

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            ACTION_COPY_OTP -> {
                val otpCode = intent.getStringExtra(EXTRA_OTP_CODE) ?: return
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP Code", otpCode)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied OTP $otpCode to clipboard", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    // Safe fallback
                }
            }
            ACTION_MARK_READ -> {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.markThreadAsRead(sender)
                    notificationManager.cancel(notificationId)
                }
            }
            ACTION_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val replyText = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()

                if (!replyText.isNullOrBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            repository.sendOutgoingSms(context, sender, replyText, -1)
                            repository.markThreadAsRead(sender)
                            notificationManager.cancel(notificationId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_COPY_OTP = "com.realme.spamblocker.sms.ACTION_COPY_OTP"
        const val ACTION_MARK_READ = "com.realme.spamblocker.sms.ACTION_MARK_READ"
        const val ACTION_REPLY = "com.realme.spamblocker.sms.ACTION_REPLY"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_OTP_CODE = "extra_otp_code"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }
}
