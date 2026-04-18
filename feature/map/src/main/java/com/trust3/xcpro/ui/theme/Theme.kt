package com.trust3.xcpro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.profiles.ProfileIdResolver
import com.trust3.xcpro.profiles.ProfileViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

@Suppress("UNUSED_PARAMETER")
@Composable
fun Baseui1Theme(
    darkTheme: Boolean = false, // Force light mode only
    content: @Composable () -> Unit
) {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val profileUiState = profileViewModel.uiState.collectAsStateWithLifecycle()
    val profileId = ProfileIdResolver.canonicalOrDefault(profileUiState.value.activeProfile?.id)
    LaunchedEffect(profileId) {
        themeViewModel.setProfileId(profileId)
    }
    val themeUiState by themeViewModel.uiState.collectAsStateWithLifecycle()
    val themeId = themeUiState.themeId
    val customColorsJson = themeUiState.customColorsJson

    val effectiveDarkTheme = false

    val colorScheme = remember(profileId, themeId, customColorsJson) {
        loadUserColorScheme(
            profileId = profileId,
            themeId = themeId,
            customColorsJson = customColorsJson,
            darkTheme = effectiveDarkTheme
        )
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

private fun loadUserColorScheme(
    profileId: String,
    themeId: String,
    customColorsJson: String?,
    darkTheme: Boolean
): androidx.compose.material3.ColorScheme {
    val selectedTheme = AppColorTheme.values().find { it.id == themeId } ?: AppColorTheme.DEFAULT

    AppLogger.d(
        "Theme",
        "Loading color scheme for profile=$profileId, theme=$themeId, customColorsLength=${customColorsJson?.length ?: 0}"
    )

    return if (customColorsJson != null) {
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
