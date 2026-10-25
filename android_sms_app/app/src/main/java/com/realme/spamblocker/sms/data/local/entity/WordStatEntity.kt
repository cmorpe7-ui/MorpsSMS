package com.realme.spamblocker.sms.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores frequency counts for each word encountered.
 * Updated incrementally whenever a user marks messages as spam or not spam.
 */
@Entity(tableName = "word_stats")
data class WordStatEntity(
    @PrimaryKey
    val word: String,
    val hamCount: Int = 0,
    val spamCount: Int = 0
)
