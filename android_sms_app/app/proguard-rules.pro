# Proguard rules for MorpsSMS

# Keep Room Database entities and DAOs
-keep class androidx.room.** { *; }
-keep class com.morps.sms.data.local.** { *; }

# Keep Compose Runtime
-keep class androidx.compose.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
