package com.example.xcpro.screens.navdrawer

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.xcpro.profiles.ProfileViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcpro.ui.theme.AppColorTheme
import com.example.xcpro.ui.theme.CustomColorScheme
import org.json.JSONObject

/**
 * Colors Screen - Main entry point for color theme customization
 *
 * Refactored for file size compliance (<500 lines).
 * Uses extracted components from:
 * - ColorsScreenComponents.kt: Preview/card components
 * - ColorsScreenPickers.kt: Color picker components
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = viewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val profileId = profileUiState.activeProfile?.id ?: "default"

    // Load current theme
    val sharedPrefs = remember { context.getSharedPreferences("ColorThemePrefs", Context.MODE_PRIVATE) }
    var selectedTheme by remember(profileId) {
        mutableStateOf(
            AppColorTheme.values().find {
                it.id == sharedPrefs.getString("profile_${profileId}_color_theme", AppColorTheme.DEFAULT.id)
            } ?: AppColorTheme.DEFAULT
        )
    }

    var showColorEditor by remember(profileId) { mutableStateOf(false) } // Keep state across theme changes
    var selectedColorType by remember { mutableStateOf("primary") } // Only primary color now

    // Current theme colors (can be modified from any base theme)
    var currentPrimary by remember { mutableStateOf(selectedTheme.primaryColor) }
    var currentSecondary by remember { mutableStateOf(selectedTheme.secondaryColor) }

    // Load custom colors for the selected theme
    LaunchedEffect(profileId, selectedTheme) {
        val customColors = loadCustomColors(context, profileId, selectedTheme.id)
        if (customColors != null) {
            Log.d("ColorsScreen", "📱 Loading custom colors for theme ${selectedTheme.id}")
            try {
                currentPrimary = customColors.toPrimaryColor()
                currentSecondary = customColors.toSecondaryColor()
                Log.d("ColorsScreen", "✅ Successfully loaded custom colors")
            } catch (e: Exception) {
                Log.e("ColorsScreen", "❌ Error loading custom colors: ${e.message}")
                // Clear broken custom colors and reset to defaults
                removeCustomColors(context, profileId, selectedTheme.id)
                currentPrimary = selectedTheme.primaryColor
                currentSecondary = selectedTheme.secondaryColor
            }
        } else {
            Log.d("ColorsScreen", "📱 No custom colors found, using theme defaults")
            // Reset to theme defaults
            currentPrimary = selectedTheme.primaryColor
            currentSecondary = selectedTheme.secondaryColor
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    Text(
                        text = "Colors",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.popBackStack("map", inclusive = false)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Go to Map"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current Theme Preview
            item {
                CurrentThemePreview(
                    theme = selectedTheme,
                    primaryColor = currentPrimary,
                    secondaryColor = currentSecondary,
                    selectedColorType = selectedColorType,
                    onColorSelected = { colorType ->
                        Log.d("ColorsScreen", "🎯 Selected color type changed to: $colorType")
                        selectedColorType = colorType
                    },
                    onEditColors = { showColorEditor = true }
                )
            }

            // Theme Selection Grid
            item {
                Text(
                    text = "Choose Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(600.dp) // Fixed height for nested scrolling
                ) {
                    items(AppColorTheme.values()) { theme ->
                        ColorThemeCard(
                            theme = theme,
                            isSelected = selectedTheme == theme,
                            onClick = {
                                selectedTheme = theme
                                saveColorTheme(context, profileId, theme)
                                // DON'T close the bottom sheet - keep it open for more selections
                            }
                        )
                    }
                }
            }
        }
    }

    // Custom Color Picker Bottom Sheet
    if (showColorEditor) {
        ModalBottomSheet(
            onDismissRequest = {
                // Don't auto-close when tapping outside - require manual close
                // Save colors but keep sheet open
                val customColors = CustomColorScheme.fromColors(
                    currentPrimary, selectedTheme.secondaryColor
                )
                saveCustomColors(context, profileId, customColors, selectedTheme.id)
            }
        ) {
            // Add top padding to avoid overlapping with the top bar
            Column(
                modifier = Modifier.padding(top = 64.dp)
            ) {
                SingleColorPicker(
                    themeDisplayName = selectedTheme.displayName,
                    selectedColorType = selectedColorType,
                    selectedColor = currentPrimary, // Only primary color now
                    onColorChanged = { newColor ->
                        Log.d("ColorsScreen", "🔄 PRIMARY color changed from $currentPrimary to $newColor")
                        currentPrimary = newColor
                        // Immediately save and apply the change
                        val customColors = CustomColorScheme.fromColors(
                            currentPrimary, selectedTheme.secondaryColor // Use theme default for secondary
                        )
                        Log.d("ColorsScreen", "🔄 About to save ${selectedColorType.uppercase()} change for profile=$profileId, theme=${selectedTheme.id}")
                        saveCustomColors(context, profileId, customColors, selectedTheme.id)
                    },
                    onSave = {
                        // Manual close button - close the sheet
                        val customColors = CustomColorScheme.fromColors(
                            currentPrimary, selectedTheme.secondaryColor
                        )
                        saveCustomColors(context, profileId, customColors, selectedTheme.id)
                        showColorEditor = false
                    },
                    onReset = {
                        showColorEditor = false
                    },
                    onCancel = {
                        // Reload original colors
                        val customColors = loadCustomColors(context, profileId, selectedTheme.id)
                        if (customColors != null) {
                            currentPrimary = customColors.toPrimaryColor()
                            currentSecondary = customColors.toSecondaryColor()
                        } else {
                            currentPrimary = selectedTheme.primaryColor
                            currentSecondary = selectedTheme.secondaryColor
                        }
                        showColorEditor = false
                    }
                )
            }
        }
    }
}

// ==================== PERSISTENCE FUNCTIONS ====================

/** Save selected color theme to preferences */
private fun saveColorTheme(context: Context, profileId: String, theme: AppColorTheme) {
    val sharedPrefs = context.getSharedPreferences("ColorThemePrefs", Context.MODE_PRIVATE)
    sharedPrefs.edit()
        .putString("profile_${profileId}_color_theme", theme.id)
        .apply()
}

