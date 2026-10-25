package com.realme.spamblocker.sms.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.realme.spamblocker.sms.data.local.prefs.AppSettingsManager
import com.realme.spamblocker.sms.data.local.prefs.AppThemeMode


@Composable
fun RealmeTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = AppSettingsManager.getInstance(context)
    val themeMode by settingsManager.themeMode.collectAsState()
    val accentColorValue by settingsManager.accentColor.collectAsState()

    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }
    val isAmoled = themeMode == AppThemeMode.AMOLED

    val accent = Color(accentColorValue)
    val onPrimaryColor = if (accentColorValue == 0xFFFFC915) Color.Black else Color.White

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            secondary = RealmeElectricBlue,
            background = if (isAmoled) Color(0xFF000000) else RealmeDarkBg,
            surface = if (isAmoled) Color(0xFF0D0D0D) else RealmeDarkSurface,
            surfaceVariant = if (isAmoled) Color(0xFF181818) else RealmeDarkSurfaceElevated,
            onPrimary = onPrimaryColor,
            onBackground = RealmeTextPrimaryLight,
            onSurface = RealmeTextPrimaryLight,
            onSurfaceVariant = RealmeTextSecondaryLight,
            outline = if (isAmoled) Color(0xFF222222) else RealmeDarkBorder,
            outlineVariant = if (isAmoled) Color(0xFF141414) else RealmeDarkSurfaceElevated
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = RealmeElectricBlue,
            background = RealmeLightBg,
            surface = RealmeLightSurface,
            surfaceVariant = RealmeLightBorder,
            onPrimary = onPrimaryColor,
            onBackground = RealmeTextPrimaryDark,
            onSurface = RealmeTextPrimaryDark,
            onSurfaceVariant = RealmeTextSecondaryDark,
            outline = RealmeLightBorder,
            outlineVariant = Color(0xFFD1D5DB)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Sync status bar with active theme
            window.statusBarColor = colorScheme.background.toArgb()
            // Sync navigation bar with active theme
            window.navigationBarColor = colorScheme.surface.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
