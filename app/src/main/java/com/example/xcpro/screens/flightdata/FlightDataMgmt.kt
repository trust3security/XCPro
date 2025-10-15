package com.example.ui1.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.xcpro.loadAirspaceFiles
import com.example.xcpro.loadSelectedClasses
import com.example.xcpro.loadWaypointFiles
import com.example.xcpro.saveAirspaceFiles
import com.example.xcpro.saveWaypointFiles
import com.example.dfcards.*
import com.example.ui1.screens.flightmgmt.FlightDataAirspaceTab
import com.example.ui1.screens.flightmgmt.FlightDataScreensTab
import com.example.xcpro.screens.flightdata.FlightDataWaypointsTab
import com.example.ui1.screens.flightmgmt.FlightDataClassesTab
import com.example.xcpro.profiles.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "FlightMgmt"

// Components are now imported from FlightDataComponents.kt

// FlightMgmtTabs component moved to FlightDataComponents.kt

// ModernTabItem component moved to FlightDataComponents.kt

// AddFileButton component moved to FlightDataComponents.kt

// SectionHeader component moved to FlightDataComponents.kt

// FileItemCard component moved to FlightDataComponents.kt

// AirspaceClassCard component moved to FlightDataComponents.kt

// TemplateEditorModal component moved to TemplateEditorModal.kt

