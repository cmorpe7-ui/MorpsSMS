package com.realme.spamblocker.sms.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.realme.spamblocker.sms.data.local.AppDatabase
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsSender {

    suspend fun sendAndSaveSms(
        context: Context,
        database: AppDatabase,
        recipient: String,
        body: String,
        subId: Int
    ): Result<MessageEntity> = withContext(Dispatchers.IO) {
        try {
            // 1. Resolve SmsManager (with Dual-SIM subscription if selected)
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val base = context.getSystemService(SmsManager::class.java)
                if (subId != -1) base.createForSubscriptionId(subId) else base
            } else {
                @Suppress("DEPRECATION")
                if (subId != -1) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
            }

            // Split long message if >160 chars
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(recipient, null, body, null, null)
            }

            val timestamp = System.currentTimeMillis()
            val contactDetails = ContactUtils.getContactDetails(context, recipient)
            val contactName = contactDetails.name
            val photoUri = contactDetails.photoUri

            // 2. Save into Room DB as Outgoing
            val entity = MessageEntity(
                sender = recipient,
                senderName = contactName,
                body = body,
                timestamp = timestamp,
                isSpam = false,
                spamProbability = 0.0f,
                isRead = true,
                isOutgoing = true,
                subId = subId,
                contactPhotoUri = photoUri
            )
            val id = database.messageDao().insertMessage(entity)

            // 3. Write to Android's Telephony.Sms.Sent provider
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, recipient)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, timestamp)
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    if (subId != -1) {
                        put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                    }
                }
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (_: Exception) {
                // If not default SMS app yet, write will be safely caught
            }

            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
