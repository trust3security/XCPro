package com.example.dfcards

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun DFCardsTheme(
    content: @Composable () -> Unit
) {
    // Simply use whatever MaterialTheme is already provided by the host project
    // This makes the library truly theme-agnostic
    content()
}

// Keep this as a fallback theme for standalone usage
@Composable
fun DFCardsStandaloneTheme(
  //  darkTheme: Boolean = isSystemInDarkTheme(),
    flightTheme: FlightThemeMode = FlightThemeMode.DAY,
    content: @Composable () -> Unit
) {
    val colorScheme = when (flightTheme) {
      //  FlightThemeMode.NIGHT -> nightFlightColorScheme
        FlightThemeMode.HIGH_CONTRAST -> highContrastColorScheme
        else -> lightColorScheme(
            primary = Color(0xFF007AFF),
            primaryContainer = Color(0xFFD0E4FF),
            secondary = Color(0xFF34C759),
            secondaryContainer = Color(0xFFB3E9C0),
            background = Color(0xFFF5F7FA),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color(0xFFFFFFFF),
            onSecondary = Color(0xFF000000),
            onBackground = Color(0xFF1C2526),
            onSurface = Color(0xFF1C2526),
            onSurfaceVariant = Color(0xFF6B7280),
            error = Color(0xFFFF3B30),
            outlineVariant = Color(0xFFE5E7EB)
        )
    }

    val flightTypography = Typography(
        headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Typography().titleMedium.copy(fontSize = 14.sp),
        bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = Typography().bodyMedium.copy(fontSize = 8.sp)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = flightTypography,
        content = content
    )
}

enum class FlightThemeMode {
    DAY, HIGH_CONTRAST
}

// Night flight color scheme (red tints to preserve night vision)
private val nightFlightColorScheme = darkColorScheme(
    primary = Color(0xFF000000),
    primaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF1976D2),
    secondaryContainer = Color(0xFFE3F2FD),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000)
)

// High contrast color scheme for bright sunlight
private val highContrastColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    primaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF1976D2),
    secondaryContainer = Color(0xFFE3F2FD),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000)
)