/** Save custom colors for a theme */
private fun saveCustomColors(context: Context, profileId: String, colors: CustomColorScheme, themeId: String) {
    val sharedPrefs = context.getSharedPreferences("ColorThemePrefs", Context.MODE_PRIVATE)
    val json = JSONObject().apply {
        put("primaryColor", colors.primaryColor)
        put("secondaryColor", colors.secondaryColor)
    }
    val key = "profile_${profileId}_theme_${themeId}_custom_colors"
    Log.d("ColorsScreen", "🎨 Saving custom colors for profile=$profileId, theme=$themeId")
    Log.d("ColorsScreen", "🎨 Key: $key")
    Log.d("ColorsScreen", "🎨 Colors: ${json.toString()}")

    sharedPrefs.edit()
        .putString(key, json.toString())
        .apply()

    // Verify save
    val saved = sharedPrefs.getString(key, null)
    Log.d("ColorsScreen", "🎨 Verified saved: $saved")
}

/** Load custom colors for a theme */
private fun loadCustomColors(context: Context, profileId: String, themeId: String): CustomColorScheme? {
    val sharedPrefs = context.getSharedPreferences("ColorThemePrefs", Context.MODE_PRIVATE)
    val jsonString = sharedPrefs.getString("profile_${profileId}_theme_${themeId}_custom_colors", null)
    return jsonString?.let {
        try {
            val json = JSONObject(it)
            CustomColorScheme(
                primaryColor = json.getString("primaryColor"),
                secondaryColor = json.getString("secondaryColor")
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Remove custom colors for a theme */
private fun removeCustomColors(context: Context, profileId: String, themeId: String) {
    val sharedPrefs = context.getSharedPreferences("ColorThemePrefs", Context.MODE_PRIVATE)
    sharedPrefs.edit()
        .remove("profile_${profileId}_theme_${themeId}_custom_colors")
        .apply()
}
