package com.example.ui1.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.dfcards.CardCategory
import com.example.dfcards.FlightTemplate
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.ui1.screens.flightmgmt.FlightDataAirspaceTab
import com.example.ui1.screens.flightmgmt.FlightDataClassesTab
import com.example.ui1.screens.flightmgmt.FlightDataScreensTab
import com.trust3.xcpro.airspace.AirspaceViewModel
import com.trust3.xcpro.flightdata.WaypointsViewModel
import com.trust3.xcpro.flightdata.FlightMgmtPreferencesViewModel
import com.trust3.xcpro.map.FlightDataMgmtPort
import com.trust3.xcpro.map.MapScreenViewModel
import com.trust3.xcpro.profiles.ProfileIdResolver
import com.trust3.xcpro.profiles.UserProfile
import com.trust3.xcpro.screens.flightdata.FlightDataWaypointsTab
import com.trust3.xcpro.hawk.HAWK_VARIO_CARD_ID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FlightMgmt(
    navController: NavHostController,
    drawerState: DrawerState,
    initialTab: String = "screens",
    autoFocusHome: Boolean = false,
    activeProfile: UserProfile? = null,
    flightDataMgmtPort: FlightDataMgmtPort
) {
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val mapEntry = remember(navBackStackEntry) { navController.getBackStackEntry("map") }
    val mapViewModel: MapScreenViewModel = hiltViewModel(mapEntry)
    val flightViewModel: FlightDataViewModel = hiltViewModel(mapEntry)
    val waypointsViewModel: WaypointsViewModel = hiltViewModel()
    val airspaceViewModel: AirspaceViewModel = hiltViewModel()
    val prefsViewModel: FlightMgmtPreferencesViewModel = hiltViewModel()

    LaunchedEffect(flightViewModel, flightDataMgmtPort) {
        flightDataMgmtPort.bindCards(flightViewModel)
    }

    val activeTab by prefsViewModel.activeTab.collectAsStateWithLifecycle()
    val lastFlightMode by prefsViewModel.lastFlightMode.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf(CardCategory.ESSENTIAL) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<FlightTemplate?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val liveFlightData by flightDataMgmtPort.liveFlightDataFlow.collectAsStateWithLifecycle()
    val waypointUiState by waypointsViewModel.uiState.collectAsStateWithLifecycle()
    val airspaceUiState by airspaceViewModel.uiState.collectAsStateWithLifecycle()

    val currentFlightMode by flightViewModel.currentFlightMode.collectAsStateWithLifecycle()
    val profileModeVisibilities by flightViewModel.profileModeVisibilities.collectAsStateWithLifecycle()
    val flightModeVisibilities = remember(profileModeVisibilities, activeProfile?.id) {
        flightViewModel.flightModeVisibilitiesFor(activeProfile?.id)
    }
    val visibleFlightModesCount = flightModeVisibilities.values.count { it }.coerceAtLeast(1)
    val showHawkCard by mapViewModel.showHawkCard.collectAsStateWithLifecycle()
    val hiddenCardIds = remember(showHawkCard) {
        if (showHawkCard) emptySet() else setOf(HAWK_VARIO_CARD_ID)
    }

    LaunchedEffect(initialTab) {
        prefsViewModel.setActiveTab(initialTab)
    }

    LaunchedEffect(activeProfile?.id) {
        flightViewModel.setActiveProfile(activeProfile?.id)
        prefsViewModel.setProfileId(ProfileIdResolver.canonicalOrDefault(activeProfile?.id))
    }

    LaunchedEffect(activeProfile?.id, lastFlightMode) {
        flightViewModel.setFlightMode(lastFlightMode)
    }

    if (showTemplateEditor && editingTemplate != null) {
        TemplateEditorModal(
            selectedCardIds = editingTemplate!!.cardIds.toSet(),
            existingTemplate = editingTemplate,
            liveFlightData = liveFlightData,
            hiddenCardIds = hiddenCardIds,
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
                waypointCount = waypointUiState.fileItems.count { it.enabled },
                airspaceCount = airspaceUiState.fileItems.count { it.enabled },
                screensCount = visibleFlightModesCount,
                onTabSelected = { prefsViewModel.setActiveTab(it) }
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
                                prefsViewModel.setLastFlightMode(mode)
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
                            liveFlightData = liveFlightData,
                            hiddenCardIds = hiddenCardIds
                        )
                    }

                    "waypoints" -> {
                        FlightDataWaypointsTab(
                            onShowDeleteDialog = { fileName ->
                                showDeleteDialog = fileName to "waypoints"
                            },
                            onErrorMessage = { message -> errorMessage = message },
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
                            onShowDeleteDialog = { fileName ->
                                showDeleteDialog = fileName to "airspace"
                            },
                            onErrorMessage = { message -> errorMessage = message },
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
                                waypointsViewModel.deleteFile(fileName)
                            }

                            "airspace" -> {
                                airspaceViewModel.deleteFile(fileName)
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
