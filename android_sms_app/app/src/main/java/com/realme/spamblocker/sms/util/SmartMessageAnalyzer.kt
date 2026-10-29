package com.realme.spamblocker.sms.util

import androidx.collection.LruCache
import java.util.regex.Pattern

data class UrlInspection(
    val url: String,
    val isShortener: Boolean,
    val isSuspiciousTld: Boolean,
    val isHttpInsecure: Boolean,
    val riskLevel: String, // "High", "Medium", "Safe"
    val warningReason: String?
)

data class TransactionInfo(
    val amount: String,
    val currency: String,
    val serviceOrMerchant: String?,
    val timestamp: Long
)

enum class MessageCategory(val title: String) {
    ALL("All"),
    PERSONAL("Personal"),
    TRANSACTIONS("Transactions"),
    PROMOTIONS("Promos"),
    STARRED("Starred")
}

object SmartMessageAnalyzer {

    // Multi-tier regex patterns for diverse OTP/verification message formats
    private val OTP_PATTERNS = listOf(
        // Google G-XXXXXX or explicit labeled codes (e.g. "code: 123456", "OTP: 123456")
        Pattern.compile("""(?i)\b(?:g-|code:?\s*|otp:?\s*|pin:?\s*|token:?\s*|passcode:?\s*)(\d{4,8})\b"""),
        // Keyword followed by code within 40 characters (e.g. "Your verification code for Netflix is 492102")
        Pattern.compile("""(?i)(?:verification code|security code|auth(?:entication)? code|one-time (?:pin|code|password)|passcode|login code|otp|pin|token|code)[^\d]{1,40}?(\b\d{4,8}\b)"""),
        // Code followed by keyword within 40 characters (e.g. "123456 is your authentication code")
        Pattern.compile("""(?i)(\b\d{4,8}\b)[^\d]{1,40}?(?:is your|is the|to verify|verification|code|pin|otp|passcode|secret)"""),
        // Action phrases: "Use 123456 to verify/login/confirm"
        Pattern.compile("""(?i)(?:use|enter|input)[^\d]{1,20}?(\b\d{4,8}\b)[^\d]{1,30}?(?:verify|login|log in|confirm|auth)"""),
        // Filipino / regional language formats
        Pattern.compile("""(?i)(?:kod|kumpirmahin|iyong code)[^\d]{1,30}?(\b\d{4,8}\b)""")
    )

    private val DIGIT_CANDIDATE_REGEX = Regex("""\b\d{4,8}\b""")
    private val YEAR_STRINGS = setOf("2024", "2025", "2026", "2027")

    private val URL_REGEX = Pattern.compile(
        """(https?://[^\s/$.?#].[^\s]*)""",
        Pattern.CASE_INSENSITIVE
    )

    private val MOBILE_PATTERN = Pattern.compile("""^(\+?63|0)?9\d{9}$""")

    private val SHORTENERS = listOf("bit.ly", "tinyurl.com", "t.me", "cutt.ly", "wa.me", "is.gd", "rb.gy", "v.gd")
    private val SUSPICIOUS_TLDS = listOf(".top", ".xyz", ".club", ".click", ".buzz", ".vip", ".work", ".live", ".link")

    private val BANK_KEYWORDS = listOf(
        "gcash", "maya", "bdo", "bpi", "metrobank", "unionbank",
        "security bank", "landbank", "rcbc", "bank account", "e-wallet",
        "account suspended", "verify your account", "deactivated"
    )

    private val TRANSACTION_KEYWORDS = listOf(
        "received", "sent", "paid", "payment", "transferred", "withdrawn",
        "deposited", "debited", "credited", "cash in", "balance", "receipt",
        "ref no", "reference no", "php", "₱"
    )

    private val PROMO_KEYWORDS = listOf(
        "promo", "discount", "sale", "voucher", "off", "free", "win", "exclusive",
        "unli", "load", "claim", "deal", "cashback", "reward"
    )

