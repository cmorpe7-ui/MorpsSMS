package com.realme.spamblocker.sms.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["isSpam", "isPinned", "timestamp"]),
        Index(value = ["sender", "timestamp"]),
        Index(value = ["isStarred", "timestamp"]),
        Index(value = ["isSpam", "sender", "id"]),
        Index(value = ["isSpam", "isRead", "isOutgoing"]),
        Index(value = ["subId"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val senderName: String? = null,
    val body: String,
    val timestamp: Long,
    val isSpam: Boolean = false,
    val spamProbability: Float = 0.0f,
    val isRead: Boolean = false,
    val isOutgoing: Boolean = false, // true = sent by user, false = received
    val isPinned: Boolean = false,   // true = pinned to top of inbox
    val subId: Int = -1,            // Subscription ID for Dual-SIM (SIM 1 vs SIM 2)
    val isStarred: Boolean = false,  // true = bookmarked/starred message
    val contactPhotoUri: String? = null, // Cached contact photo URI
    val scheduledTimestamp: Long = 0L    // Future delivery timestamp (0 = immediate/sent)
)
