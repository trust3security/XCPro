package com.example.xcpro.tasks.aat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.AATTask

/**
 * AAT Task Map Overlay - Completely separate from racing tasks
 * Handles only AAT task visualization and validation
 */
@Composable
fun AATTaskMapOverlay(
    taskManager: TaskManagerCoordinator,
    mapLibreMap: MapLibreMap?,
    modifier: Modifier = Modifier
) {
    val currentTask = taskManager.currentTask

    // Plot AAT task on map when task changes
    LaunchedEffect(currentTask) {
        if (currentTask.waypoints.isNotEmpty()) {
            taskManager.plotOnMap(mapLibreMap)
        }
    }

    Box(modifier = modifier) {
        // AAT task visualization UI could go here
        // (area display, optimal path info, etc.)
    }
}
