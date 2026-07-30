package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getDynamicColorScheme(
    themeName: String,
    accentName: String,
    systemInDark: Boolean
): ColorScheme {
    // 1. Resolve exact accent color
    val accentColor = when (accentName) {
        "Mint" -> Color(0xFF66BB6A)
        "Sage" -> Color(0xFF708B75)
        "Ocean" -> Color(0xFF00897B)
        "Indigo" -> Color(0xFF5C6BC0)
        "Lavender" -> Color(0xFFAB47BC)
        "Rose" -> Color(0xFFEC407A)
        "Amber" -> Color(0xFFFFA726)
        "Terracotta" -> Color(0xFFD84315)
        "Slate" -> Color(0xFF78909C)
        "Gold" -> Color(0xFFFBC02D)
        else -> Color(0xFF708B75) // default Sage
    }

    val secondaryColor = when (themeName) {
        "Natural Tones" -> Color(0xFF435345) // Forest
        "Cosmic Galaxy" -> Color(0xFF8E99F3) // Soft indigo secondary
        "Midnight Eclipse" -> Color(0xFF90A4AE) // Metal
        "Lavender Mist" -> Color(0xFFAB47BC) // Bright orchid
        else -> Color(0xFF435345)
    }

    // 2. Build Light or Dark styles based on fluid theme backgrounds
    return when (themeName) {
        "Cosmic Galaxy" -> {
            darkColorScheme(
                primary = accentColor,
                secondary = secondaryColor,
                tertiary = Color(0xFF5C6BC0),
                background = Color(0xFF0A0E1C), // Midnight deep space space
                surface = Color(0xFF141A2E), // Galaxy paper
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color(0xFFE4E7F4),
                onSurface = Color(0xFFE4E7F4),
                surfaceVariant = Color(0xFF222B45)
            )
        }
        "Midnight Eclipse" -> {
            darkColorScheme(
                primary = accentColor,
                secondary = secondaryColor,
                tertiary = Color(0xFF90A4AE),
                background = Color(0xFF07090C), // Pitch dark eclipse bg
                surface = Color(0xFF10141A), // Dark charcoal paper
                onPrimary = Color.Black,
                onSecondary = Color.White,
                onBackground = Color(0xFFECEFF1),
                onSurface = Color(0xFFECEFF1),
                surfaceVariant = Color(0xFF1B222B)
            )
        }
        "Lavender Mist" -> {
            lightColorScheme(
                primary = accentColor,
                secondary = secondaryColor,
                tertiary = Color(0xFF8E24AA),
                background = Color(0xFFFAF6FC), // Dreamy lavender field hue
                surface = Color.White,
                onPrimary = Color.White,
                onSecondary = Color(0xFF3F1B63),
                onBackground = Color(0xFF2C104B),
                onSurface = Color(0xFF2C104B),
                surfaceVariant = Color(0xFFF4EBF7)
            )
        }
        else -> { // "Natural Tones"
            lightColorScheme(
                primary = accentColor,
                secondary = secondaryColor,
                tertiary = Color(0xFF435345),
                background = Color(0xFFFDFCF9), // Warm organic sand/beige
                surface = Color.White,
                onPrimary = Color.White,
                onSecondary = Color(0xFF1B1C17),
                onBackground = Color(0xFF1B1C17),
                onSurface = Color(0xFF1B1C17),
                surfaceVariant = Color(0xFFE1E3D3)
            )
        }
    }
}

@Composable
fun MyApplicationTheme(
    selectedTheme: String = "Natural Tones",
    selectedAccent: String = "Sage",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = getDynamicColorScheme(selectedTheme, selectedAccent, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
