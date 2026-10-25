package com.realme.spamblocker.sms.data.local.dao

import androidx.room.*
import com.realme.spamblocker.sms.data.local.entity.WordStatEntity

@Dao
interface WordStatDao {

    @Query("SELECT * FROM word_stats WHERE word IN (:words)")
    suspend fun getStatsForWords(words: List<String>): List<WordStatEntity>

    @Query("SELECT SUM(hamCount) FROM word_stats")
    suspend fun getTotalHamWords(): Int?

    @Query("SELECT SUM(spamCount) FROM word_stats")
    suspend fun getTotalSpamWords(): Int?

    @Query("SELECT COUNT(*) FROM word_stats")
    suspend fun getVocabSize(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: List<WordStatEntity>)

    @Query("SELECT * FROM word_stats WHERE word = :word LIMIT 1")
    suspend fun getWordStat(word: String): WordStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWord(stat: WordStatEntity)

    @Query("SELECT COUNT(*) FROM word_stats")
    suspend fun count(): Int
}
