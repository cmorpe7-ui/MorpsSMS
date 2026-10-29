package com.realme.spamblocker.sms.data.importer

import android.content.Context
import android.provider.Telephony
import com.realme.spamblocker.sms.data.local.AppDatabase
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import com.realme.spamblocker.sms.ml.BayesClassifier
import com.realme.spamblocker.sms.util.ContactUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsSyncManager {

    /**
     * Synchronizes SMS messages from Android's system Telephony database (content://sms).
     * If [forceFullSync] is true, checks up to 1000 messages and avoids duplicates.
     * Otherwise, performs an incremental sync importing messages newer than the latest stored message.
     */
    private var isSyncing = false

    suspend fun syncExistingSmsIfNeeded(
        context: Context,
        database: AppDatabase,
        forceFullSync: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (isSyncing) return@withContext
        isSyncing = true
        try {
            val messageDao = database.messageDao()
            val wordStatDao = database.wordStatDao()

        val latestTimestamp = if (forceFullSync) null else messageDao.getLatestTimestamp()

        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ
        )

        val selection = if (latestTimestamp != null && latestTimestamp > 0) {
            "${Telephony.Sms.DATE} > ?"
        } else {
            null
        }
        val selectionArgs = if (latestTimestamp != null && latestTimestamp > 0) {
            arrayOf(latestTimestamp.toString())
        } else {
            null
        }

        val cursor = try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT 1000"
            )
        } catch (e: Exception) {
            null
        } ?: return@withContext

        val settings = com.realme.spamblocker.sms.data.local.prefs.AppSettingsManager.getInstance(context)
        val threshold = settings.spamThreshold.value
        val whitelist = settings.whitelistContacts.value
        val phShield = settings.philippineShield.value

        // Re-evaluate existing messages with updated user settings if forceFullSync requested
        if (forceFullSync) {
            val allStored = messageDao.getAllMessagesList()
            val blocked = database.blockedNumberDao().getAllBlockedNumbersList().map { it.phoneNumber }.toSet()
            for (msg in allStored) {
                if (!msg.isOutgoing) {
                    val isExplicitlyBlocked = blocked.contains(msg.sender)
                    val isContact = ContactUtils.isContact(context, msg.sender)
                    val isOtp = com.realme.spamblocker.sms.util.SmartMessageAnalyzer.extractOtp(msg.body) != null
                    val (newProb, newSpam) = when {
                        isExplicitlyBlocked -> Pair(1.0f, true)
                        isOtp -> Pair(0.0f, false)
                        isContact && whitelist -> Pair(0.0f, false)
                        else -> {
                            val score = BayesClassifier.predict(wordStatDao, msg.body, phShield, msg.sender)
                            Pair(score, score >= threshold)
                        }
                    }
                    if (msg.isSpam != newSpam || kotlin.math.abs(msg.spamProbability - newProb) > 0.01f) {
                        messageDao.updateMessageSpamScore(msg.id, newSpam, newProb)
                    }
                }
            }
        }

        val existingTimestamps = if (latestTimestamp != null && latestTimestamp > 0) {
            messageDao.getTimestampsSince(latestTimestamp - 5000L).toHashSet()
        } else {
            messageDao.getAllTimestamps().toHashSet()
        }

        val messagesToInsert = mutableListOf<MessageEntity>()
        val localContactCache = mutableMapOf<String, com.realme.spamblocker.sms.util.ContactDetails>()

        cursor.use {
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val address = if (addressIndex != -1) it.getString(addressIndex) ?: "Unknown" else "Unknown"
                val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                val date = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                val type = if (typeIndex != -1) it.getInt(typeIndex) else Telephony.Sms.MESSAGE_TYPE_INBOX
                val isRead = if (readIndex != -1) it.getInt(readIndex) == 1 else true

                // Instant in-memory check without touching database
                if (existingTimestamps.contains(date)) {
                    continue
                }
                existingTimestamps.add(date)

                val isOutgoing = (type == Telephony.Sms.MESSAGE_TYPE_SENT)
                val contactDetails = localContactCache.getOrPut(address) {
                    ContactUtils.getContactDetails(context, address)
                }
                val isContact = if (!isOutgoing) contactDetails.isContact else true
                val contactName = contactDetails.name
                val photoUri = contactDetails.photoUri

                // Calculate spam probability based on user preferences
                var isSpam = false
                var spamProbability = 0.0f
                val isOtp = com.realme.spamblocker.sms.util.SmartMessageAnalyzer.extractOtp(body) != null
                if (!isOutgoing && !isOtp && (!isContact || !whitelist) && body.isNotBlank()) {
                    spamProbability = BayesClassifier.predict(wordStatDao, body, phShield, address)
                    isSpam = spamProbability >= threshold
                }

                messagesToInsert.add(
                    MessageEntity(
                        sender = address,
                        senderName = contactName,
                        body = body,
                        timestamp = date,
                        isSpam = isSpam,
                        spamProbability = spamProbability,
                        isRead = isRead,
                        isOutgoing = isOutgoing,
                        contactPhotoUri = photoUri
                    )
                )
            }
        }

        if (messagesToInsert.isNotEmpty()) {
            messageDao.insertAll(messagesToInsert)
        }
        } finally {
            isSyncing = false
        }
    }
}