    // Ultra-fast in-memory LRU caches
    private val otpCache = LruCache<String, String>(200)
    private val urlCache = LruCache<String, List<UrlInspection>>(200)
    private val bankImpersonationCache = LruCache<String, Boolean>(200)
    private val categoryCache = LruCache<String, MessageCategory>(300)

    private val TRANSACTION_REGEX = Pattern.compile(
        """(?:(?:PHP|Php|php|₱)\s*([\d,]+\.?\d*)|([\d,]+\.?\d*)\s*(?:PHP|Php|php|₱))"""
    )
    private val CLEAN_SENDER_REGEX = Pattern.compile("[\\s-]+")

    /**
     * Extracts OTP / verification code with zero-latency fast-path.
     */
    fun extractOtp(body: String): String? {
        if (body.length < 4 || body.none { it.isDigit() }) return null

        val lower = body.lowercase()
        // Fast-path: Skip regex entirely if no verification keywords exist
        val hasOtpKeyword = lower.contains("code") || lower.contains("otp") ||
                lower.contains("pin") || lower.contains("verif") ||
                lower.contains("passcode") || lower.contains("auth") ||
                lower.contains("one-time") || lower.contains("onetime") ||
                lower.contains("security") || lower.contains("token") ||
                lower.contains("código") || lower.contains("kod") ||
                lower.contains("kumpirma") || lower.contains("g-")
        if (!hasOtpKeyword) {
            return null
        }

        otpCache.get(body)?.let { return it }

        // 1. Try structured high-confidence regex patterns
        for (pattern in OTP_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val code = matcher.group(1)
                if (code != null && code.length in 4..8) {
                    otpCache.put(body, code)
                    return code
                }
            }
        }

        // 2. Fallback heuristic: If message clearly has verification intent and exactly one 4-8 digit number
        val candidates = DIGIT_CANDIDATE_REGEX.findAll(body).map { it.value }.toList()
        val nonYears = candidates.filter { it !in YEAR_STRINGS }
        val bestCandidate = when {
            nonYears.size == 1 -> nonYears.first()
            candidates.size == 1 -> candidates.first()
            else -> null
        }

        if (bestCandidate != null) {
            otpCache.put(body, bestCandidate)
            return bestCandidate
        }

