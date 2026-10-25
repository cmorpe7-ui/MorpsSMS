package com.realme.spamblocker.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.realme.spamblocker.sms.data.local.AppDatabase
import com.realme.spamblocker.sms.data.repository.SmsRepository
import com.realme.spamblocker.sms.util.ContactUtils
import com.realme.spamblocker.sms.util.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Intercepts incoming SMS when this app is the Default SMS App.
 * Evaluates the message with on-device Bayesian model.
 * If spam: silently saves to the 'Spam & Filter' tab (No notification sound).
 * If ham: saves to Inbox and shows notification.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val fullBody = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages.firstOrNull()?.originatingAddress ?: "Unknown"

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repository = SmsRepository(db, context)

                // 1. Check if contact is whitelisted & resolve contact details
                val contactDetails = ContactUtils.getContactDetails(context, sender)
                val isContact = contactDetails.isContact
                val contactName = contactDetails.name
                val photoUri = contactDetails.photoUri

                // 2. Classify and Save
                val savedMessage = repository.saveIncomingMessage(
                    sender = sender,
                    senderName = contactName,
                    body = fullBody,
                    isContact = isContact,
                    contactPhotoUri = photoUri
                )

                // 3. If legitimate (Ham): write to Android system provider and notify
                if (!savedMessage.isSpam) {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, sender)
                            put(Telephony.Sms.BODY, fullBody)
                            put(Telephony.Sms.DATE, savedMessage.timestamp)
                            put(Telephony.Sms.READ, 0)
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                        }
                        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    } catch (_: Exception) {
                        // Handled safely
                    }

                    val displayName = contactName ?: sender
                    NotificationUtils.showNotification(context, displayName, fullBody)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
