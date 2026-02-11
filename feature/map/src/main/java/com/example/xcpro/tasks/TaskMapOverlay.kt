package com.example.xcpro.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.maps.MapLibreMap

/**
 * Generic Task Map Overlay - No task-specific imports to prevent contamination
 * Delegates to specific task overlays based on task type
 */
@Composable
fun TaskMapOverlay(
    taskManager: TaskManagerCoordinator,
    mapLibreMap: MapLibreMap?,
    taskViewModel: TaskSheetViewModel? = null,
    modifier: Modifier = Modifier
) {
    val resolvedViewModel = taskViewModel ?: hiltViewModel()
    val uiState by resolvedViewModel.uiState.collectAsStateWithLifecycle()
    val currentTask = uiState.task
    val currentTaskType = uiState.taskType
    val activeLeg = uiState.stats.activeIndex

    // Plot task on map when task changes, task type switches, OR map becomes available
    LaunchedEffect(currentTask, currentTaskType, activeLeg, mapLibreMap) {

        // CRITICAL FIX: Clear ALL task visuals before plotting new ones
        // This ensures complete separation and prevents color contamination
        if (mapLibreMap != null) {

            // FORCE clear ALL Racing visuals - even when switching to AAT
            TaskMapRenderRouter.clearAllTaskVisuals(taskManager, mapLibreMap)

            // Additional safety: Clear any remaining task-related layers that might be orphaned
            mapLibreMap.getStyle { style ->
                val allTaskLayers = listOf(
                    // Racing layers (blue colors)
                    "racing-waypoints", "racing-turnpoint-areas-fill", "racing-turnpoint-areas-border", "racing-course-line",
                    // AAT layers (green colors)
                    "aat-waypoints", "aat-areas-layer", "aat-borders-layer", "aat-lines-layer", "aat-task-line", "aat-target-points-layer",
                    // Generic task layers
                    "task-waypoints", "task-areas", "task-line"
                )

                val allTaskSources = listOf(
                    // Racing sources
                    "racing-waypoints", "racing-turnpoint-areas", "racing-course-line",
                    // AAT sources
                    "aat-waypoints", "aat-areas", "aat-lines", "aat-task-line", "aat-target-points",
                    // Generic sources
                    "task-waypoints", "task-areas", "task-line"
                )

                // Remove all layers first (MapLibre requirement)
                allTaskLayers.forEach { layerId ->
                    try {
                        if (style.getLayer(layerId) != null) {
                            style.removeLayer(layerId)
                        }
                    } catch (e: Exception) { /* Layer doesn't exist */ }
                }

                // Then remove all sources
                allTaskSources.forEach { sourceId ->
                    try {
                        if (style.getSource(sourceId) != null) {
                            style.removeSource(sourceId)
                        }
                    } catch (e: Exception) { /* Source doesn't exist */ }
                }
            }

        }

        if (currentTask.waypoints.isNotEmpty()) {
            TaskMapRenderRouter.plotCurrentTask(taskManager, mapLibreMap)
        }
    }

    Box(modifier = modifier) {
        // Generic task visualization UI could go here
        // (distance display, task info, etc.)
        // Specific task validation should be handled by dedicated overlays
    }

    // NOTE: MapLibre 10+ location listeners differ; wiring is left to the host to avoid SDK mismatch.
    // Provide a hook on the taskManager to report proximity from GNSS elsewhere.
}
