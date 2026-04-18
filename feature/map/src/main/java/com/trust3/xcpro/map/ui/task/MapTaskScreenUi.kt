package com.trust3.xcpro.map.ui.task

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.map.MapTaskScreenManager
import com.trust3.xcpro.map.model.MapLocationUiModel
import com.trust3.xcpro.tasks.TaskFlightSurfaceUiState
import com.trust3.xcpro.tasks.TaskMinimizedIndicator
import com.trust3.xcpro.tasks.TaskSheetViewModel
import com.trust3.xcpro.tasks.TaskTopDropdownPanel

/**
 * Compose wrappers for AAT / task UI surfaces.
 * Lives under map/ui/task to keep presentation separate per CODING_POLICY.
 */
object MapTaskScreenUi {

    internal object Tags {
        const val TASK_TOP_PANEL = "map_task_top_panel"
        const val TASK_MINIMIZED_INDICATOR = "map_task_collapsed_bar"
    }

    @Composable
    fun TaskTopPanel(
        taskScreenManager: MapTaskScreenManager,
        allWaypoints: List<WaypointData>,
        unitsPreferences: UnitsPreferences = UnitsPreferences(),
        currentQNH: String,
        modifier: Modifier = Modifier,
        panelContent: (@Composable BoxScope.() -> Unit)? = null
    ) {
        val panelState by taskScreenManager.taskPanelState.collectAsStateWithLifecycle()
        val isExpanded =
            panelState == MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL ||
                panelState == MapTaskScreenManager.TaskPanelState.EXPANDED_FULL

        if (isExpanded) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .zIndex(70f)
                    .testTag(Tags.TASK_TOP_PANEL)
            ) {
                if (panelContent != null) {
                    panelContent()
                } else {
                    TaskTopDropdownPanel(
                        panelState = panelState,
                        mapLibreMap = taskScreenManager.mapState.mapLibreMap,
                        allWaypoints = allWaypoints,
                        unitsPreferences = unitsPreferences,
                        currentQNH = currentQNH,
                        onClearTask = taskScreenManager::handleTaskClear,
                        onSaveTask = taskScreenManager::handleTaskSave,
                        onDismiss = taskScreenManager::hideTaskPanel,
                        onStateChange = taskScreenManager::setPanelState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }

    @Composable
    fun TaskMinimizedIndicatorOverlay(
        taskScreenManager: MapTaskScreenManager,
        unitsPreferences: UnitsPreferences = UnitsPreferences(),
        modifier: Modifier = Modifier,
        indicatorContent: (@Composable BoxScope.() -> Unit)? = null,
        showBottomSheetOverride: Boolean? = null,
        currentTaskOverride: com.trust3.xcpro.tasks.core.Task? = null,
        activeLegOverride: Int? = null,
        taskFlightSurfaceUiState: TaskFlightSurfaceUiState? = null,
        taskViewModel: TaskSheetViewModel? = null,
        currentLocation: MapLocationUiModel? = null
    ) {
        val panelState by taskScreenManager.taskPanelState.collectAsStateWithLifecycle()
        val isPanelExpanded =
            panelState == MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL ||
                panelState == MapTaskScreenManager.TaskPanelState.EXPANDED_FULL

        val resolvedTaskViewModel = taskViewModel ?: if (currentTaskOverride != null) null else hiltViewModel()
        val taskUiState = resolvedTaskViewModel?.uiState?.collectAsStateWithLifecycle()?.value

        val currentTask = currentTaskOverride ?: taskFlightSurfaceUiState?.task ?: taskUiState?.task ?: return
        val activeLeg = activeLegOverride ?: taskFlightSurfaceUiState?.displayLegIndex ?: taskUiState?.stats?.activeIndex ?: 0
        val onSetActiveLeg: (Int) -> Unit = { legIndex ->
            resolvedTaskViewModel?.onSetActiveLeg(legIndex)
        }
        val isExpanded = showBottomSheetOverride ?: isPanelExpanded
        if (!isExpanded && currentTask.waypoints.isNotEmpty()) {
            val currentGpsLocation =
                if (indicatorContent == null) {
                    currentLocation?.let { location ->
                        Pair(location.latitude, location.longitude)
                    }
                } else {
                    null
                }

            Box(
                modifier = modifier
                    .wrapContentWidth(Alignment.CenterHorizontally, unbounded = false)
                    .wrapContentHeight(Alignment.Top, unbounded = false)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .zIndex(71f)
                    .testTag(Tags.TASK_MINIMIZED_INDICATOR)
            ) {
                if (indicatorContent != null) {
                    indicatorContent()
                } else {
                    TaskMinimizedIndicator(
                        task = currentTask,
                        activeLegIndex = activeLeg,
                        onSetActiveLeg = onSetActiveLeg,
                        distanceToWaypointMeters = resolvedTaskViewModel?.let { vm ->
                            { lat, lon -> vm.distanceToWaypointMeters(activeLeg, lat, lon) }
                        },
                        unitsPreferences = unitsPreferences,
                        currentGPSLocation = currentGpsLocation,
                        onClick = taskScreenManager::handleMinimizedIndicatorClick,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun AllTaskScreenComponents(
        taskScreenManager: MapTaskScreenManager,
        allWaypoints: List<WaypointData>,
        unitsPreferences: UnitsPreferences = UnitsPreferences(),
        currentQNH: String,
        modifier: Modifier = Modifier,
        taskFlightSurfaceUiState: TaskFlightSurfaceUiState,
        currentLocation: MapLocationUiModel? = null
    ) {
        TaskTopPanel(
            taskScreenManager = taskScreenManager,
            allWaypoints = allWaypoints,
            unitsPreferences = unitsPreferences,
            currentQNH = currentQNH,
            modifier = modifier
        )

        TaskMinimizedIndicatorOverlay(
            taskScreenManager = taskScreenManager,
            unitsPreferences = unitsPreferences,
            modifier = modifier,
            taskFlightSurfaceUiState = taskFlightSurfaceUiState,
            currentLocation = currentLocation
        )
    }
}
