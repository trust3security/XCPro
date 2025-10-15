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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

// ✅ Look & Feel Menu Item
data class LookAndFeelOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String? = null
)

// ✅ Status Bar Styles
enum class StatusBarStyle(
    val id: String,
    val title: String,
    val description: String,
    val detailedDescription: String,
    val icon: ImageVector,
    val example: String
) {
    TRANSPARENT(
        id = "transparent",
        title = "Transparent",
        description = "Map shows through the status bar",
        detailedDescription = "The status bar becomes completely transparent, allowing the map to be visible behind it. This creates a seamless, immersive experience where your flight data overlays directly on the map.",
        icon = Icons.Default.VisibilityOff,
        example = "🗺️ Full map visibility"
    ),
    THEMED(
        id = "themed",
        title = "Themed",
        description = "Status bar matches your app theme",
        detailedDescription = "The status bar color adapts to your current app theme, creating a cohesive look. It changes between light and dark modes automatically, maintaining visual consistency throughout the app.",
        icon = Icons.Default.Palette,
        example = "🎨 Adaptive colors"
    ),
    EDGE_TO_EDGE(
        id = "edge_to_edge",
        title = "Edge to Edge",
        description = "Content extends under the status bar",
        detailedDescription = "Your content extends edge-to-edge on the screen with a subtle scrim on the status bar. This modern approach maximizes screen real estate while keeping system icons visible.",
        icon = Icons.Default.Fullscreen,
        example = "📱 Modern full-screen"
    ),
    OVERLAY(
        id = "overlay",
        title = "Overlay",
        description = "Semi-transparent overlay on map",
        detailedDescription = "A semi-transparent overlay provides the perfect balance between map visibility and status bar readability. System icons remain clearly visible while the map shows through subtly.",
        icon = Icons.Default.Layers,
        example = "🔍 Balanced visibility"
    )
}

// ✅ Card Styles
enum class CardStyle(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    COMPACT(
        id = "compact",
        title = "Compact",
        description = "Small cards with essential info",
        icon = Icons.Outlined.ViewCompact
    ),
    STANDARD(
        id = "standard", 
        title = "Standard",
        description = "Medium cards with balanced layout",
        icon = Icons.Outlined.ViewModule
    ),
    LARGE(
        id = "large",
        title = "Large", 
        description = "Big cards with detailed information",
        icon = Icons.Outlined.ViewAgenda
    ),
    MINIMAL(
        id = "minimal",
        title = "Minimal",
        description = "Ultra-clean with only key data",
        icon = Icons.Outlined.RemoveRedEye
    )
}

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
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    Text(
                        text = "Look & Feel",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        scope.launch {
                            navController.popBackStack()
                            drawerState.open()
                        }
                    }) {
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

@Composable
private fun LookAndFeelMenuItem(
    option: LookAndFeelOption,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusBarStyleOption(
    style: StatusBarStyle,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = style.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = style.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = style.example,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CardStyleOption(
    style: CardStyle,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = style.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = style.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
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