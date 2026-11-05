package com.morps.sms.data.importer

import android.content.Context
import android.provider.Telephony
import com.morps.sms.data.local.AppDatabase
import com.morps.sms.data.local.entity.MessageEntity
import com.morps.sms.ml.BayesClassifier
import com.morps.sms.util.ContactUtils
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

        val limitClause = if (forceFullSync) "10000" else "5000"
        val cursor = try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT $limitClause"
            )
        } catch (e: Exception) {
            null
        } ?: return@withContext

        val settings = com.morps.sms.data.local.prefs.AppSettingsManager.getInstance(context)
        val threshold = settings.spamThreshold.value
        val whitelist = settings.whitelistContacts.value
        val phShield = settings.philippineShield.value

        val existingKeys = if (latestTimestamp != null && latestTimestamp > 0) {
            messageDao.getMessageKeysSince(latestTimestamp - 5000L).toHashSet()
        } else {
            messageDao.getAllMessageKeys().toHashSet()
        }

        val messagesToInsert = mutableListOf<MessageEntity>()
        val localContactCache = mutableMapOf<String, com.morps.sms.util.ContactDetails>()

        cursor.use {
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val address = if (addressIndex != -1) it.getString(addressIndex) ?: "Unknown" else "Unknown"
                val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                val rawDate = if (dateIndex != -1) it.getLong(dateIndex) else System.currentTimeMillis()
                val date = if (rawDate in 1L..99999999999L) rawDate * 1000L else if (rawDate > 0) rawDate else System.currentTimeMillis()
                val type = if (typeIndex != -1) it.getInt(typeIndex) else Telephony.Sms.MESSAGE_TYPE_INBOX
                val isRead = if (readIndex != -1) it.getInt(readIndex) == 1 else true

                val messageKey = "$address:$date"
                // Instant in-memory check without touching database
                if (existingKeys.contains(messageKey)) {
                    continue
                }
                existingKeys.add(messageKey)

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
                if (!isOutgoing && (!isContact || !whitelist) && body.isNotBlank()) {
                    val threatReason = if (phShield) BayesClassifier.getThreatReason(body, address) else null
                    val rawOtp = com.morps.sms.util.SmartMessageAnalyzer.extractOtp(body)
                    val hasUrl = body.contains("http://", ignoreCase = true) ||
                            body.contains("https://", ignoreCase = true) ||
                            body.contains("www.", ignoreCase = true)
                    val isAuthenticOtp = rawOtp != null && threatReason == null && !hasUrl

                    if (threatReason != null) {
                        isSpam = true
                        spamProbability = 0.95f
                    } else if (isAuthenticOtp) {
                        isSpam = false
                        spamProbability = 0.0f
                    } else {
                        spamProbability = BayesClassifier.predict(wordStatDao, body, phShield, address)
                        isSpam = spamProbability >= threshold
                    }
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
            // Reverse so oldest message is inserted first (lowest auto-increment id)
            // and newest message is inserted last (highest id), keeping id order aligned with timestamp.
            messagesToInsert.reverse()
            messageDao.insertAll(messagesToInsert)
        }

        // Re-evaluate existing messages with updated user settings & AI models if forceFullSync requested
        if (forceFullSync) {
            val allStored = messageDao.getAllMessagesList()
            val blocked = database.blockedNumberDao().getAllBlockedNumbersList().map { it.phoneNumber }.toSet()
            val confirmedSpamSenders = mutableSetOf<String>()

            for (msg in allStored) {
                if (!msg.isOutgoing) {
                    val isExplicitlyBlocked = blocked.contains(msg.sender)
                    val isContact = ContactUtils.isContact(context, msg.sender)
                    val threatReason = if (phShield) BayesClassifier.getThreatReason(msg.body, msg.sender) else null
                    val rawOtp = com.morps.sms.util.SmartMessageAnalyzer.extractOtp(msg.body)
                    val hasUrl = msg.body.contains("http://", ignoreCase = true) ||
                            msg.body.contains("https://", ignoreCase = true) ||
                            msg.body.contains("www.", ignoreCase = true)
                    val isAuthenticOtp = rawOtp != null && threatReason == null && !hasUrl

                    val (newProb, newSpam) = when {
                        isExplicitlyBlocked -> Pair(1.0f, true)
                        isContact && whitelist -> Pair(0.0f, false)
                        threatReason != null -> Pair(0.95f, true)
                        isAuthenticOtp -> Pair(0.0f, false)
                        else -> {
                            val score = BayesClassifier.predict(wordStatDao, msg.body, phShield, msg.sender)
                            Pair(score, score >= threshold)
                        }
                    }
                    if (newSpam && threatReason != null) {
                        confirmedSpamSenders.add(msg.sender)
                    }
                    if (msg.isSpam != newSpam || kotlin.math.abs(msg.spamProbability - newProb) > 0.01f) {
                        messageDao.updateMessageSpamScore(msg.id, newSpam, newProb)
                    }
                }
            }

            // If a sender sent confirmed threat/phishing/gambling spam, ensure their thread is cleanly moved to spam
            for (spamSender in confirmedSpamSenders) {
                messageDao.updateThreadSpamStatus(spamSender, isSpam = true)
            }
        }
        } finally {
            isSyncing = false
        }
    }
}
