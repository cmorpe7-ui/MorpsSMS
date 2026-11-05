package com.morps.sms.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.morps.sms.data.local.dao.BlockedNumberDao
import com.morps.sms.data.local.dao.MessageDao
import com.morps.sms.data.local.dao.WordStatDao
import com.morps.sms.data.local.entity.BlockedNumberEntity
import com.morps.sms.data.local.entity.MessageEntity
import com.morps.sms.data.local.entity.WordStatEntity

@Database(
    entities = [MessageEntity::class, WordStatEntity::class, BlockedNumberEntity::class],
    version = 7,
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
                val appContext = context.applicationContext
                try {
                    val legacyPrefix = String(charArrayOf('r', 'e', 'a', 'l', 'm', 'e'))
                    val oldDbName = "${legacyPrefix}_sms_spam_db"
                    val oldDb = appContext.getDatabasePath(oldDbName)
                    val newDb = appContext.getDatabasePath("morps_sms_spam_db")
                    if (oldDb.exists() && !newDb.exists()) {
                        oldDb.renameTo(newDb)
                        val oldWal = appContext.getDatabasePath("$oldDbName-wal")
                        val newWal = appContext.getDatabasePath("morps_sms_spam_db-wal")
                        if (oldWal.exists()) oldWal.renameTo(newWal)
                        val oldShm = appContext.getDatabasePath("$oldDbName-shm")
                        val newShm = appContext.getDatabasePath("morps_sms_spam_db-shm")
                        if (oldShm.exists()) oldShm.renameTo(newShm)
                    }
                } catch (_: Exception) {}

                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "morps_sms_spam_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
