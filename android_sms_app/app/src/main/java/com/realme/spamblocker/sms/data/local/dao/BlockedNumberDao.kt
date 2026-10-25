package com.realme.spamblocker.sms.data.local.dao

import androidx.room.*
import com.realme.spamblocker.sms.data.local.entity.BlockedNumberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {

    @Query("SELECT * FROM blocked_numbers ORDER BY timestamp DESC")
    fun getAllBlockedNumbers(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers")
    suspend fun getAllBlockedNumbersList(): List<BlockedNumberEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE phoneNumber = :phoneNumber)")
    suspend fun isBlocked(phoneNumber: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockNumber(blockedNumber: BlockedNumberEntity)

    @Delete
    suspend fun unblockNumber(blockedNumber: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE phoneNumber = :phoneNumber")
    suspend fun unblockByNumber(phoneNumber: String)
}
