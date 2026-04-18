package com.trust3.xcpro.screens.flightdata

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import com.example.ui1.screens.FileItem
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.trust3.xcpro.flightdata.WaypointsViewModel
import com.trust3.xcpro.map.ui.documentRefForUri

private const val TAG = "FlightWaypointsTab"

/**
 * Flight Data Waypoints Tab - Main Coordinator
 *
 * Refactored from 1,116 lines to focused coordinator pattern.
 * Delegates to specialized modules:
 * - WaypointHelpers.kt - Utility functions
 * - WaypointFileManagement.kt - File UI components
 * - HomeWaypointSelector.kt - Home waypoint selection
 * - WaypointSearchDisplay.kt - Search and waypoint list
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightDataWaypointsTab(
    onShowDeleteDialog: (String) -> Unit,
    onErrorMessage: (String) -> Unit,
    autoFocusHome: Boolean = false,
    addFileButton: @Composable (String, () -> Unit) -> Unit,
    sectionHeader: @Composable (String, String) -> Unit,
    fileItemCard: @Composable (FileItem, String, (String) -> Unit, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val viewModel: WaypointsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var triggerHomeFocus by remember { mutableStateOf(false) }

    // Auto-focus home waypoint when parameter is true
    LaunchedEffect(autoFocusHome) {
        if (autoFocusHome) {
            Log.d(TAG, " AUTO-FOCUS HOME PARAMETER RECEIVED: $autoFocusHome")
            kotlinx.coroutines.delay(300)
            triggerHomeFocus = true
            Log.d(TAG, " TRIGGER HOME FOCUS SET TO TRUE")
        }
    }

    // Load waypoints when files or states change
    LaunchedEffect(uiState.isLoading) {
        isLoading = uiState.isLoading
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { onErrorMessage(it) }
    }

    // File picker launcher
    val waypointFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importFile(documentRefForUri(context, it))
        }
    }

    // Filter waypoints based on search query
    val filteredWaypoints = remember(uiState.allWaypoints, searchQuery) {
        filterWaypoints(uiState.allWaypoints, searchQuery)
    }

    // State to control scrolling
    val listState = rememberLazyListState()
    var homeWaypointItemIndex by remember { mutableStateOf(-1) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Add File Button
        item {
            addFileButton("waypoints") {
                Log.d(TAG, " Opening waypoint file picker...")
                waypointFilePickerLauncher.launch("application/octet-stream")
            }
        }

        when {
            // Scenario 1: No files added at all
            uiState.files.isEmpty() -> {
                item { EmptyWaypointFilesCard() }
            }

            // Scenario 2: Files added but ALL are disabled
            uiState.fileItems.none { it.enabled } -> {
                item {
                    sectionHeader("Waypoint Files", "")
                }

                items(uiState.fileItems) { file ->
                        fileItemCard(
                            file,
                            "waypoints",
                            { fileName ->
                                Log.d(TAG, "Toggling waypoint file: $fileName")
                                val newValue = !(uiState.checkedStates[fileName] ?: false)
                                viewModel.toggleFile(fileName)
                                val statusLabel = if (newValue) "enabled" else "disabled"
                                Log.d(TAG, "Waypoint file $fileName is now ${statusLabel}")
                            },
                        { fileName ->
                            Log.d(TAG, "Delete requested for waypoint file: $fileName")
                            onShowDeleteDialog(fileName)
                        }
                    )
                }

                item { AllFilesDisabledCard() }
            }

            // Scenario 3: Normal operation - files enabled
            else -> {
                item {
                    sectionHeader("Waypoint Files", "${uiState.fileItems.count { it.enabled }} active")
                }

                items(uiState.fileItems) { file ->
                    fileItemCard(
                        file,
                        "waypoints",
                        { fileName ->
                            Log.d(TAG, "Toggling waypoint file: $fileName")
                            viewModel.toggleFile(fileName)
                        },
                        { fileName -> onShowDeleteDialog(fileName) }
                    )
                }
                if (uiState.allWaypoints.isNotEmpty()) {
                    item {
                        LaunchedEffect(Unit) {
                            homeWaypointItemIndex = 1 + 1 + uiState.fileItems.size
                        }

                        HomeWaypointSelector(
                            availableWaypoints = uiState.allWaypoints,
                            autoFocus = triggerHomeFocus,
                            onAutoFocusConsumed = { triggerHomeFocus = false },
                            onSearchFocused = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        index = homeWaypointItemIndex,
                                        scrollOffset = -100
                                    )
                                }
                            }
                        )
                    }
                }

                // Waypoint search and list
                if (isLoading) {
                    item { LoadingCard() }
                } else if (uiState.allWaypoints.isNotEmpty()) {
                    val waypointSearchIndex = 1 + 1 + uiState.fileItems.size + 1

                    item {
                        WaypointSearchHeader(
                            totalWaypoints = uiState.allWaypoints.size,
                            filteredWaypoints = filteredWaypoints.size,
                            searchQuery = searchQuery,
                            onSearchQueryChanged = { searchQuery = it },
                            onSearchFocused = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        index = waypointSearchIndex,
                                        scrollOffset = -100
                                    )
                                }
                            }
                        )
                    }

                    items(filteredWaypoints) { waypoint ->
                        WaypointCard(waypoint = waypoint)
                    }
                }
            }
        }
    }
}


