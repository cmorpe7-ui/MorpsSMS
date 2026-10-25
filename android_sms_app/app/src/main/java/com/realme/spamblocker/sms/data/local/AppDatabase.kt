package com.realme.spamblocker.sms.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.realme.spamblocker.sms.data.local.dao.BlockedNumberDao
import com.realme.spamblocker.sms.data.local.dao.MessageDao
import com.realme.spamblocker.sms.data.local.dao.WordStatDao
import com.realme.spamblocker.sms.data.local.entity.BlockedNumberEntity
import com.realme.spamblocker.sms.data.local.entity.MessageEntity
import com.realme.spamblocker.sms.data.local.entity.WordStatEntity

@Database(
    entities = [MessageEntity::class, WordStatEntity::class, BlockedNumberEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun wordStatDao(): WordStatDao
    abstract fun blockedNumberDao(): BlockedNumberDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "realme_sms_spam_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
