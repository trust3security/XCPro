package com.example.xcpro.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ✅ Predefined Color Themes
enum class AppColorTheme(
    val id: String,
    val displayName: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color
) {
    DEFAULT(
        id = "default",
        displayName = "Default Blue",
        description = "Classic professional blue theme",
        primaryColor = Color(0xFF2196F3),
        secondaryColor = Color(0xFF03A9F4)
    ),
    AVIATION(
        id = "aviation",
        displayName = "Aviation",
        description = "High contrast aviation theme",
        primaryColor = Color(0xFF1565C0),
        secondaryColor = Color(0xFF0D47A1)
    ),
    FOREST(
        id = "forest",
        displayName = "Forest Green",
        description = "Natural green theme for outdoor flying",
        primaryColor = Color(0xFF2E7D32),
        secondaryColor = Color(0xFF388E3C)
    ),
    SUNSET(
        id = "sunset",
        displayName = "Sunset",
        description = "Warm sunset colors",
        primaryColor = Color(0xFFE65100),
        secondaryColor = Color(0xFFFF6F00)
    ),
    OCEAN(
        id = "ocean",
        displayName = "Ocean",
        description = "Deep ocean blue theme",
        primaryColor = Color(0xFF006064),
        secondaryColor = Color(0xFF00838F)
    ),
    MONOCHROME(
        id = "monochrome",
        displayName = "Monochrome",
        description = "Clean black and white theme",
        primaryColor = Color(0xFF212121),
        secondaryColor = Color(0xFF424242)
    ),
    PURPLE(
        id = "purple",
        displayName = "Royal Purple",
        description = "Elegant purple theme",
        primaryColor = Color(0xFF6A1B9A),
        secondaryColor = Color(0xFF7B1FA2)
    ),
    THERMAL(
        id = "thermal",
        displayName = "Thermal",
        description = "Heat map inspired colors",
        primaryColor = Color(0xFFD32F2F),
        secondaryColor = Color(0xFFE53935)
    ),
    NIGHT(
        id = "night",
        displayName = "Night Flight",
        description = "Dark theme for night flying",
        primaryColor = Color(0xFF1A237E),
        secondaryColor = Color(0xFF283593)
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom",
        description = "Create your own color scheme",
        primaryColor = Color(0xFF2196F3),
        secondaryColor = Color(0xFF03A9F4)
    )
}

// ✅ Create Material3 Color Schemes
fun AppColorTheme.toLightColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = primaryColor,
        onPrimary = Color.White,
        primaryContainer = primaryColor.copy(alpha = 0.1f),
        onPrimaryContainer = primaryColor,
        
        secondary = secondaryColor,
        onSecondary = Color.White,
        secondaryContainer = secondaryColor.copy(alpha = 0.1f),
        onSecondaryContainer = secondaryColor,
        
        
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        
        background = Color(0xFFFDFDFD),
        onBackground = Color(0xFF1A1C1E),
        
        surface = Color.White,
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFE0E3E8),
        onSurfaceVariant = Color(0xFF44474C),
        
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6CF),
        
        scrim = Color.Black,
        inverseSurface = Color(0xFF2F3133),
        inverseOnSurface = Color(0xFFF1F0F4),
        inversePrimary = primaryColor.copy(alpha = 0.8f)
    )
}

fun AppColorTheme.toDarkColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = primaryColor.copy(alpha = 0.9f),
        onPrimary = Color.Black,
        primaryContainer = primaryColor.copy(alpha = 0.3f),
        onPrimaryContainer = primaryColor.copy(alpha = 0.9f),
        
        secondary = secondaryColor.copy(alpha = 0.9f),
        onSecondary = Color.Black,
        secondaryContainer = secondaryColor.copy(alpha = 0.3f),
        onSecondaryContainer = secondaryColor.copy(alpha = 0.9f),
        
        
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        
        background = Color(0xFF1A1C1E),
        onBackground = Color(0xFFE3E2E6),
        
        surface = Color(0xFF1A1C1E),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = Color(0xFF44474C),
        onSurfaceVariant = Color(0xFFC4C6CF),
        
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474C),
        
        scrim = Color.Black,
        inverseSurface = Color(0xFFE3E2E6),
        inverseOnSurface = Color(0xFF1A1C1E),
        inversePrimary = primaryColor
    )
}

// ✅ Custom color data class for saving
data class CustomColorScheme(
    val primaryColor: String,
    val secondaryColor: String
) {
    fun toPrimaryColor(): Color {
        val argbValue = try {
            primaryColor.toLong(16).toInt()
        } catch (e: NumberFormatException) {
            android.util.Log.w("ColorThemes", "⚠️ Failed to parse primary color hex '$primaryColor', using default")
            0xFF2196F3.toInt()
        }
        android.util.Log.d("ColorThemes", "🎨 Converting primary hex '$primaryColor' to ARGB: $argbValue")
        return Color(argbValue)
    }
    
    fun toSecondaryColor(): Color {
        val argbValue = try {
            secondaryColor.toLong(16).toInt()
        } catch (e: NumberFormatException) {
            android.util.Log.w("ColorThemes", "⚠️ Failed to parse secondary color hex '$secondaryColor', using default")
            0xFF03A9F4.toInt()
        }
        android.util.Log.d("ColorThemes", "🎨 Converting secondary hex '$secondaryColor' to ARGB: $argbValue")
        return Color(argbValue)
    }
    
    
    companion object {
        fun fromColors(
            primary: Color,
            secondary: Color
        ): CustomColorScheme {
            // Convert Color to ARGB Int using the correct toArgb() method
            val primaryArgb = primary.toArgb()
            val secondaryArgb = secondary.toArgb()
            
            val primaryHex = String.format("%08X", primaryArgb)
            val secondaryHex = String.format("%08X", secondaryArgb)
            
            android.util.Log.d("ColorThemes", "🎨 Converting colors to hex:")
            android.util.Log.d("ColorThemes", "🎨 Primary: $primaryArgb -> $primaryHex")
            android.util.Log.d("ColorThemes", "🎨 Secondary: $secondaryArgb -> $secondaryHex")
            
            return CustomColorScheme(
                primaryColor = primaryHex,
                secondaryColor = secondaryHex
            )
        }
    }
}