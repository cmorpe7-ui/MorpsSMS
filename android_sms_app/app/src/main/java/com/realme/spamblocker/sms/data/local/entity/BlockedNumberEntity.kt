package com.realme.spamblocker.sms.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey
    val phoneNumber: String,
    val reason: String = "Manual block",
    val timestamp: Long = System.currentTimeMillis()
)