        return null
    }

    /**
     * Inspects URLs in message body with zero-latency fast-path.
     */
    fun inspectUrls(body: String): List<UrlInspection> {
        // Fast-path: Skip regex if no link markers exist
        if (!body.contains("http://") && !body.contains("https://") &&
            !body.contains("t.me/") && !body.contains("bit.ly/") &&
            !body.contains("tinyurl") && !body.contains("wa.me/")
        ) {
            return emptyList()
        }

        urlCache.get(body)?.let { return it }

        val results = mutableListOf<UrlInspection>()
        val matcher = URL_REGEX.matcher(body)
        while (matcher.find()) {
            val url = matcher.group(1) ?: continue
            val lower = url.lowercase()

            val isShortener = SHORTENERS.any { lower.contains(it) }
            val isSuspiciousTld = SUSPICIOUS_TLDS.any { lower.contains(it) }
            val isHttp = lower.startsWith("http://")

            val riskLevel: String
            val reason: String?

            if (isShortener || isSuspiciousTld) {
                riskLevel = "High"
                reason = if (isShortener) "Masked short link hides true destination" else "Suspicious domain extension frequently used in scams"
            } else if (isHttp) {
                riskLevel = "Medium"
                reason = "Insecure unencrypted HTTP connection"
            } else {
                riskLevel = "Safe"
                reason = null
            }

            results.add(
                UrlInspection(
                    url = url,
                    isShortener = isShortener,
                    isSuspiciousTld = isSuspiciousTld,
                    isHttpInsecure = isHttp,
                    riskLevel = riskLevel,
                    warningReason = reason
                )
            )
        }

        urlCache.put(body, results)
        return results
    }

    /**
     * Checks if a standard 11-digit mobile number is impersonating a bank or official brand.
     */
    fun isBankImpersonation(sender: String, body: String): Boolean {
        val cacheKey = "$sender:$body"
        bankImpersonationCache.get(cacheKey)?.let { return it }

        // Fast-path: Check keywords first before regex
        val lowerBody = body.lowercase()
        val hasBankKeyword = BANK_KEYWORDS.any { lowerBody.contains(it) }
        if (!hasBankKeyword) {
            bankImpersonationCache.put(cacheKey, false)
            return false
        }

        val cleanSender = sender.replace("\\s|-".toRegex(), "")
        val isMobile = MOBILE_PATTERN.matcher(cleanSender).matches()

        val isImpersonator = isMobile && hasBankKeyword
        bankImpersonationCache.put(cacheKey, isImpersonator)
        return isImpersonator
    }

    /**
     * Extracts financial transaction details if present.
     */
    fun extractTransaction(body: String, timestamp: Long): TransactionInfo? {
        if (!body.contains("PHP", ignoreCase = true) && !body.contains("₱")) return null

        val matcher = TRANSACTION_REGEX.matcher(body)
        if (matcher.find()) {
            val amountStr = matcher.group(1) ?: matcher.group(2) ?: return null
            val lower = body.lowercase()

            val service = when {
                lower.contains("gcash") -> "GCash"
                lower.contains("maya") -> "Maya"
                lower.contains("bpi") -> "BPI"
                lower.contains("bdo") -> "BDO"
                lower.contains("metrobank") -> "Metrobank"
                lower.contains("unionbank") -> "UnionBank"
                lower.contains("shopee") -> "ShopeePay"
                lower.contains("lazada") -> "Lazada"
                else -> "Transaction"
            }

            return TransactionInfo(
                amount = amountStr,
                currency = "PHP",
                serviceOrMerchant = service,
                timestamp = timestamp
            )
        }
        return null
    }

    /**
     * Automatically categorizes an incoming message with instant LRU caching.
     */
    fun determineCategory(
        sender: String,
        senderName: String?,
        body: String,
        isSpam: Boolean,
        isStarred: Boolean = false
    ): MessageCategory {
        if (isStarred) return MessageCategory.STARRED
        if (isSpam) return MessageCategory.ALL

        val cacheKey = "$sender|${senderName.orEmpty()}|${body.hashCode()}"
        categoryCache.get(cacheKey)?.let { return it }

        val cleanSender = CLEAN_SENDER_REGEX.matcher(sender).replaceAll("")
        val isMobileOrContact = !senderName.isNullOrBlank() || MOBILE_PATTERN.matcher(cleanSender).matches()
        val lowerBody = body.lowercase()

        // 1. Transactions & OTPs
        val hasOtp = extractOtp(body) != null
        val hasTransaction = extractTransaction(body, 0L) != null
        val hasBankKeyword = BANK_KEYWORDS.any { lowerBody.contains(it) }

        val result = if (hasOtp || hasTransaction || (!isMobileOrContact && (hasBankKeyword || TRANSACTION_KEYWORDS.any { lowerBody.contains(it) }))) {
            MessageCategory.TRANSACTIONS
        } else if (!isMobileOrContact && PROMO_KEYWORDS.any { lowerBody.contains(it) }) {
            MessageCategory.PROMOTIONS
        } else if (isMobileOrContact) {
            MessageCategory.PERSONAL
        } else if (PROMO_KEYWORDS.any { lowerBody.contains(it) }) {
            MessageCategory.PROMOTIONS
        } else if (TRANSACTION_KEYWORDS.any { lowerBody.contains(it) }) {
            MessageCategory.TRANSACTIONS
        } else {
            MessageCategory.ALL
        }

        categoryCache.put(cacheKey, result)
        return result
    }
}
