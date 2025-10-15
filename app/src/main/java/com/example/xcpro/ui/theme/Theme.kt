package com.example.xcpro.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcpro.profiles.ProfileViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary, // 0xFF007AFF - blue
    primaryContainer = BlueContainer, // 0xFFD0E4FF - light blue
    secondary = GreenSecondary, // 0xFF34C759
    secondaryContainer = GreenContainer, // 0xFFB3E9C0
    background = BackgroundLight, // 0xFFF5F7FA
    surface = SurfaceLight, // 0xFFFFFFFF
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000), // BLACK TEXT
    onBackground = Color(0xFF000000), // BLACK TEXT
    onSurface = Color(0xFF000000), // BLACK TEXT - this is what you want
    onSurfaceVariant = OnSurfaceVariantGray, // 0xFF6B7280
    error = RedError,
    outlineVariant = OutlineVariantLight
)

@Composable
fun Baseui1Theme(
    darkTheme: Boolean = false, // Force light mode only
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = viewModel()
    val profileUiState = profileViewModel.uiState.collectAsState()
    val profileId = profileUiState.value.activeProfile?.id ?: "default"
    
    // Create a state for the current theme ID that can be observed
    val sharedPrefs = remember { context.getSharedPreferences("ColorThemePrefs", Context.MODE_PRIVATE) }
    var currentThemeId by remember(profileId) { 
        mutableStateOf<String>(sharedPrefs.getString("profile_${profileId}_color_theme", AppColorTheme.DEFAULT.id) ?: AppColorTheme.DEFAULT.id)
    }
    
    // React to theme changes by checking SharedPreferences periodically
    LaunchedEffect(profileId) {
        while (true) {
            delay(100) // Check every 100ms for faster theme changes
            val newThemeId = sharedPrefs.getString("profile_${profileId}_color_theme", AppColorTheme.DEFAULT.id) ?: AppColorTheme.DEFAULT.id
            if (currentThemeId != newThemeId) {
                currentThemeId = newThemeId
            }
        }
    }
    
    // Track custom colors changes to force theme reload
    var customColorsTrigger by remember { mutableStateOf(0) }
    
    // Check for custom color changes
    var lastCustomColorsJson by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(profileId, currentThemeId) {
        while (true) {
            delay(100) // Check every 100ms for custom color changes
            val newCustomColorsJson = sharedPrefs.getString("profile_${profileId}_theme_${currentThemeId}_custom_colors", null)
            // Only increment trigger when custom colors actually change
            if (newCustomColorsJson != lastCustomColorsJson) {
                lastCustomColorsJson = newCustomColorsJson
                customColorsTrigger++
                android.util.Log.d("Theme", "🎨 Custom colors changed, triggering recomposition. New JSON: $newCustomColorsJson")
            }
        }
    }
    
    // Load color scheme based on current theme (recomputes when customColorsTrigger changes)
    // Always use light mode (darkTheme = false)
    val colorScheme = remember(profileId, currentThemeId, customColorsTrigger) {
        loadUserColorScheme(context, profileId, false)
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography.copy(
            headlineSmall = Typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = Typography.titleMedium.copy(fontSize = 16.sp),
            bodyLarge = Typography.bodyLarge.copy(fontSize = 14.sp),
            bodyMedium = Typography.bodyMedium.copy(fontSize = 12.sp)
        ),
        content = content
    )
}

private fun loadUserColorScheme(context: Context, profileId: String, darkTheme: Boolean): androidx.compose.material3.ColorScheme {
    val sharedPrefs = context.getSharedPreferences("ColorThemePrefs", Context.MODE_PRIVATE)
    val themeId = sharedPrefs.getString("profile_${profileId}_color_theme", AppColorTheme.DEFAULT.id)
    
    // Try to find the theme from predefined themes
    val selectedTheme = AppColorTheme.values().find { it.id == themeId } ?: AppColorTheme.DEFAULT
    
    // Check if there are custom colors for this specific theme
    val customColorsJson = sharedPrefs.getString("profile_${profileId}_theme_${themeId}_custom_colors", null)
    
    android.util.Log.d("Theme", "🎨 Loading color scheme for profile=$profileId, theme=$themeId")
    android.util.Log.d("Theme", "🎨 Custom colors JSON: $customColorsJson")
    
    return if (customColorsJson != null) {
        // Use custom colors for this theme
        try {
            val gson = Gson()
            val customColors = gson.fromJson<CustomColorScheme>(
                customColorsJson,
                object : TypeToken<CustomColorScheme>() {}.type
            )
            
            // Create custom ColorScheme directly
            createCustomColorScheme(
                customColors.toPrimaryColor(),
                customColors.toSecondaryColor(),
                darkTheme
            )
        } catch (e: Exception) {
            // Fallback to theme defaults if custom colors are invalid
            if (darkTheme) selectedTheme.toDarkColorScheme() else selectedTheme.toLightColorScheme()
        }
    } else {
        // Use theme defaults
        if (darkTheme) selectedTheme.toDarkColorScheme() else selectedTheme.toLightColorScheme()
    }
}

private fun createCustomColorScheme(
    primaryColor: Color,
    secondaryColor: Color,
    darkTheme: Boolean
): androidx.compose.material3.ColorScheme {
    return if (darkTheme) {
        androidx.compose.material3.darkColorScheme(
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
    } else {
        androidx.compose.material3.lightColorScheme(
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
}