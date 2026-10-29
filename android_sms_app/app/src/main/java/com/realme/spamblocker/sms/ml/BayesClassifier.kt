package com.realme.spamblocker.sms.ml

import com.realme.spamblocker.sms.data.local.dao.WordStatDao
import com.realme.spamblocker.sms.data.local.entity.WordStatEntity
import java.util.regex.Pattern
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.max

object BayesClassifier {

    private val MOBILE_PATTERN = Pattern.compile("""^(\+?63|0)?9\d{9}${'$'}""")

    // Philippine scam trigger keywords
    private val BANK_KEYWORDS = listOf(
        "gcash", "maya", "bdo", "bpi", "metrobank", "unionbank",
        "landbank", "rcbc", "security bank", "palawan", "cimb",
        "e-wallet", "online banking", "bank account"
    )

    private val GAMBLING_KEYWORDS = listOf(
        "jili", "casino", "slot", "slots", "sabong", "color game", "free bet",
        "deposit bonus", "scatter", "free 100", "free 777", "jackpot", "vip club"
    )

    private val LOAN_KEYWORDS = listOf(
        "pautang", "pesoloan", "mabilis cash", "fast loan", "quick loan",
        "instant loan", "loan approved", "guaranteed approval", "no requirements"
    )

    private val JOB_KEYWORDS = listOf(
        "part time", "parttime", "part-time", "daily salary", "daily income",
        "wfh", "work from home", "commission task", "shopee agent", "lazada agent",
        "telegram recruiter", "whatsapp recruiter", "like and follow"
    )

    private val URGENCY_KEYWORDS = listOf(
        "suspended", "suspension", "deactivated", "blocked", "locking",
        "sim blocked", "sim card blocked", "unauthorized", "security alert",
        "final warning", "immediate action", "within 24 hours", "arrest warrant"
    )

