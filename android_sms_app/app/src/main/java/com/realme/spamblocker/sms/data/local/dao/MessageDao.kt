package com.realme.spamblocker.sms.data.local.dao

import androidx.room.*
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // Distinct latest message per conversation thread in Inbox, with pinned chats at the top!
    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT sender, MAX(timestamp) AS max_timestamp
            FROM messages
            WHERE isSpam = 0
            GROUP BY sender
        ) latest ON m.sender = latest.sender AND m.timestamp = latest.max_timestamp
        WHERE m.isSpam = 0
        GROUP BY m.sender
        ORDER BY m.isPinned DESC, m.timestamp DESC
    """)
    fun getInboxConversations(): Flow<List<MessageEntity>>

    // Distinct latest message per conversation thread in Spam
    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT sender, MAX(timestamp) AS max_timestamp
            FROM messages
            WHERE isSpam = 1
            GROUP BY sender
        ) latest ON m.sender = latest.sender AND m.timestamp = latest.max_timestamp
        WHERE m.isSpam = 1
        GROUP BY m.sender
        ORDER BY m.timestamp DESC
    """)
    fun getSpamConversations(): Flow<List<MessageEntity>>

    // Full 1-on-1 thread messages with a specific contact/number
    @Query("SELECT * FROM messages WHERE sender = :sender ORDER BY timestamp ASC")
    fun getConversationMessages(sender: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sender = :sender ORDER BY timestamp ASC")
    suspend fun getMessagesBySender(sender: String): List<MessageEntity>

    // Unread count
    @Query("SELECT COUNT(*) FROM messages WHERE isSpam = 0 AND isRead = 0 AND isOutgoing = 0")
    fun getUnreadInboxCount(): Flow<Int>

    // Total spam count
    @Query("SELECT COUNT(DISTINCT sender) FROM messages WHERE isSpam = 1")
    fun getSpamCount(): Flow<Int>

    // Search query across sender name, number, or message body
    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT sender, MAX(timestamp) AS max_timestamp
            FROM messages
            WHERE isSpam = 0
            GROUP BY sender
        ) latest ON m.sender = latest.sender AND m.timestamp = latest.max_timestamp
        WHERE m.isSpam = 0
        AND (m.sender LIKE '%' || :query || '%' OR m.senderName LIKE '%' || :query || '%' OR m.body LIKE '%' || :query || '%')
        GROUP BY m.sender
        ORDER BY m.isPinned DESC, m.timestamp DESC
    """)
    fun searchInbox(query: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET isSpam = :isSpam WHERE sender = :sender")
    suspend fun updateThreadSpamStatus(sender: String, isSpam: Boolean)

    @Query("UPDATE messages SET isSpam = :isSpam WHERE sender IN (:senders)")
    suspend fun updateMultipleThreadsSpamStatus(senders: List<String>, isSpam: Boolean)

    @Query("UPDATE messages SET isPinned = :isPinned WHERE sender = :sender")
    suspend fun setThreadPinned(sender: String, isPinned: Boolean)

    @Query("UPDATE messages SET isSpam = :isSpam WHERE id = :id")
    suspend fun updateSpamStatus(id: Long, isSpam: Boolean)

    @Query("UPDATE messages SET isSpam = :isSpam, spamProbability = :probability WHERE id = :id")
    suspend fun updateMessageSpamScore(id: Long, isSpam: Boolean, probability: Float)

    @Query("UPDATE messages SET isRead = :isRead WHERE id = :id")
    suspend fun updateReadStatus(id: Long, isRead: Boolean)

    @Query("UPDATE messages SET isRead = 1 WHERE sender = :sender")
    suspend fun markThreadAsRead(sender: String)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE sender = :sender")
    suspend fun deleteThread(sender: String)

    @Query("DELETE FROM messages WHERE sender IN (:senders)")
    suspend fun deleteMultipleThreads(senders: List<String>)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int

    @Query("SELECT MAX(timestamp) FROM messages")
    suspend fun getLatestTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM messages WHERE sender = :sender AND timestamp = :timestamp")
    suspend fun messageExists(sender: String, timestamp: Long): Int

    @Query("SELECT timestamp FROM messages WHERE timestamp >= :sinceTimestamp")
    suspend fun getTimestampsSince(sinceTimestamp: Long): List<Long>

    @Query("SELECT timestamp FROM messages")
    suspend fun getAllTimestamps(): List<Long>

    @Query("SELECT sender || ':' || timestamp FROM messages")
    suspend fun getAllMessageKeys(): List<String>

    @Query("SELECT sender || ':' || timestamp FROM messages WHERE timestamp >= :sinceTimestamp")
    suspend fun getMessageKeysSince(sinceTimestamp: Long): List<String>

    @Query("SELECT * FROM messages WHERE isStarred = 1 ORDER BY timestamp DESC")
    fun getStarredMessages(): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarredStatus(id: Long, isStarred: Boolean)

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesList(): List<MessageEntity>
}
