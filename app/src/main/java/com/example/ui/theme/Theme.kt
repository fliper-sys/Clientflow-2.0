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
        "Natural Tones" -> Color(0xFF81C784)
        "Cosmic Galaxy" -> Color(0xFF8E99F3)
        "Midnight Eclipse" -> Color(0xFF90A4AE)
        "Lavender Mist" -> Color(0xFFCE93D8)
        else -> Color(0xFF81C784)
    }

    val isDarkScheme = systemInDark || themeName == "Cosmic Galaxy" || themeName == "Midnight Eclipse"

    return if (isDarkScheme) {
        when (themeName) {
            "Cosmic Galaxy" -> {
                darkColorScheme(
                    primary = accentColor,
                    secondary = secondaryColor,
                    tertiary = Color(0xFF7986CB),
                    background = Color(0xFF0B0E1B), // Midnight deep space
                    surface = Color(0xFF161B2E), // Galaxy surface card
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = Color(0xFFE2E8F0),
                    onSurface = Color(0xFFE2E8F0),
                    surfaceVariant = Color(0xFF232B42),
                    onSurfaceVariant = Color(0xFFCBD5E1),
                    outline = Color(0xFF475569),
                    outlineVariant = Color(0xFF334155)
                )
            }
            "Midnight Eclipse" -> {
                darkColorScheme(
                    primary = accentColor,
                    secondary = secondaryColor,
                    tertiary = Color(0xFFB0BEC5),
                    background = Color(0xFF090B0E), // Pitch dark eclipse
                    surface = Color(0xFF131720), // Dark charcoal card
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = Color(0xFFECEFF1),
                    onSurface = Color(0xFFECEFF1),
                    surfaceVariant = Color(0xFF1E2530),
                    onSurfaceVariant = Color(0xFFCBD5E1),
                    outline = Color(0xFF475569),
                    outlineVariant = Color(0xFF334155)
                )
            }
            "Lavender Mist" -> {
                darkColorScheme(
                    primary = accentColor,
                    secondary = secondaryColor,
                    tertiary = Color(0xFFAB47BC),
                    background = Color(0xFF140D1A), // Low-light dark purple/violet
                    surface = Color(0xFF21152B), // Violet card surface
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = Color(0xFFF3E8F7),
                    onSurface = Color(0xFFF3E8F7),
                    surfaceVariant = Color(0xFF322240),
                    onSurfaceVariant = Color(0xFFE1D0E8),
                    outline = Color(0xFF6A4B7D),
                    outlineVariant = Color(0xFF483157)
                )
            }
            else -> { // "Natural Tones" in Dark Mode
                darkColorScheme(
                    primary = accentColor,
                    secondary = secondaryColor,
                    tertiary = Color(0xFFA5D6A7),
                    background = Color(0xFF121614), // Low-light organic slate background
                    surface = Color(0xFF1A211E), // Soft dark sage surface
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = Color(0xFFE2E8F4),
                    onSurface = Color(0xFFE2E8F4),
                    surfaceVariant = Color(0xFF26322C),
                    onSurfaceVariant = Color(0xFFC2D3C9),
                    outline = Color(0xFF43584E),
                    outlineVariant = Color(0xFF2E3D36)
                )
            }
        }
    } else {
        when (themeName) {
            "Lavender Mist" -> {
                lightColorScheme(
                    primary = accentColor,
                    secondary = Color(0xFF435345),
                    tertiary = Color(0xFF8E24AA),
                    background = Color(0xFFFAF6FC),
                    surface = Color.White,
                    onPrimary = Color.White,
                    onSecondary = Color(0xFF3F1B63),
                    onBackground = Color(0xFF2C104B),
                    onSurface = Color(0xFF2C104B),
                    surfaceVariant = Color(0xFFF4EBF7),
                    onSurfaceVariant = Color(0xFF5E4378),
                    outline = Color(0xFFD1B2E0),
                    outlineVariant = Color(0xFFE9D8F2)
                )
            }
            else -> { // "Natural Tones"
                lightColorScheme(
                    primary = accentColor,
                    secondary = Color(0xFF435345),
                    tertiary = Color(0xFF435345),
                    background = Color(0xFFFDFCF9), // Warm organic sand/beige
                    surface = Color.White,
                    onPrimary = Color.White,
                    onSecondary = Color(0xFF1B1C17),
                    onBackground = Color(0xFF1B1C17),
                    onSurface = Color(0xFF1B1C17),
                    surfaceVariant = Color(0xFFE1E3D3),
                    onSurfaceVariant = Color(0xFF44483D),
                    outline = Color(0xFF75796C),
                    outlineVariant = Color(0xFFC5C8BA)
                )
            }
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
