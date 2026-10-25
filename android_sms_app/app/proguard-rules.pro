# Proguard rules for Realme SMS Spam Blocker

# Keep Room Database entities and DAOs
-keep class androidx.room.** { *; }
-keep class com.realme.spamblocker.sms.data.local.** { *; }

# Keep Compose Runtime
-keep class androidx.compose.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
