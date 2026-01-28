package com.example.ui1.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.dfcards.CardCategory
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.ui1.screens.flightmgmt.FlightDataAirspaceTab
import com.example.ui1.screens.flightmgmt.FlightDataClassesTab
import com.example.ui1.screens.flightmgmt.FlightDataScreensTab
import com.example.ui1.screens.flightmgmt.buildAirspaceFileItems
import com.example.ui1.screens.flightmgmt.refreshAvailableAirspaceClasses
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.loadWaypointFiles
import com.example.xcpro.saveAirspaceFiles
import com.example.xcpro.saveWaypointFiles
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.profiles.UserProfile
import com.example.xcpro.screens.flightdata.FlightDataWaypointsTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

private const val TAG = "FlightMgmt"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FlightMgmt(
    navController: NavHostController,
    drawerState: DrawerState,
    initialTab: String = "screens",
    autoFocusHome: Boolean = false,
    activeProfile: UserProfile? = null,
    flightDataManager: FlightDataManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapEntry = remember(navController) { navController.getBackStackEntry("map") }
    val mapViewModel: MapScreenViewModel = hiltViewModel(mapEntry)
    val cardPreferences = mapViewModel.cardPreferences
    val flightViewModel: FlightDataViewModel = viewModel(mapEntry)
    val airspaceRepository = remember(context) { AirspaceRepository(context) }

    LaunchedEffect(cardPreferences) {
        flightViewModel.initializeCardPreferences(cardPreferences)
    }

    LaunchedEffect(flightDataManager) {
        flightDataManager.cardFlightDataFlow
            .filterNotNull()
            .collectLatest { displaySample ->
                flightViewModel.updateCardsWithLiveData(displaySample)
            }
    }

    val sharedPrefs = remember {
        context.getSharedPreferences("FlightMgmtPrefs", Context.MODE_PRIVATE)
    }

    var activeTab by remember { mutableStateOf(initialTab) }
    var selectedCategory by remember { mutableStateOf(CardCategory.ESSENTIAL) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<FlightTemplate?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val liveFlightData = flightDataManager.liveFlightData

    val selectedWaypointFiles = remember { mutableStateListOf<Uri>() }
    val waypointCheckedStates = remember { mutableStateMapOf<String, Boolean>() }
    val selectedAirspaceFiles = remember { mutableStateListOf<Uri>() }
    val airspaceCheckedStates = remember { mutableStateMapOf<String, Boolean>() }
    val airspaceClassStates = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        val (waypointFiles, waypointChecks) = loadWaypointFiles(context)
        selectedWaypointFiles.clear()
        selectedWaypointFiles.addAll(waypointFiles)
        waypointCheckedStates.clear()
        waypointCheckedStates.putAll(waypointChecks)

        val (airspaceFiles, airspaceChecks) = airspaceRepository.loadAirspaceFiles()
        selectedAirspaceFiles.clear()
        selectedAirspaceFiles.addAll(airspaceFiles)
        airspaceCheckedStates.clear()
        airspaceCheckedStates.putAll(airspaceChecks)

        val savedClassStates = airspaceRepository.loadSelectedClasses() ?: emptyMap()
        airspaceClassStates.clear()
        airspaceClassStates.putAll(savedClassStates)
    }

    val waypointFileItems = selectedWaypointFiles.map { uri ->
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        FileItem(
            name = fileName,
            enabled = waypointCheckedStates[fileName] ?: false,
            count = 1250,
            status = if (waypointCheckedStates[fileName] == true) "Loaded" else "Disabled",
            uri = uri
        )
    }
    val airspaceFileItems by produceState(
        initialValue = emptyList<FileItem>(),
        selectedAirspaceFiles.toList(),
        airspaceCheckedStates.toMap()
    ) {
        value = buildAirspaceFileItems(
            context,
            selectedAirspaceFiles.toList(),
            airspaceCheckedStates.toMap()
        )
    }

    val currentFlightMode by flightViewModel.currentFlightMode.collectAsStateWithLifecycle()
    val profileModeVisibilities by flightViewModel.profileModeVisibilities.collectAsStateWithLifecycle()
    val flightModeVisibilities = remember(profileModeVisibilities, activeProfile?.id) {
        flightViewModel.flightModeVisibilitiesFor(activeProfile?.id)
    }
    val visibleFlightModesCount = flightModeVisibilities.values.count { it }.coerceAtLeast(1)

    LaunchedEffect(activeProfile?.id) {
        flightViewModel.setActiveProfile(activeProfile?.id)
        activeProfile?.id?.let { profileId ->
            val storedMode = sharedPrefs.getString("profile_${profileId}_last_flight_mode", null)
            val restoredMode = storedMode?.let { runCatching { FlightModeSelection.valueOf(it) }.getOrNull() }
                ?: FlightModeSelection.CRUISE
            flightViewModel.setFlightMode(restoredMode)
        }
    }

    LaunchedEffect(activeTab) {
        sharedPrefs.edit()
            .putString("last_active_tab", activeTab)
            .apply()
    }

    if (showTemplateEditor && editingTemplate != null) {
        TemplateEditorModal(
            selectedCardIds = editingTemplate!!.cardIds.toSet(),
            existingTemplate = editingTemplate,
            liveFlightData = liveFlightData,
            onSaveTemplate = { name, cards ->
                scope.launch {
                    if (editingTemplate == null) {
                        flightViewModel.createTemplate(name, cards)
                    } else {
                        flightViewModel.updateTemplate(editingTemplate!!.id, name, cards)
                    }
                    showTemplateEditor = false
                    editingTemplate = null
                }
            },
            onDismiss = {
                showTemplateEditor = false
                editingTemplate = null
            }
        )
    }

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
                screensCount = visibleFlightModesCount,
                onTabSelected = { activeTab = it }
            )

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                        .togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                        )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { tab ->
                when (tab) {
                    "screens" -> {
                        FlightDataScreensTab(
                            activeProfile = activeProfile,
                            selectedFlightMode = currentFlightMode,
                            onFlightModeSelected = { mode ->
                                flightViewModel.setFlightMode(mode)
                                activeProfile?.let { profile ->
                                    sharedPrefs.edit()
                                        .putString("profile_${profile.id}_last_flight_mode", mode.name)
                                        .apply()
                                }
                            },
                            selectedCategory = selectedCategory,
                            onCategorySelected = { selectedCategory = it },
                            flightViewModel = flightViewModel,
                            onEditTemplate = { template ->
                                editingTemplate = template
                                showTemplateEditor = true
                            },
                            onDeleteTemplate = { template ->
                                errorMessage = "\"${template.name}\" deleted"
                            },
                            liveFlightData = liveFlightData
                        )
                    }

                    "waypoints" -> {
                        FlightDataWaypointsTab(
                            selectedWaypointFiles = selectedWaypointFiles,
                            waypointCheckedStates = waypointCheckedStates,
                            onShowDeleteDialog = { fileName ->
                                showDeleteDialog = fileName to "waypoints"
                            },
                            onErrorMessage = { message -> errorMessage = message },
                            scope = scope,
                            autoFocusHome = autoFocusHome,
                            addFileButton = { type, onClick -> AddFileButton(type, onClick) },
                            sectionHeader = { title, count -> SectionHeader(title, count) },
                            fileItemCard = { file, type, onToggle, onDelete ->
                                FileItemCard(file, type, onToggle, onDelete)
                            }
                        )
                    }

                    "airspace" -> {
                        FlightDataAirspaceTab(
                            selectedAirspaceFiles = selectedAirspaceFiles,
                            airspaceCheckedStates = airspaceCheckedStates,
                            airspaceClassStates = airspaceClassStates,
                            onShowDeleteDialog = { fileName ->
                                showDeleteDialog = fileName to "airspace"
                            },
                            onErrorMessage = { message -> errorMessage = message },
                            scope = scope,
                            addFileButton = { type, onClick -> AddFileButton(type, onClick) },
                            sectionHeader = { title, count -> SectionHeader(title, count) },
                            fileItemCard = { file, type, onToggle, onDelete ->
                                FileItemCard(file, type, onToggle, onDelete)
                            },
                            airspaceClassCard = { airspaceClass, onToggle ->
                                AirspaceClassCard(airspaceClass, onToggle)
                            }
                        )
                    }

                    "classes" -> {
                        FlightDataClassesTab(
                            airspaceClassItems = listOf(),
                            selectedClasses = airspaceClassStates,
                            onSelectedClassesChanged = { },
                            sectionHeader = { title, count -> SectionHeader(title, count) },
                            airspaceClassCard = { airspaceClass, onToggle ->
                                AirspaceClassCard(airspaceClass, onToggle)
                            }
                        )
                    }
                }
            }
        }
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            delay(3000)
            errorMessage = null
        }
    }

    showDeleteDialog?.let { (fileName, type) ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete File") },
            text = { Text("Are you sure you want to delete $fileName?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (type) {
                            "waypoints" -> {
                                selectedWaypointFiles.removeAll { uri ->
                                    uri.lastPathSegment?.substringAfterLast("/") == fileName
                                }
                                waypointCheckedStates.remove(fileName)
                                scope.launch {
                                    saveWaypointFiles(
                                        context,
                                        selectedWaypointFiles,
                                        waypointCheckedStates.toMap()
                                    )
                                }
                            }

                            "airspace" -> {
                                selectedAirspaceFiles.removeAll { uri ->
                                    uri.lastPathSegment?.substringAfterLast("/") == fileName
                                }
                                airspaceCheckedStates.remove(fileName)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        saveAirspaceFiles(
                                            context,
                                            selectedAirspaceFiles,
                                            airspaceCheckedStates.toMap()
                                        )
                                    }
                                    refreshAvailableAirspaceClasses(
                                        context,
                                        selectedAirspaceFiles,
                                        airspaceCheckedStates,
                                        airspaceClassStates,
                                        scope
                                    )
                                }
                            }
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
