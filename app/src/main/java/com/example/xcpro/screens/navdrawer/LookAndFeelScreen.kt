package com.example.ui1.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import com.example.xcpro.profiles.ProfileViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcpro.MainActivity
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar

// ✅ Look & Feel Menu Item
// moved to LookAndFeelModels.kt
// ✅ Status Bar Styles
// moved to LookAndFeelModels.kt
// ✅ Card Styles
// moved to LookAndFeelModels.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookAndFeelScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileViewModel: ProfileViewModel = viewModel()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val profileId = profileUiState.activeProfile?.id ?: "default"
    
    // State for expanded sections
    var showStatusBarOptions by remember { mutableStateOf(false) }
    var showCardStyleOptions by remember { mutableStateOf(false) }
    var showColorsOptions by remember { mutableStateOf(false) }
    
    // Get current preferences
    val sharedPrefs = remember { context.getSharedPreferences("LookAndFeelPrefs", Context.MODE_PRIVATE) }
    var selectedStatusBarStyle by remember(profileId) { 
        mutableStateOf(
            StatusBarStyle.values().find { 
                it.id == sharedPrefs.getString("profile_${profileId}_status_bar_style", StatusBarStyle.TRANSPARENT.id) 
            } ?: StatusBarStyle.TRANSPARENT
        )
    }
    var selectedCardStyle by remember(profileId) { 
        mutableStateOf(
            CardStyle.values().find { 
                it.id == sharedPrefs.getString("profile_${profileId}_card_style", CardStyle.STANDARD.id) 
            } ?: CardStyle.STANDARD
        )
    }

    // Menu options
    val lookAndFeelOptions = listOf(
        LookAndFeelOption(
            id = "colors",
            title = "Colors",
            subtitle = "App color theme",
            icon = Icons.Outlined.ColorLens,
            route = "colors"
        ),
        LookAndFeelOption(
            id = "status_bar",
            title = "Status Bar Style",
            subtitle = selectedStatusBarStyle.title,
            icon = Icons.Outlined.PhoneAndroid
        ),
        LookAndFeelOption(
            id = "card_style",
            title = "Card Style",
            subtitle = selectedCardStyle.title,
            icon = Icons.Outlined.Dashboard
        ),
        LookAndFeelOption(
            id = "theme",
            title = "Theme",
            subtitle = "System default",
            icon = Icons.Outlined.Palette
        ),
        LookAndFeelOption(
            id = "animations",
            title = "Animations",
            subtitle = "Enabled",
            icon = Icons.Outlined.Animation
        ),
        LookAndFeelOption(
            id = "font_size",
            title = "Font Size",
            subtitle = "Medium",
            icon = Icons.Outlined.FormatSize
        )
    )

    Scaffold(
        topBar = {
            // ✅ Match Flight Data header style exactly
            SettingsTopAppBar(
                title = "Look & Feel",
                onNavigateUp = { navController.popBackStack() },
                onOpenDrawer = {
                    scope.launch {
                        navController.popBackStack("map", inclusive = false)
                        drawerState.open()
                    }
                },
                onNavigateToMap = {
                    scope.launch {
                        drawerState.close()
                        navController.popBackStack("map", inclusive = false)
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lookAndFeelOptions) { option ->
                LookAndFeelMenuItem(
                    option = option,
                    onClick = {
                        when (option.id) {
                            "colors" -> showColorsOptions = true
                            "status_bar" -> showStatusBarOptions = true
                            "card_style" -> showCardStyleOptions = true
                            // Handle other options
                        }
                    }
                )
            }
        }
    }

    // ✅ Status Bar Style Bottom Sheet
    if (showStatusBarOptions) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true // Force full expansion, no half-state
        )
        
        ModalBottomSheet(
            onDismissRequest = { showStatusBarOptions = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight() // Use actual content height
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Status Bar Style",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                StatusBarStyle.values().forEach { style ->
                    StatusBarStyleOption(
                        style = style,
                        isSelected = selectedStatusBarStyle == style,
                        onSelect = {
                            selectedStatusBarStyle = style
                            // Save preference
                            sharedPrefs.edit()
                                .putString("profile_${profileId}_status_bar_style", style.id)
                                .apply()
                            // Apply style immediately
                            (context as? MainActivity)?.applyUserStatusBarStyle(profileId)
                            showStatusBarOptions = false
                        }
                    )
                    if (style != StatusBarStyle.values().last()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ✅ Card Style Bottom Sheet
    if (showCardStyleOptions) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true // Force full expansion, no half-state
        )
        
        ModalBottomSheet(
            onDismissRequest = { showCardStyleOptions = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight() // Use actual content height
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Card Style",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                CardStyle.values().forEach { style ->
                    CardStyleOption(
                        style = style,
                        isSelected = selectedCardStyle == style,
                        onSelect = {
                            selectedCardStyle = style
                            // Save preference
                            sharedPrefs.edit()
                                .putString("profile_${profileId}_card_style", style.id)
                                .apply()
                            showCardStyleOptions = false
                        }
                    )
                    if (style != CardStyle.values().last()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // ✅ Colors Bottom Sheet
    if (showColorsOptions) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true // Force full expansion, no half-state
        )
        
        ModalBottomSheet(
            onDismissRequest = { showColorsOptions = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Colors",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Choose your app's color theme",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Get current color theme with reactive state
                var currentTheme by remember(profileId) {
                    mutableStateOf(
                        com.example.xcpro.ui.theme.AppColorTheme.values().find { 
                            it.id == sharedPrefs.getString("profile_${profileId}_color_theme", com.example.xcpro.ui.theme.AppColorTheme.DEFAULT.id)
                        } ?: com.example.xcpro.ui.theme.AppColorTheme.DEFAULT
                    )
                }
                
                // Quick color theme options (use actual AppColorTheme enum values)
                val quickColorThemes = listOf(
                    com.example.xcpro.ui.theme.AppColorTheme.DEFAULT,
                    com.example.xcpro.ui.theme.AppColorTheme.AVIATION,
                    com.example.xcpro.ui.theme.AppColorTheme.FOREST,
                    com.example.xcpro.ui.theme.AppColorTheme.SUNSET,
                    com.example.xcpro.ui.theme.AppColorTheme.OCEAN,
                    com.example.xcpro.ui.theme.AppColorTheme.PURPLE
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickColorThemes) { theme ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    // Save the selected color theme
                                    val colorPrefs = context.getSharedPreferences("ColorThemePrefs", Context.MODE_PRIVATE)
                                    colorPrefs.edit()
                                        .putString("profile_${profileId}_color_theme", theme.id)
                                        .apply()
                                    
                                    // Update local state immediately for UI feedback
                                    currentTheme = theme
                                    
                                    android.util.Log.d("LookAndFeel", "🎨 Color theme changed to: ${theme.displayName}")
                                    showColorsOptions = false 
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentTheme.id == theme.id) 
                                    MaterialTheme.colorScheme.primaryContainer
                                else 
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = if (currentTheme.id == theme.id) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(theme.primaryColor, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(theme.secondaryColor, CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (currentTheme.id == theme.id) FontWeight.Bold else FontWeight.Medium,
                                        color = if (currentTheme.id == theme.id) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = theme.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (currentTheme.id == theme.id) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // "More Colors" button to go to full ColorsScreen
                OutlinedButton(
                    onClick = {
                        showColorsOptions = false
                        navController.navigate("colors")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("More Color Options & Custom Colors")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/* moved to LookAndFeelSections.kt */
// moved to LookAndFeelSections.kt

/* moved to LookAndFeelSections.kt */
// moved to LookAndFeelSections.kt

/* moved to LookAndFeelSections.kt */
// moved to LookAndFeelSections.kt