    // Suspicious URL patterns common in PH SMS spam
    private val URL_SHORTENER_REGEX = Regex(
        """(?i)(bit\.ly|tinyurl|t\.me|cutt\.ly|shorturl|is\.gd|rb\.gy|wa\.me|v\.gd|shor\.by|tiny\.cc)"""
    )
    private val RAW_IP_URL_REGEX = Regex(
        """(?i)https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""
    )
    private val SUSPICIOUS_TLD_REGEX = Regex(
        """(?i)\.(top|xyz|club|click|buzz|vip|work|live|link|rest|icu|cam|cyou|shop)(/|\s|${'$'})"""
    )

    private val PH_CURRENCY_REGEX = Regex("(₱|php|pesos?)", RegexOption.IGNORE_CASE)
    private val MONEY_AMOUNT_REGEX = Regex("(₱|php|p)\\s*\\d+([,\\.]\\d+)?", RegexOption.IGNORE_CASE)

    // Official commercial registered Sender IDs (Not 11-digit mobile numbers)
    private val KNOWN_LEGIT_SENDERS = listOf(
        "gcash", "maya", "bdo", "bpi", "metrobank", "unionbank", "globe",
        "smart", "dito", "google", "apple", "shopee", "lazada", "foodpanda",
        "grab", "govph", "landbank", "rcbc", "securitybank", "cimb"
    )

    private val STOP_WORDS = setOf(
        "the", "is", "at", "which", "on", "a", "an", "and", "or", "to", "in",
        "for", "with", "ang", "mga", "ng", "sa", "at", "na", "pa", "ko", "mo", "ka", "ni"
    )

    /**
     * Normalizes and de-obfuscates text used by scammers trying to bypass filters
     * (e.g. "G C A S H" -> "gcash", "c@sin0" -> "casino", "j1l1" -> "jili").
     */
    fun deobfuscate(text: String): String {
        var processed = text

        // 1. Collapse spaced single letters (e.g. "G C A S H" -> "GCASH")
        processed = processed.replace(Regex("""(?<=\b[a-zA-Z])\s+(?=[a-zA-Z]\b)"""), "")

        // 2. Collapse punctuation-separated letters (e.g. "G.C.A.S.H" -> "GCASH")
        processed = processed.replace(Regex("""(?<=\b[a-zA-Z])[._\-](?=[a-zA-Z]\b)"""), "")

        // 3. De-obfuscate common leetspeak sandwiched inside letters
        processed = processed
            .replace(Regex("""(?<=[a-zA-Z])0(?=[a-zA-Z])"""), "o")
            .replace(Regex("""(?<=[a-zA-Z])1(?=[a-zA-Z])"""), "i")
            .replace(Regex("""(?<=[a-zA-Z])3(?=[a-zA-Z])"""), "e")
            .replace(Regex("""(?<=[a-zA-Z])4(?=[a-zA-Z])"""), "a")
            .replace("@", "a")
            .replace("${'$'}", "s")

        return processed
    }

    /**
     * Splits text into normalized unigram and bigram tokens for contextual detection.
     */
    fun tokenize(text: String): List<String> {
        val cleanText = deobfuscate(text.lowercase())
        val words = cleanText
            .replace(Regex("[^a-z0-9₱\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length in 2..25 && it !in STOP_WORDS }

        if (words.isEmpty()) return emptyList()

        val tokens = mutableListOf<String>()
        tokens.addAll(words)

        // Generate bigrams for phrase-level detection (e.g. "claim_prize", "account_suspended")
        for (i in 0 until words.size - 1) {
            tokens.add("${words[i]}_${words[i + 1]}")
        }

        return tokens
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
     * Active learning weight multiplier when user manually flags messages
     */
    const val USER_FEEDBACK_WEIGHT = 5

    /**
     * Predicts spam probability with Bayesian inference and multi-layer threat heuristics.
     * Returns a float between 0.0 (Legitimate Ham) and 1.0 (Definitely Spam).
     */
    suspend fun predict(
        wordStatDao: WordStatDao,
        message: String,
        enableHeuristics: Boolean = true,
        sender: String = ""
    ): Float {
        val tokens = tokenize(message)
        if (tokens.isEmpty()) return 0.0f

        val totalHamWords = cachedTotalHam ?: (wordStatDao.getTotalHamWords() ?: 100).toDouble().also { cachedTotalHam = it }
        val totalSpamWords = cachedTotalSpam ?: (wordStatDao.getTotalSpamWords() ?: 100).toDouble().also { cachedTotalSpam = it }
        val vocabSize = cachedVocabSize ?: (wordStatDao.getVocabSize().coerceAtLeast(50)).toDouble().also { cachedVocabSize = it }

        // Fetch known statistics for the tokens in this message
        val statsMap = wordStatDao.getStatsForWords(tokens).associateBy { it.word }

        // Only evaluate informative tokens (those with actual spam/ham observations)
        val informativeTokens = tokens.filter { token ->
            val stat = statsMap[token]
            stat != null && (stat.spamCount > 0 || stat.hamCount > 0)
        }

        var rawProbability = 0.0f
        if (informativeTokens.isNotEmpty()) {
            var logSpamProb = 0.0
            var logHamProb = 0.0

            for (token in informativeTokens) {
                val stat = statsMap[token]
                val spamCount = stat?.spamCount ?: 0
                val hamCount = stat?.hamCount ?: 0

                val pTokenGivenSpam = (spamCount + 1.0) / (totalSpamWords + vocabSize)
                val pTokenGivenHam = (hamCount + 1.0) / (totalHamWords + vocabSize)

                logSpamProb += ln(pTokenGivenSpam)
                logHamProb += ln(pTokenGivenHam)
            }

            val logDiff = (logHamProb - logSpamProb).coerceIn(-50.0, 50.0)
            rawProbability = (1.0 / (1.0 + exp(logDiff))).toFloat()
        }

        // Apply multi-layer neural heuristic threat adjustments
        if (enableHeuristics) {
            rawProbability = applyAdvancedHeuristics(message, tokens, rawProbability, sender)
        }

        return min(max(rawProbability, 0.0f), 1.0f)
    }

    /**
     * Evaluates specialized threat patterns and returns a human-readable threat reason.
     */
    fun getThreatReason(message: String, sender: String): String? {
        val lower = deobfuscate(message.lowercase())
        val cleanSender = sender.replace("\\s|-".toRegex(), "")
        val isMobileSender = cleanSender.isNotBlank() && MOBILE_PATTERN.matcher(cleanSender).matches()

        // 1. Bank Impersonation
        if (isMobileSender && BANK_KEYWORDS.any { lower.contains(it) }) {
            return "Bank Impersonation"
        }

        // 2. Suspicious Links & IP
        if (RAW_IP_URL_REGEX.containsMatchIn(message)) {
            return "Raw IP Link"
        }
        if (URL_SHORTENER_REGEX.containsMatchIn(message)) {
            return "Masked Link"
        }
        if (SUSPICIOUS_TLD_REGEX.containsMatchIn(message)) {
            return "Suspicious Domain"
        }

        // 3. Online Casino & Illegal Gambling
        if (GAMBLING_KEYWORDS.any { lower.contains(it) }) {
            return "Online Casino"
        }

        // 4. Predatory Loans
        if (LOAN_KEYWORDS.any { lower.contains(it) }) {
            return "Predatory Loan"
        }

        // 5. Fake Job / Task Scam
        if (JOB_KEYWORDS.any { lower.contains(it) }) {
            return "Job Scam"
        }

        // 6. Urgency & Threats
        if (URGENCY_KEYWORDS.any { lower.contains(it) }) {
            return "Urgent Threat"
        }

        return null
    }

    /**
     * Multi-layer threat analysis and heuristic adjustment engine.
     */
    private fun applyAdvancedHeuristics(
        message: String,
        tokens: List<String>,
        baseScore: Float,
        sender: String
    ): Float {
        var score = baseScore
        val lower = deobfuscate(message.lowercase())
        val cleanSender = sender.replace("\\s|-".toRegex(), "")
        val isMobileSender = cleanSender.isNotBlank() && MOBILE_PATTERN.matcher(cleanSender).matches()
        val isOfficialAlphanumeric = cleanSender.isNotBlank() && !isMobileSender && cleanSender.any { it.isLetter() }

        // Layer 1: Bank Impersonation (Mobile number claiming to be bank / e-wallet)
        val hasBankKeyword = BANK_KEYWORDS.any { lower.contains(it) }
        if (isMobileSender && hasBankKeyword) {
            score += 0.50f
        }

        // Layer 2: Suspicious URL Shorteners & IP links
        if (URL_SHORTENER_REGEX.containsMatchIn(message)) {
            score += 0.30f
        }
        if (RAW_IP_URL_REGEX.containsMatchIn(message)) {
            score += 0.45f
        }
        if (SUSPICIOUS_TLD_REGEX.containsMatchIn(message)) {
            score += 0.25f
        }

        // Layer 3: Gambling & Online Casino
        val hasGambling = GAMBLING_KEYWORDS.any { lower.contains(it) }
        if (hasGambling) {
            score += 0.25f
        }

        // Layer 4: Predatory Loans
        val hasLoan = LOAN_KEYWORDS.any { lower.contains(it) }
        if (hasLoan) {
            score += 0.25f
        }

        // Layer 5: Fake Job / Task Scam
        val hasJob = JOB_KEYWORDS.any { lower.contains(it) }
        if (hasJob) {
            score += 0.25f
        }

        // Layer 6: Urgency & Threats
        val hasUrgency = URGENCY_KEYWORDS.any { lower.contains(it) }
        if (hasUrgency) {
            score += 0.20f
        }

        // Currency mentions in high-risk context
        if ((PH_CURRENCY_REGEX.containsMatchIn(message) || MONEY_AMOUNT_REGEX.containsMatchIn(message)) && (hasGambling || hasLoan || hasJob)) {
            score += 0.15f
        }

        // Layer 7: Authentic Brand Safe-Guard (Anti-False-Positive)
        // If sender is a registered commercial brand and doesn't contain suspicious external short links
        if (isOfficialAlphanumeric && !URL_SHORTENER_REGEX.containsMatchIn(message) && !RAW_IP_URL_REGEX.containsMatchIn(message)) {
            val isKnownLegitimateBrand = KNOWN_LEGIT_SENDERS.any { cleanSender.contains(it, ignoreCase = true) }
            if (isKnownLegitimateBrand) {
                score = (score - 0.35f).coerceAtLeast(0.0f)
            }
        }

        return score
    }

    /**
     * Active Learning: Updates vocabulary counts when a user marks a message as Spam or Ham.
     */
    suspend fun train(
        wordStatDao: WordStatDao,
        message: String,
        isSpam: Boolean,
        weight: Int = USER_FEEDBACK_WEIGHT
    ) {
        val tokens = tokenize(message)
        if (tokens.isEmpty()) return

        val existingStats = wordStatDao.getStatsForWords(tokens).associateBy { it.word }
        val updatedList = mutableListOf<WordStatEntity>()

        for (token in tokens) {
            val existing = existingStats[token]
            if (existing != null) {
                updatedList.add(
                    existing.copy(
                        spamCount = if (isSpam) existing.spamCount + weight else existing.spamCount,
                        hamCount = if (!isSpam) existing.hamCount + weight else existing.hamCount
                    )
                )
            } else {
                updatedList.add(
                    WordStatEntity(
                        word = token,
                        spamCount = if (isSpam) weight else 0,
                        hamCount = if (!isSpam) weight else 0
                    )
                )
            }
        }
        wordStatDao.insertOrUpdate(updatedList)
        invalidateCache()
    }

    /**
     * Active Learning Recovery: User marked a false positive message as 'Not Spam'.
     */
    suspend fun untrainSpamAndLearnHam(
        wordStatDao: WordStatDao,
        message: String,
        weight: Int = USER_FEEDBACK_WEIGHT
    ) {
        val tokens = tokenize(message)
        if (tokens.isEmpty()) return

        val existingStats = wordStatDao.getStatsForWords(tokens).associateBy { it.word }
        val updatedList = mutableListOf<WordStatEntity>()

        for (token in tokens) {
            val existing = existingStats[token]
            if (existing != null) {
                updatedList.add(
                    existing.copy(
                        spamCount = (existing.spamCount - weight).coerceAtLeast(0),
                        hamCount = existing.hamCount + weight
                    )
                )
            } else {
                updatedList.add(
                    WordStatEntity(word = token, spamCount = 0, hamCount = weight)
                )
            }
        }
        wordStatDao.insertOrUpdate(updatedList)
        invalidateCache()
    }

    private val initialSpamWords = listOf(
        // Critical Scam Brands & Gambing
        "gcash" to 40, "maya" to 35, "jili" to 35, "casino" to 35, "slot" to 30, "slots" to 30,
        "sabong" to 30, "color_game" to 30, "jackpot" to 25, "free_scatter" to 25, "free_100" to 30,
        "vip_club" to 20, "free_bet" to 25, "deposit_bonus" to 25,

        // Lending & Predatory Finance
        "pautang" to 30, "loan" to 25, "mabilis_cash" to 30, "pesoloan" to 30, "fast_approval" to 25,
        "no_requirements" to 25, "instant_loan" to 25, "cash_advance" to 20,

        // Phishing & Urgency
        "account_suspended" to 40, "verify_account" to 35, "suspended" to 25, "deactivated" to 25,
        "sim_blocked" to 30, "sim_registration" to 30, "unauthorized_access" to 30, "click_link" to 25,
        "click_here" to 25, "claim_prize" to 30, "nanalo" to 30, "winner" to 25, "uncollected" to 25,

        // Fake Jobs & Recruitment
        "part_time" to 30, "parttime" to 30, "wfh" to 25, "daily_salary" to 30, "daily_income" to 25,
        "commission_task" to 30, "shopee_agent" to 30, "lazada_agent" to 30, "telegram" to 25, "whatsapp" to 25
    )

    private val initialHamWords = listOf(
        "kumusta" to 30, "ingat" to 30, "po" to 35, "opo" to 30, "dito" to 25,
        "saan" to 25, "kain" to 25, "bahay" to 25, "uwi" to 25, "sige" to 25,
        "salamat" to 30, "thanks" to 30, "okay" to 30, "later" to 20, "tomorrow" to 20,
        "yes" to 20, "meet" to 20, "call" to 15, "work" to 15, "office" to 15,
        "otw" to 25, "sakay" to 20, "traffic" to 20, "arrived" to 20, "see_you" to 25,
        "ingat_po" to 30, "salamat_po" to 30, "saan_ka" to 25
    )

    /**
     * Seeds initial knowledge into the database or adds missing intelligence.
     */
    suspend fun seedInitialData(wordStatDao: WordStatDao) {
        val allWords = (initialSpamWords.map { it.first } + initialHamWords.map { it.first }).distinct()
        val existingWords = wordStatDao.getStatsForWords(allWords).map { it.word }.toSet()

        val newEntities = mutableListOf<WordStatEntity>()
        for ((word, count) in initialSpamWords) {
            if (!existingWords.contains(word)) {
                newEntities.add(WordStatEntity(word = word, spamCount = count, hamCount = 0))
            }
        }
        for ((word, count) in initialHamWords) {
            if (!existingWords.contains(word)) {
                val existing = newEntities.find { it.word == word }
                if (existing != null) {
                    newEntities.remove(existing)
                    newEntities.add(existing.copy(hamCount = count))
                } else {
                    newEntities.add(WordStatEntity(word = word, spamCount = 0, hamCount = count))
                }
            }
        }
        if (newEntities.isNotEmpty()) {
            wordStatDao.insertOrUpdate(newEntities)
            invalidateCache()
        }
    }

    suspend fun seedInitialDataIfEmpty(wordStatDao: WordStatDao) {
        seedInitialData(wordStatDao)
    }
}
