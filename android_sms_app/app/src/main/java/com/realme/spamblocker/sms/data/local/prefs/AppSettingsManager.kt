package com.realme.spamblocker.sms.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED Pure Black")
}

data class AccentPalette(
    val name: String,
    val colorValue: Long
)

val AvailableAccents = listOf(
    AccentPalette("realme Blue", 0xFF4C8DFF),
    AccentPalette("realme Yellow", 0xFFFFC915),
    AccentPalette("Emerald Green", 0xFF00C853),
    AccentPalette("Sunset Orange", 0xFFFF6D00),
    AccentPalette("Cyberpunk Purple", 0xFF9C27B0),
    AccentPalette("Flamingo Pink", 0xFFE91E63)
)

enum class BubbleShape(val title: String, val radiusDp: Int) {
    COMPACT("Classic", 10),
    COLOROS("ColorOS Smooth", 18),
    PILL("Pill Round", 26)
}

enum class ChatTextSize(val title: String, val sizeSp: Int) {
    SMALL("Small", 13),
    NORMAL("Normal", 15),
    LARGE("Large", 17),
    EXTRA_LARGE("Huge", 20)
}

enum class NotificationPrivacy(val title: String) {
    FULL("Show Sender & Message"),
    SENDER_ONLY("Show Sender Only"),
    HIDE("Hide All Content")
}

class AppSettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 1. Theme Mode
    private val _themeMode = MutableStateFlow(
        AppThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, AppThemeMode.AMOLED.name) ?: AppThemeMode.AMOLED.name)
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    // 2. Accent Color (Hex Long)
    private val _accentColor = MutableStateFlow(
        prefs.getLong(KEY_ACCENT_COLOR, 0xFF4C8DFF)
    )
    val accentColor: StateFlow<Long> = _accentColor.asStateFlow()

    // 3. Bubble Shape
    private val _bubbleShape = MutableStateFlow(
        BubbleShape.valueOf(prefs.getString(KEY_BUBBLE_SHAPE, BubbleShape.COLOROS.name) ?: BubbleShape.COLOROS.name)
    )
    val bubbleShape: StateFlow<BubbleShape> = _bubbleShape.asStateFlow()

    // 4. Chat Text Size
    private val _chatTextSize = MutableStateFlow(
        ChatTextSize.valueOf(prefs.getString(KEY_CHAT_TEXT_SIZE, ChatTextSize.NORMAL.name) ?: ChatTextSize.NORMAL.name)
    )
    val chatTextSize: StateFlow<ChatTextSize> = _chatTextSize.asStateFlow()

    // 5. Spam AI Sensitivity Threshold (0.60f, 0.70f, 0.85f)
    private val _spamThreshold = MutableStateFlow(
        prefs.getFloat(KEY_SPAM_THRESHOLD, 0.70f)
    )
    val spamThreshold: StateFlow<Float> = _spamThreshold.asStateFlow()

    // 6. Whitelist Saved Contacts
    private val _whitelistContacts = MutableStateFlow(
        prefs.getBoolean(KEY_WHITELIST_CONTACTS, true)
    )
    val whitelistContacts: StateFlow<Boolean> = _whitelistContacts.asStateFlow()

    // 7. Philippine Scam Keyword Shield
    private val _philippineShield = MutableStateFlow(
        prefs.getBoolean(KEY_PH_SHIELD, true)
    )
    val philippineShield: StateFlow<Boolean> = _philippineShield.asStateFlow()

    // 8. Notification Privacy
    private val _notificationPrivacy = MutableStateFlow(
        NotificationPrivacy.valueOf(
            prefs.getString(KEY_NOTIF_PRIVACY, NotificationPrivacy.FULL.name) ?: NotificationPrivacy.FULL.name
        )
    )
    val notificationPrivacy: StateFlow<NotificationPrivacy> = _notificationPrivacy.asStateFlow()

    // 9. Notification Vibration
    private val _notificationVibration = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIF_VIBRATE, true)
    )
    val notificationVibration: StateFlow<Boolean> = _notificationVibration.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAccentColor(color: Long) {
        prefs.edit().putLong(KEY_ACCENT_COLOR, color).apply()
        _accentColor.value = color
    }

    fun setBubbleShape(shape: BubbleShape) {
        prefs.edit().putString(KEY_BUBBLE_SHAPE, shape.name).apply()
        _bubbleShape.value = shape
    }

    fun setChatTextSize(size: ChatTextSize) {
        prefs.edit().putString(KEY_CHAT_TEXT_SIZE, size.name).apply()
        _chatTextSize.value = size
    }

    fun setSpamThreshold(threshold: Float) {
        prefs.edit().putFloat(KEY_SPAM_THRESHOLD, threshold).apply()
        _spamThreshold.value = threshold
    }

    fun setWhitelistContacts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WHITELIST_CONTACTS, enabled).apply()
        _whitelistContacts.value = enabled
    }

    fun setPhilippineShield(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PH_SHIELD, enabled).apply()
        _philippineShield.value = enabled
    }

    // 10. Biometric Lock for Spam & Blocked
    private val _biometricLock = MutableStateFlow(
        prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
    )
    val biometricLock: StateFlow<Boolean> = _biometricLock.asStateFlow()

    fun setNotificationPrivacy(privacy: NotificationPrivacy) {
        prefs.edit().putString(KEY_NOTIF_PRIVACY, privacy.name).apply()
        _notificationPrivacy.value = privacy
    }

    fun setNotificationVibration(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_VIBRATE, enabled).apply()
        _notificationVibration.value = enabled
    }

    fun setBiometricLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
        _biometricLock.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "realme_sms_customization_prefs"

        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_ACCENT_COLOR = "pref_accent_color"
        private const val KEY_BUBBLE_SHAPE = "pref_bubble_shape"
        private const val KEY_CHAT_TEXT_SIZE = "pref_chat_text_size"
        private const val KEY_SPAM_THRESHOLD = "pref_spam_threshold"
        private const val KEY_WHITELIST_CONTACTS = "pref_whitelist_contacts"
        private const val KEY_PH_SHIELD = "pref_ph_shield"
        private const val KEY_NOTIF_PRIVACY = "pref_notif_privacy"
        private const val KEY_NOTIF_VIBRATE = "pref_notif_vibrate"
        private const val KEY_BIOMETRIC_LOCK = "pref_biometric_lock"

        @Volatile
        private var INSTANCE: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