// CompactCardItem component moved to FlightDataComponents.kt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FlightMgmt(
    navController: NavHostController,
    drawerState: DrawerState,
    initialTab: String = "screens",
    autoFocusHome: Boolean = false, // ADD THIS PARAMETER
    activeProfile: UserProfile? = null // ✅ ADD: Active profile for profile-aware template management
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cardPreferences = remember { CardPreferences(context) }

    // ✅ DEBUG: Log active profile information
    Log.d(TAG, "🔧 FlightMgmt initialized with activeProfile: ${activeProfile?.name ?: "NULL"} (ID: ${activeProfile?.id ?: "NULL"})")

    val sharedPrefs = remember { context.getSharedPreferences("FlightMgmtPrefs", Context.MODE_PRIVATE) }

    // TODO: Restore live flight data for card previews using UnifiedSensorManager
    var liveFlightData by remember { mutableStateOf<RealTimeFlightData?>(null) }
    // FlightDataProvider requires dataProvider parameter - needs refactoring
    // FlightDataProvider { data ->
    //     liveFlightData = data
    // }
    var activeTab by remember {
        mutableStateOf(initialTab)
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    var selectedFlightMode by remember(activeProfile?.id) { 
        mutableStateOf(
            // Restore last selected flight mode for this profile, default to CRUISE
            FlightModeSelection.values().find { mode ->
                sharedPrefs.getString("profile_${activeProfile?.id}_last_flight_mode", null) == mode.name
            } ?: FlightModeSelection.CRUISE
        )
    }
    var allTemplates by remember { mutableStateOf<List<FlightTemplate>>(emptyList()) }
    var selectedTemplate by remember { mutableStateOf<FlightTemplate?>(null) }
    var selectedCategory by remember { mutableStateOf(CardCategory.ESSENTIAL) }

    var showTemplateEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<FlightTemplate?>(null) }

    val selectedWaypointFiles = remember { mutableStateListOf<Uri>() }
    val waypointCheckedStates = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }

    val selectedAirspaceFiles = remember { mutableStateListOf<Uri>() }
    val airspaceCheckedStates = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    val airspaceClassStates = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    
    // ✅ NEW: Dynamic screens count based on visible flight modes
    var visibleFlightModesCount by remember { mutableStateOf(3) } // Default to 3

    LaunchedEffect(activeTab) {
        sharedPrefs.edit().putString("last_active_tab", activeTab).apply()
        Log.d(TAG, "💾 Saved active tab: $activeTab")
    }
    
    // ✅ NEW: Update screens count based on active profile's flight mode visibilities
    LaunchedEffect(activeProfile?.id) {
        activeProfile?.let { profile ->
            try {
                val visibilities = cardPreferences.getProfileAllFlightModeVisibilities(profile.id).first()
                visibleFlightModesCount = visibilities.values.count { it }.coerceAtLeast(1) // Always at least 1 (Cruise)
                Log.d(TAG, "📊 Updated visible flight modes count: $visibleFlightModesCount for profile '${profile.name}'")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading flight mode visibilities: ${e.message}")
                visibleFlightModesCount = 1 // Fallback to minimum (Cruise only)
            }
        }
    }

    LaunchedEffect(Unit) {
        val (waypointFiles, waypointChecks) = loadWaypointFiles(context)
        selectedWaypointFiles.clear()
        selectedWaypointFiles.addAll(waypointFiles)
        waypointCheckedStates.value = waypointChecks

        val (airspaceFiles, airspaceChecks) = loadAirspaceFiles(context)
        selectedAirspaceFiles.clear()
        selectedAirspaceFiles.addAll(airspaceFiles)
        airspaceCheckedStates.value = airspaceChecks

        val savedClassStates = loadSelectedClasses(context) ?: mutableMapOf()
        airspaceClassStates.value = savedClassStates

        val templates = cardPreferences.getAllTemplates().first()
        allTemplates = templates

        if (cardPreferences.getFlightModeTemplate("CRUISE").first() == null) {
            cardPreferences.saveFlightModeTemplate("CRUISE", "id01")
        }
        if (cardPreferences.getFlightModeTemplate("THERMAL").first() == null) {
            cardPreferences.saveFlightModeTemplate("THERMAL", "id02")
        }
        if (cardPreferences.getFlightModeTemplate("FINAL_GLIDE").first() == null) {
            cardPreferences.saveFlightModeTemplate("FINAL_GLIDE", "id03")
        }
    }

    val waypointFileItems = selectedWaypointFiles.map { uri ->
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        FileItem(
            name = fileName,
            enabled = waypointCheckedStates.value[fileName] ?: false,
            count = 1250,
            status = if (waypointCheckedStates.value[fileName] == true) "Loaded" else "Disabled",
            uri = uri
        )
    }

    val airspaceFileItems = selectedAirspaceFiles.map { uri ->
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        FileItem(
            name = fileName,
            enabled = airspaceCheckedStates.value[fileName] ?: false,
            count = 200,
            status = if (airspaceCheckedStates.value[fileName] == true) "Loaded" else "Disabled",
            uri = uri
        )
    }

    if (showTemplateEditor && editingTemplate != null) {
        TemplateEditorModal(
            selectedCardIds = editingTemplate!!.cardIds.toSet(),
            existingTemplate = editingTemplate,
            liveFlightData = null,
            onSaveTemplate = { name, cardIds ->
                val updatedTemplate = editingTemplate!!.copy(name = name, cardIds = cardIds)
                selectedTemplate = updatedTemplate
                scope.launch {
                    try {
                        val updatedTemplates = allTemplates.map { t ->
                            if (t.id == editingTemplate!!.id) updatedTemplate else t
                        }
                        allTemplates = updatedTemplates
                        cardPreferences.saveAllTemplates(updatedTemplates)
                        showTemplateEditor = false
                        editingTemplate = null
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save edited template: ${e.message}", e)
                    }
                }
            },
            onDismiss = {
                showTemplateEditor = false
                editingTemplate = null
            }
        )
    } else {
        Scaffold(
            topBar = {
                FlightMgmtHeader(
                    onBackClick = {
                        scope.launch {
                            navController.popBackStack()
                            drawerState.open()
                        }
                    },
                    onMapClick = {
                        navController.navigate("map") {
                            popUpTo("map") { inclusive = false }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                FlightMgmtTabs(
                    activeTab = activeTab,
                    waypointCount = waypointFileItems.count { it.enabled },
                    airspaceCount = airspaceFileItems.count { it.enabled },
                    screensCount = visibleFlightModesCount, // ✅ Pass dynamic count
                    onTabSelected = { activeTab = it }
                )

                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) with
                                fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { tab ->
                    when (tab) {
                        "screens" -> {
                            FlightDataScreensTab(
                                selectedFlightMode = selectedFlightMode,
                                onFlightModeSelected = { mode ->
                                    selectedFlightMode = mode
                                    // Save the last selected flight mode for this profile
                                    activeProfile?.let { profile ->
                                        sharedPrefs.edit()
                                            .putString("profile_${profile.id}_last_flight_mode", mode.name)
                                            .apply()
                                        Log.d(TAG, "💾 Saved last flight mode for profile '${profile.name}': ${mode.name}")
                                    }
                                },
                                allTemplates = allTemplates,
                                selectedTemplate = selectedTemplate,
                                onTemplateSelected = { template ->
                                    selectedTemplate = template
                                    val updatedTemplates = allTemplates.map { t ->
                                        if (t.id == template.id) template else t
                                    }
                                    allTemplates = updatedTemplates
                                },
                                selectedCategory = selectedCategory,
                                onCategorySelected = { selectedCategory = it },
                                cardPreferences = cardPreferences,
                                scope = scope,
                                onEditTemplate = { template ->
                                    editingTemplate = template
                                    showTemplateEditor = true
                                },
                                // ✅ ADD: Pass active profile for profile-aware template management
                                activeProfile = activeProfile,
                                // ✅ NEW: Pass live flight data for card previews
                                liveFlightData = liveFlightData
                            )
                        }
                        "waypoints" -> {
                            FlightDataWaypointsTab(
                                selectedWaypointFiles = selectedWaypointFiles,
                                waypointCheckedStates = waypointCheckedStates.value,
                                onWaypointStateChanged = { newStates ->
                                    waypointCheckedStates.value = newStates
                                },
                                onShowDeleteDialog = { fileName ->
                                    showDeleteDialog = fileName to "waypoints"
                                },
                                onErrorMessage = { message ->
                                    errorMessage = message
                                },
                                scope = scope,
                                autoFocusHome = autoFocusHome, // ADD THIS
                                addFileButton = { type: String, onClick: () -> Unit -> AddFileButton(type, onClick) },
                                sectionHeader = { title: String, count: String -> SectionHeader(title, count) },
                                fileItemCard = { file: com.example.ui1.screens.FileItem, type: String, onToggle: (String) -> Unit, onDelete: (String) -> Unit ->
                                    FileItemCard(file, type, onToggle, onDelete)
                                }
                            )
                        }
                        "airspace" -> {
                            FlightDataAirspaceTab(
                                selectedAirspaceFiles = selectedAirspaceFiles,
                                airspaceCheckedStates = airspaceCheckedStates,
                                onAirspaceStateChanged = { airspaceCheckedStates.value = it },
                                airspaceClassStates = airspaceClassStates,
                                onAirspaceClassStateChanged = { newStates ->
                                    airspaceClassStates.value = newStates
                                },
                                onShowDeleteDialog = { fileName ->
                                    showDeleteDialog = fileName to "airspace"
                                },
                                onErrorMessage = { message -> errorMessage = message },
                                scope = scope,
                                addFileButton = { type, onClick -> AddFileButton(type, onClick) },
                                sectionHeader = { title, count -> SectionHeader(title, count) },
                                fileItemCard = { file, type, onToggle, onDelete -> FileItemCard(file, type, onToggle, onDelete) },
                                airspaceClassCard = { airspaceClass, onToggle -> AirspaceClassCard(airspaceClass, onToggle) }
                            )
                        }
                        "classes" -> {
                            FlightDataClassesTab(
                                airspaceClassItems = listOf(), // Empty for now, can be populated with actual airspace classes
                                selectedClasses = airspaceClassStates.value,
                                onSelectedClassesChanged = { newStates ->
                                    airspaceClassStates.value = newStates
                                },
                                sectionHeader = { title, count -> SectionHeader(title, count) },
                                airspaceClassCard = { airspaceClass, onToggle -> AirspaceClassCard(airspaceClass, onToggle) }
                            )
                        }
                    }
                }
            }
        }

        // Error handling
        errorMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                errorMessage = null
            }
        }

        // Delete dialog
        showDeleteDialog?.let { (fileName, tabType) ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete File") },
                text = { Text("Are you sure you want to delete $fileName?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (tabType) {
                                "waypoints" -> {
                                    selectedWaypointFiles.removeAll { uri ->
                                        uri.lastPathSegment?.substringAfterLast("/") == fileName
                                    }
                                    waypointCheckedStates.value = waypointCheckedStates.value.toMutableMap().apply {
                                        remove(fileName)
                                    }
                                    saveWaypointFiles(context, selectedWaypointFiles, waypointCheckedStates.value)
                                }
                                "airspace" -> {
                                    selectedAirspaceFiles.removeAll { uri ->
                                        uri.lastPathSegment?.substringAfterLast("/") == fileName
                                    }
                                    airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                        remove(fileName)
                                    }
                                    saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
                                }
                            }

                            try {
                                val file = File(context.filesDir, fileName)
                                if (file.exists()) {
                                    file.delete()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting file: ${e.message}")
                            }

                            showDeleteDialog = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}