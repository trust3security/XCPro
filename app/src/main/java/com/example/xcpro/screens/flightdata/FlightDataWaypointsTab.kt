package com.example.xcpro.screens.flightdata

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xcpro.WaypointData
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.saveWaypointFiles
import com.example.ui1.screens.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

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
    selectedWaypointFiles: SnapshotStateList<Uri>,
    waypointCheckedStates: SnapshotStateMap<String, Boolean>,
    onShowDeleteDialog: (String) -> Unit,
    onErrorMessage: (String) -> Unit,
    scope: CoroutineScope,
    autoFocusHome: Boolean = false,
    addFileButton: @Composable (String, () -> Unit) -> Unit,
    sectionHeader: @Composable (String, String) -> Unit,
    fileItemCard: @Composable (FileItem, String, (String) -> Unit, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var allWaypoints by remember { mutableStateOf<List<WaypointData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var triggerHomeFocus by remember { mutableStateOf(false) }

    // Auto-focus home waypoint when parameter is true
    LaunchedEffect(autoFocusHome) {
        if (autoFocusHome) {
            Log.d(TAG, "🎯 AUTO-FOCUS HOME PARAMETER RECEIVED: $autoFocusHome")
            kotlinx.coroutines.delay(300)
            triggerHomeFocus = true
            Log.d(TAG, "🎯 TRIGGER HOME FOCUS SET TO TRUE")
        }
    }

    // Load waypoints when files or states change
    LaunchedEffect(selectedWaypointFiles.toList(), waypointCheckedStates.toMap()) {
        isLoading = true
        try {
            allWaypoints = getAllWaypoints(context, selectedWaypointFiles, waypointCheckedStates)
            Log.d(TAG, "Loaded ${allWaypoints.size} waypoints from enabled files")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading waypoints: ${e.message}")
            onErrorMessage("Error loading waypoints: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // File picker launcher
    val waypointFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val fileName = copyFileToInternalStorage(context, it)
                    if (!fileName.endsWith(".cup", ignoreCase = true)) {
                        onErrorMessage("Only .cup files are supported for waypoint files.")
                        return@launch
                    }
                    if (!selectedWaypointFiles.any { file ->
                            file.lastPathSegment?.substringAfterLast("/") == fileName
                        }) {
                        selectedWaypointFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                        waypointCheckedStates[fileName] = true
                        saveWaypointFiles(
                            context,
                            selectedWaypointFiles,
                            waypointCheckedStates.toMap()
                        )
                        Log.d(TAG, "✅ Added waypoint file: $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying waypoint file: ${e.message}")
                    onErrorMessage("Error copying file: ${e.message}")
                }
            }
        }
    }

    // Create file items with dynamic counts
    val waypointFileItems = selectedWaypointFiles.map { uri ->
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        val waypointCount = getWaypointCount(context, uri)
        FileItem(
            name = fileName,
            enabled = waypointCheckedStates[fileName] ?: false,
            count = waypointCount,
            status = if (waypointCheckedStates[fileName] == true) "Loaded" else "Disabled",
            uri = uri
        )
    }

    // Filter waypoints based on search query
    val filteredWaypoints = remember(allWaypoints, searchQuery) {
        filterWaypoints(allWaypoints, searchQuery)
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
                Log.d(TAG, "📁 Opening waypoint file picker...")
                waypointFilePickerLauncher.launch("application/octet-stream")
            }
        }

        when {
            // Scenario 1: No files added at all
            selectedWaypointFiles.isEmpty() -> {
                item { EmptyWaypointFilesCard() }
            }

            // Scenario 2: Files added but ALL are disabled
            waypointFileItems.none { it.enabled } -> {
                item {
                    sectionHeader("Waypoint Files", "")
                }

                items(waypointFileItems) { file ->
                    fileItemCard(
                        file,
                        "waypoints",
                        { fileName ->
                            Log.d(TAG, "Toggling waypoint file: $fileName")
                            val newValue = !(waypointCheckedStates[fileName] ?: false)
                            waypointCheckedStates[fileName] = newValue
                            saveWaypointFiles(
                                context,
                                selectedWaypointFiles,
                                waypointCheckedStates.toMap()
                            )
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
                    sectionHeader("Waypoint Files", "${waypointFileItems.count { it.enabled }} active")
                }

                items(waypointFileItems) { file ->
                    fileItemCard(
                        file,
                        "waypoints",
                        { fileName ->
                            Log.d(TAG, "Toggling waypoint file: $fileName")
                            val newValue = !(waypointCheckedStates[fileName] ?: false)
                            waypointCheckedStates[fileName] = newValue
                            saveWaypointFiles(
                                context,
                                selectedWaypointFiles,
                                waypointCheckedStates.toMap()
                            )
                        },
                        { fileName -> onShowDeleteDialog(fileName) }
                    )
                }
                if (allWaypoints.isNotEmpty()) {
                    item {
                        LaunchedEffect(Unit) {
                            homeWaypointItemIndex = 1 + 1 + waypointFileItems.size
                        }

                        HomeWaypointSelector(
                            availableWaypoints = allWaypoints,
                            context = context,
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
                } else if (allWaypoints.isNotEmpty()) {
                    val waypointSearchIndex = 1 + 1 + waypointFileItems.size + 1

                    item {
                        WaypointSearchHeader(
                            totalWaypoints = allWaypoints.size,
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


