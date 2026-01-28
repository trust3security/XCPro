package com.example.ui1.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.saveWaypointFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import java.io.File

private const val TAG = "TaskFileManagement"

/**
 * Task File Management Module
 *
 * Handles airspace and waypoint file picker logic.
 * Extracted from Task.kt for better modularity.
 */

@Composable
fun rememberAirspaceFilePicker(
    context: Context,
    scope: CoroutineScope,
    mapLibreMap: MapLibreMap?,
    selectedAirspaceFiles: MutableList<Uri>,
    airspaceCheckedStates: MutableState<MutableMap<String, Boolean>>,
    selectedClasses: MutableState<MutableMap<String, Boolean>>,
    onError: (String?) -> Unit
): ManagedActivityResultLauncher<String, Uri?> {
    val airspaceRepository = remember(context) { AirspaceRepository(context) }
    return rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val fileName = copyFileToInternalStorage(context, it)
                    if (!fileName.endsWith(".txt", ignoreCase = true)) {
                        onError("Only .txt files are supported for airspace files.")
                        Log.e(TAG, "Selected file is not a .txt file: $fileName")
                        return@launch
                    }
                    if (!selectedAirspaceFiles.any { file ->
                            file.lastPathSegment?.substringAfterLast("/") == fileName
                        }) {
                        selectedAirspaceFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                        airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                            put(fileName, false)
                        }
                        airspaceRepository.saveAirspaceFiles(selectedAirspaceFiles, airspaceCheckedStates.value)
                        val newClasses = airspaceRepository.parseClasses(selectedAirspaceFiles)
                        selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                            newClasses.forEach { put(it, it == "R" || it == "D") }
                        }
                        airspaceRepository.saveSelectedClasses(selectedClasses.value)
                        loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                        onError(null)
                        Log.d(TAG, " Airspace file added: $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying file: ${e.message}")
                    onError("Error copying file: ${e.message}")
                }
            }
        }
    }
}

@Composable
fun rememberWaypointFilePicker(
    context: Context,
    scope: CoroutineScope,
    mapLibreMap: MapLibreMap?,
    selectedWaypointFiles: MutableList<Uri>,
    waypointCheckedStates: MutableState<MutableMap<String, Boolean>>,
    onError: (String?) -> Unit
): ManagedActivityResultLauncher<String, Uri?> {
    return rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val fileName = copyFileToInternalStorage(context, it)
                    if (!fileName.endsWith(".cup", ignoreCase = true)) {
                        onError("Only .cup files are supported for waypoint files.")
                        Log.e(TAG, "Selected file is not a .cup file: $fileName")
                        return@launch
                    }
                    if (!selectedWaypointFiles.any { file ->
                            file.lastPathSegment?.substringAfterLast("/") == fileName
                        }) {
                        selectedWaypointFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                        waypointCheckedStates.value = waypointCheckedStates.value.toMutableMap().apply {
                            put(fileName, true) // Default to checked
                        }
                        saveWaypointFiles(context, selectedWaypointFiles, waypointCheckedStates.value)
                        onError(null)
                        loadAndApplyWaypoints(
                            context,
                            mapLibreMap,
                            selectedWaypointFiles,
                            waypointCheckedStates.value
                        )
                        Log.d(TAG, " Waypoint file added and saved: $fileName")
                    } else {
                        onError("File already selected: $fileName")
                        Log.d(TAG, "Duplicate waypoint file ignored: $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying waypoint file: ${e.message}")
                    onError("Error copying file: ${e.message}")
                }
            }
        }
    }
}
