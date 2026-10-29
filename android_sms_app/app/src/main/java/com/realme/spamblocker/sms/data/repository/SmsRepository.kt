package com.realme.spamblocker.sms.data.repository

import android.content.Context
import android.provider.Telephony
import com.realme.spamblocker.sms.data.importer.SmsSyncManager
import com.realme.spamblocker.sms.data.local.AppDatabase
import com.realme.spamblocker.sms.data.local.entity.BlockedNumberEntity
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import com.realme.spamblocker.sms.data.local.entity.WordStatEntity
import com.realme.spamblocker.sms.data.local.prefs.AppSettingsManager
import com.realme.spamblocker.sms.ml.BayesClassifier
import com.realme.spamblocker.sms.util.SmsSender
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class SmsRepository(
    private val database: AppDatabase,
    private val context: Context? = null
) {

    private val messageDao = database.messageDao()
    private val wordStatDao = database.wordStatDao()
    private val blockedNumberDao = database.blockedNumberDao()

    val inboxMessages: Flow<List<MessageEntity>> = messageDao.getInboxConversations()
    val spamMessages: Flow<List<MessageEntity>> = messageDao.getSpamConversations()
    val starredMessages: Flow<List<MessageEntity>> = messageDao.getStarredMessages()
    val unreadCount: Flow<Int> = messageDao.getUnreadInboxCount()
    val spamCount: Flow<Int> = messageDao.getSpamCount()

    fun searchInbox(query: String): Flow<List<MessageEntity>> = messageDao.searchInbox(query)

    fun getConversationMessages(sender: String): Flow<List<MessageEntity>> =
        messageDao.getConversationMessages(sender)

    fun getBlockedNumbers(): Flow<List<BlockedNumberEntity>> = blockedNumberDao.getAllBlockedNumbers()

    suspend fun saveIncomingMessage(
        sender: String,
        senderName: String?,
        body: String,
        isContact: Boolean,
        contactPhotoUri: String? = null
    ): MessageEntity {
        // 1. Manual Blacklist check: If explicitly blocked, mark as spam immediately
        val isExplicitlyBlocked = blockedNumberDao.isBlocked(sender)

        // Read user-customized AI thresholds and whitelist preferences
        val settings = context?.let { AppSettingsManager.getInstance(it) }
        val threshold = settings?.spamThreshold?.value ?: 0.70f
        val whitelistContacts = settings?.whitelistContacts?.value ?: true
        val phShield = settings?.philippineShield?.value ?: true

        // 2. Never treat authentic OTP / verification codes as spam (unless explicitly blocked)
        val isOtp = com.realme.spamblocker.sms.util.SmartMessageAnalyzer.extractOtp(body) != null

        val (spamProbability, isSpam) = when {
            isExplicitlyBlocked -> Pair(1.0f, true)
            isOtp -> Pair(0.0f, false)
            isContact && whitelistContacts -> Pair(0.0f, false)
            else -> {
                val score = BayesClassifier.predict(wordStatDao, body, phShield, sender)
                Pair(score, score >= threshold)
            }
        }

        val entity = MessageEntity(
            sender = sender,
            senderName = senderName,
            body = body,
            timestamp = System.currentTimeMillis(),
            isSpam = isSpam,
            spamProbability = spamProbability,
            isRead = false,
            isOutgoing = false,
            contactPhotoUri = contactPhotoUri
        )

        val id = messageDao.insertMessage(entity)
        return entity.copy(id = id)
    }

    suspend fun sendOutgoingSms(
        context: Context,
        recipient: String,
        body: String,
        subId: Int
    ): Result<MessageEntity> {
        return SmsSender.sendAndSaveSms(context, database, recipient, body, subId)
    }

    suspend fun markAsSpam(message: MessageEntity) {
        messageDao.updateThreadSpamStatus(message.sender, isSpam = true)
        // Automatically add sender to blocked numbers blacklist
        blockedNumberDao.blockNumber(
            BlockedNumberEntity(
                phoneNumber = message.sender,
                reason = "Reported Spam"
            )
        )
        // Incremental active learning: Learn all messages in this conversation as Spam
        val threadMessages = messageDao.getMessagesBySender(message.sender)
        if (threadMessages.isNotEmpty()) {
            for (msg in threadMessages) {
                BayesClassifier.train(wordStatDao, msg.body, isSpam = true)
            }
        } else {
            BayesClassifier.train(wordStatDao, message.body, isSpam = true)
        }
    }

    suspend fun markAsNotSpam(message: MessageEntity) {
        messageDao.updateThreadSpamStatus(message.sender, isSpam = false)
        // Remove sender from blocked numbers blacklist
        blockedNumberDao.unblockByNumber(message.sender)
        // Incremental active learning: Recover false positive for all messages in thread
        val threadMessages = messageDao.getMessagesBySender(message.sender)
        if (threadMessages.isNotEmpty()) {
            for (msg in threadMessages) {
                BayesClassifier.untrainSpamAndLearnHam(wordStatDao, msg.body)
            }
        } else {
            BayesClassifier.untrainSpamAndLearnHam(wordStatDao, message.body)
        }
    }

    suspend fun markMultipleAsSpam(senders: List<String>, sampleMessages: List<String>) {
        messageDao.updateMultipleThreadsSpamStatus(senders, isSpam = true)
        for (sender in senders) {
            blockedNumberDao.blockNumber(
                BlockedNumberEntity(
                    phoneNumber = sender,
                    reason = "Reported Spam"
                )
            )
            val threadMessages = messageDao.getMessagesBySender(sender)
            for (msg in threadMessages) {
                BayesClassifier.train(wordStatDao, msg.body, isSpam = true)
            }
        }
        for (msg in sampleMessages) {
            BayesClassifier.train(wordStatDao, msg, isSpam = true)
        }
    }

    suspend fun togglePin(sender: String, isPinned: Boolean) {
        messageDao.setThreadPinned(sender, isPinned)
    }

    suspend fun toggleStar(id: Long, isStarred: Boolean) {
        messageDao.updateStarredStatus(id, isStarred)
    }

    suspend fun toggleReadStatus(id: Long, isRead: Boolean) {
        messageDao.updateReadStatus(id, isRead)
    }

    suspend fun markAsUnread(id: Long) {
        messageDao.updateReadStatus(id, false)
    }

    suspend fun markThreadAsRead(sender: String) {
        messageDao.markThreadAsRead(sender)
    }

    suspend fun deleteMessage(message: MessageEntity) {
        messageDao.deleteMessage(message)
        context?.let { ctx ->
            try {
                ctx.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} = ?",
                    arrayOf(message.sender, message.timestamp.toString())
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun deleteThread(sender: String) {
        messageDao.deleteThread(sender)
        context?.let { ctx ->
            try {
                ctx.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.ADDRESS} = ?",
                    arrayOf(sender)
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun deleteMultipleThreads(senders: List<String>) {
        messageDao.deleteMultipleThreads(senders)
        context?.let { ctx ->
            for (sender in senders) {
                try {
                    ctx.contentResolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${Telephony.Sms.ADDRESS} = ?",
                        arrayOf(sender)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun deleteAndMarkSpam(message: MessageEntity) {
        markAsSpam(message)
        messageDao.deleteThread(message.sender)
    }

    suspend fun blockNumber(phoneNumber: String, reason: String = "Manual Block") {
        blockedNumberDao.blockNumber(BlockedNumberEntity(phoneNumber, reason))
        messageDao.updateThreadSpamStatus(phoneNumber, true)
    }

    suspend fun unblockNumber(phoneNumber: String) {
        blockedNumberDao.unblockByNumber(phoneNumber)
        messageDao.updateThreadSpamStatus(phoneNumber, false)
    }

    suspend fun syncExistingSms(context: Context, forceFullSync: Boolean = false) {
        SmsSyncManager.syncExistingSmsIfNeeded(context, database, forceFullSync)
    }

    suspend fun getAllMessages(): List<MessageEntity> {
        return messageDao.getAllMessagesList()
    }

    suspend fun exportBackupJson(): String {
        val root = JSONObject()
        val allMsgs = messageDao.getAllMessagesList()
        val msgsArray = JSONArray()
        for (m in allMsgs) {
            val obj = JSONObject().apply {
                put("sender", m.sender)
                put("senderName", m.senderName ?: "")
                put("body", m.body)
                put("timestamp", m.timestamp)
                put("isSpam", m.isSpam)
                put("isRead", m.isRead)
                put("isOutgoing", m.isOutgoing)
                put("isPinned", m.isPinned)
                put("isStarred", m.isStarred)
            }
            msgsArray.put(obj)
        }
        root.put("messages", msgsArray)
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        return root.toString(2)
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            val msgsArray = root.getJSONArray("messages")
            val toInsert = mutableListOf<MessageEntity>()
            for (i in 0 until msgsArray.length()) {
                val obj = msgsArray.getJSONObject(i)
                val sender = obj.getString("sender")
                val timestamp = obj.getLong("timestamp")
                if (messageDao.messageExists(sender, timestamp) == 0) {
                    toInsert.add(
                        MessageEntity(
                            sender = sender,
                            senderName = obj.optString("senderName").takeIf { it.isNotBlank() },
                            body = obj.getString("body"),
                            timestamp = timestamp,
                            isSpam = obj.optBoolean("isSpam", false),
                            isRead = obj.optBoolean("isRead", true),
                            isOutgoing = obj.optBoolean("isOutgoing", false),
                            isPinned = obj.optBoolean("isPinned", false),
                            isStarred = obj.optBoolean("isStarred", false)
                        )
                    )
                }
            }
            if (toInsert.isNotEmpty()) {
                messageDao.insertAll(toInsert)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureSeeded() {
        BayesClassifier.seedInitialDataIfEmpty(wordStatDao)
    }
}
