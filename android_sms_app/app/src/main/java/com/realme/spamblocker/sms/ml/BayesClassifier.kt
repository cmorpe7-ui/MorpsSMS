package com.realme.spamblocker.sms.ml

import com.realme.spamblocker.sms.data.local.dao.WordStatDao
import com.realme.spamblocker.sms.data.local.entity.WordStatEntity
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.max

object BayesClassifier {

    // Philippine scam trigger keywords that apply heuristic weighting
    private val PH_SCAM_KEYWORDS = setOf(
        "gcash", "maya", "jili", "casino", "slot", "slots", "sabong", "pautang",
        "loan", "nanalo", "prize", "jackpot", "telegram", "whatsapp", "recruiter",
        "salary", "parttime", "part-time", "commission", "task", "rebate",
        "uncollected", "suspended", "verified", "register", "bonus"
    )

    // Suspicious URL patterns common in PH SMS spam
    private val URL_SHORTENER_REGEX = Regex("(bit\\.ly|tinyurl|t\\.me|cutt\\.ly|shorturl|is\\.gd|rb\\.gy|wa\\.me)", RegexOption.IGNORE_CASE)
    private val PH_CURRENCY_REGEX = Regex("(₱|php|pesos?)", RegexOption.IGNORE_CASE)
    private val MONEY_AMOUNT_REGEX = Regex("(₱|php|p)\\s*\\d+([,\\.]\\d+)?", RegexOption.IGNORE_CASE)

