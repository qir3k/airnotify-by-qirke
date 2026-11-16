package com.qirke.airnotify.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeAccent(
    val id: String,
    val displayNameEn: String,
    val displayNamePl: String,
    val previewColor: Color
) {
    SYSTEM("system", "Dynamic System", "Z urządzenia (Monet)", Color(0xFF4285F4)),
    CYAN("cyan", "Fitbit Cyan", "Cyjan (Fitbit Air)", Color(0xFF00E5FF)),
    EMERALD("emerald", "Emerald Green", "Szmaragdowy", Color(0xFF10B981)),
    VIOLET("violet", "Electric Violet", "Fioletowy", Color(0xFFA855F7)),
    AMBER("amber", "Sunset Amber", "Bursztynowy", Color(0xFFF59E0B)),
    ROSE("rose", "Vibrant Rose", "Różany", Color(0xFFF43F5E));

    companion object {
        fun fromId(id: String): AppThemeAccent =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
    }
}

private fun buildColorScheme(accent: AppThemeAccent, darkTheme: Boolean): ColorScheme {
    val primaryColor = when (accent) {
        AppThemeAccent.SYSTEM -> Color(0xFF38BDF8)
        AppThemeAccent.CYAN -> Color(0xFF00E5FF)
        AppThemeAccent.EMERALD -> Color(0xFF10B981)
        AppThemeAccent.VIOLET -> Color(0xFFA855F7)
        AppThemeAccent.AMBER -> Color(0xFFF59E0B)
        AppThemeAccent.ROSE -> Color(0xFFF43F5E)
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.Black,
            primaryContainer = primaryColor.copy(alpha = 0.22f),
            onPrimaryContainer = Color.White,
            secondary = primaryColor.copy(alpha = 0.85f),
            onSecondary = Color.Black,
            secondaryContainer = primaryColor.copy(alpha = 0.15f),
            onSecondaryContainer = Color.White,
            tertiary = Color(0xFF94A3B8),
            background = Color(0xFF0B111D),
            surface = Color(0xFF111927),
            surfaceVariant = Color(0xFF1E293B),
            onSurface = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF94A3B8)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = Color.Black,
            secondary = primaryColor.copy(alpha = 0.85f),
            onSecondary = Color.White,
            secondaryContainer = primaryColor.copy(alpha = 0.10f),
            onSecondaryContainer = Color.Black,
            tertiary = Color(0xFF64748B),
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF475569)
        )
    }
}

@Composable
fun AirNotifyTheme(
    accentId: String = "system",
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }

    val context = LocalContext.current
    val accent = AppThemeAccent.fromId(accentId)

    val colorScheme = when {
        accent == AppThemeAccent.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> buildColorScheme(accent, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