    /**
     * Splits a text message into normalized lowercase word tokens.
     */
    fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9₱\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length in 2..25 }
    }

    private var cachedTotalHam: Double? = null
    private var cachedTotalSpam: Double? = null
    private var cachedVocabSize: Double? = null

    fun invalidateCache() {
        cachedTotalHam = null
        cachedTotalSpam = null
        cachedVocabSize = null
    }

    /**
     * Predicts spam probability for an incoming SMS.
     * Returns a float between 0.0 (Definitely Ham) and 1.0 (Definitely Spam).
     */
    suspend fun predict(
        wordStatDao: WordStatDao,
        message: String,
        enableHeuristics: Boolean = true
    ): Float {
        val words = tokenize(message)
        if (words.isEmpty()) return 0.0f

        val totalHamWords = cachedTotalHam ?: (wordStatDao.getTotalHamWords() ?: 100).toDouble().also { cachedTotalHam = it }
        val totalSpamWords = cachedTotalSpam ?: (wordStatDao.getTotalSpamWords() ?: 100).toDouble().also { cachedTotalSpam = it }
        val vocabSize = cachedVocabSize ?: (wordStatDao.getVocabSize().coerceAtLeast(50)).toDouble().also { cachedVocabSize = it }

        // Fetch known statistics for the tokens in this message
        val statsMap = wordStatDao.getStatsForWords(words).associateBy { it.word }

        var logSpamProb = 0.0
        var logHamProb = 0.0

        for (word in words) {
            val stat = statsMap[word]
            val spamCount = stat?.spamCount ?: 0
            val hamCount = stat?.hamCount ?: 0

            // Laplace smoothing: (count + 1) / (total_words + vocab_size)
            val pWordGivenSpam = (spamCount + 1.0) / (totalSpamWords + vocabSize)
            val pWordGivenHam = (hamCount + 1.0) / (totalHamWords + vocabSize)

            logSpamProb += ln(pWordGivenSpam)
            logHamProb += ln(pWordGivenHam)
        }

        // Convert log-likelihood difference to posterior probability: 1 / (1 + exp(logHam - logSpam))
        val logDiff = (logHamProb - logSpamProb).coerceIn(-50.0, 50.0)
        var rawProbability = (1.0 / (1.0 + exp(logDiff))).toFloat()

        // Apply Philippine-specific heuristic adjustments if enabled
        if (enableHeuristics) {
            rawProbability = applyPhilippineHeuristics(message, words, rawProbability)
        }

        return min(max(rawProbability, 0.0f), 1.0f)
    }

    /**
     * Boosts spam probability if message exhibits hallmark Philippine scam patterns
     */
    private fun applyPhilippineHeuristics(message: String, words: List<String>, baseScore: Float): Float {
        var score = baseScore

        // 1. Check for URL shorteners (bit.ly, t.me, wa.me, etc.)
        if (URL_SHORTENER_REGEX.containsMatchIn(message)) {
            score += 0.25f
        }

        // 2. Check for Money mentions (₱ or PHP)
        if (PH_CURRENCY_REGEX.containsMatchIn(message) || MONEY_AMOUNT_REGEX.containsMatchIn(message)) {
            score += 0.15f
        }

        // 3. Check for Philippine scam indicator words
        val scamMatchCount = words.count { it in PH_SCAM_KEYWORDS }
        if (scamMatchCount > 0) {
            score += (scamMatchCount * 0.12f).coerceAtMost(0.40f)
        }

        return score
    }

    /**
     * Active Learning: Updates vocabulary counts when a user marks a message as Spam or Ham.
     */
    suspend fun train(wordStatDao: WordStatDao, message: String, isSpam: Boolean) {
        val words = tokenize(message)
        if (words.isEmpty()) return

        val existingStats = wordStatDao.getStatsForWords(words).associateBy { it.word }
        val updatedList = mutableListOf<WordStatEntity>()

        for (word in words) {
            val existing = existingStats[word]
            if (existing != null) {
                updatedList.add(
                    existing.copy(
                        spamCount = if (isSpam) existing.spamCount + 1 else existing.spamCount,
                        hamCount = if (!isSpam) existing.hamCount + 1 else existing.hamCount
                    )
                )
            } else {
                updatedList.add(
                    WordStatEntity(
                        word = word,
                        spamCount = if (isSpam) 1 else 0,
                        hamCount = if (!isSpam) 1 else 0
                    )
                )
            }
        }
        wordStatDao.insertOrUpdate(updatedList)
        invalidateCache()
    }

    /**
     * Active Learning Recovery: User marked a false positive message as 'Not Spam'.
     * Decrements the spam counters and increments ham counters.
     */
    suspend fun untrainSpamAndLearnHam(wordStatDao: WordStatDao, message: String) {
        val words = tokenize(message)
        if (words.isEmpty()) return

        val existingStats = wordStatDao.getStatsForWords(words).associateBy { it.word }
        val updatedList = mutableListOf<WordStatEntity>()

        for (word in words) {
            val existing = existingStats[word]
            if (existing != null) {
                updatedList.add(
                    existing.copy(
                        spamCount = (existing.spamCount - 1).coerceAtLeast(0),
                        hamCount = existing.hamCount + 1
                    )
                )
            } else {
                updatedList.add(
                    WordStatEntity(word = word, spamCount = 0, hamCount = 1)
                )
            }
        }
        wordStatDao.insertOrUpdate(updatedList)
        invalidateCache()
    }

    /**
     * Seeds initial knowledge into the database so the app is smart right on install.
     */
    suspend fun seedInitialDataIfEmpty(wordStatDao: WordStatDao) {
        if (wordStatDao.count() > 0) return

        val initialSpamWords = listOf(
            "claim" to 15, "prize" to 20, "won" to 25, "free" to 30, "cash" to 20,
            "urgent" to 18, "winner" to 22, "congratulations" to 15, "click" to 16,
            "link" to 20, "gcash" to 35, "maya" to 25, "jili" to 30, "casino" to 30,
            "slot" to 25, "pautang" to 25, "nanalo" to 25, "bonus" to 20, "salary" to 15,
            "telegram" to 20, "uncollected" to 18, "suspended" to 18, "rebate" to 15
        )

        val initialHamWords = listOf(
            "kumusta" to 20, "ingat" to 25, "po" to 30, "opo" to 25, "dito" to 20,
            "saan" to 15, "kain" to 20, "bahay" to 18, "uwi" to 22, "sige" to 20,
            "thanks" to 25, "okay" to 30, "later" to 15, "tomorrow" to 15, "yes" to 20,
            "meet" to 15, "call" to 10, "work" to 12, "office" to 10
        )

        val seedEntities = mutableListOf<WordStatEntity>()
        for ((word, count) in initialSpamWords) {
            seedEntities.add(WordStatEntity(word = word, spamCount = count, hamCount = 0))
        }
        for ((word, count) in initialHamWords) {
            val existing = seedEntities.find { it.word == word }
            if (existing != null) {
                seedEntities.remove(existing)
                seedEntities.add(existing.copy(hamCount = count))
            } else {
                seedEntities.add(WordStatEntity(word = word, spamCount = 0, hamCount = count))
            }
        }
        wordStatDao.insertOrUpdate(seedEntities)
    }
}
