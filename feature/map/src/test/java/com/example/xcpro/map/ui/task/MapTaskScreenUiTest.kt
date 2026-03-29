package com.example.xcpro.map.ui.task

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTasksUseCase
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.tasks.TaskFlightSurfaceUiState
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapTaskScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun taskTopPanel_showsContainerWhenVisible() {
        val taskScreenManager = createManager(sampleTask(withWaypoints = false))
        taskScreenManager.showTaskBottomSheet()

        composeTestRule.setContent {
            MapTaskScreenUi.TaskTopPanel(
                taskScreenManager = taskScreenManager,
                allWaypoints = emptyList(),
                currentQNH = "1013",
                panelContent = { Box(Modifier) }
            )
        }

        composeTestRule
            .onNodeWithTag(MapTaskScreenUi.Tags.TASK_TOP_PANEL)
            .assertIsDisplayed()
    }

    @Test
    fun taskTopPanel_hidesContainerWhenNotVisible() {
        val taskScreenManager = createManager(sampleTask(withWaypoints = false))

        composeTestRule.setContent {
            MapTaskScreenUi.TaskTopPanel(
                taskScreenManager = taskScreenManager,
                allWaypoints = emptyList(),
                currentQNH = "1013",
                panelContent = { Box(Modifier) }
            )
        }

        composeTestRule
            .onAllNodesWithTag(MapTaskScreenUi.Tags.TASK_TOP_PANEL)
            .assertCountEquals(0)
    }

    @Test
    fun taskMinimizedIndicator_showsWhenTaskActiveAndSheetHidden() {
        val task = sampleTask(withWaypoints = true)
        val taskScreenManager = createManager(task).apply {
            hideTaskBottomSheet()
        }

        composeTestRule.setContent {
            MapTaskScreenUi.TaskMinimizedIndicatorOverlay(
                taskScreenManager = taskScreenManager,
                indicatorContent = { Box(Modifier) },
                currentTaskOverride = task,
                activeLegOverride = 0,
                showBottomSheetOverride = false
            )
        }

        composeTestRule
            .onAllNodesWithTag(MapTaskScreenUi.Tags.TASK_MINIMIZED_INDICATOR)
            .assertCountEquals(1)
    }

    @Test
    fun taskMinimizedIndicator_hidesWhenNoTaskWaypoints() {
        val task = sampleTask(withWaypoints = false)
        val taskScreenManager = createManager(task).apply {
            hideTaskBottomSheet()
        }

        composeTestRule.setContent {
            MapTaskScreenUi.TaskMinimizedIndicatorOverlay(
                taskScreenManager = taskScreenManager,
                indicatorContent = { Box(Modifier) },
                currentTaskOverride = task,
                activeLegOverride = 0,
                showBottomSheetOverride = false
            )
        }

        composeTestRule
            .onAllNodesWithTag(MapTaskScreenUi.Tags.TASK_MINIMIZED_INDICATOR)
            .assertCountEquals(0)
    }

    @Test
    fun taskMinimizedIndicator_usesFlightSurfaceLegForRacingDisplay() {
        val task = sampleTaskForFlightSurface()
        val taskScreenManager = createManager(task).apply {
            hideTaskBottomSheet()
        }

        composeTestRule.setContent {
            MapTaskScreenUi.TaskMinimizedIndicatorOverlay(
                taskScreenManager = taskScreenManager,
                currentTaskOverride = task,
                taskFlightSurfaceUiState = TaskFlightSurfaceUiState(
                    task = task,
                    taskType = TaskType.RACING,
                    displayLegIndex = 1
                ),
                showBottomSheetOverride = false
            )
        }

        composeTestRule
            .onNodeWithText("Turn One")
            .assertIsDisplayed()
    }

    private fun createManager(task: Task): MapTaskScreenManager {
        val tasksUseCase: MapTasksUseCase = mock()
        whenever(tasksUseCase.currentTaskSnapshot()).thenReturn(task)
        whenever(tasksUseCase.currentWaypointCount()).thenReturn(task.waypoints.size)

        val mapState = MapScreenState()

        return MapTaskScreenManager(
            mapState = mapState,
            tasksUseCase = tasksUseCase,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    private fun sampleTask(withWaypoints: Boolean): Task {
        return if (withWaypoints) {
            Task(
                id = "task_with_points",
                waypoints = listOf(
                    TaskWaypoint(
                        id = "wp-1",
                        title = "Start",
                        subtitle = "Start Line",
                        lat = 40.0,
                        lon = -105.0,
                        role = WaypointRole.START
                    )
                )
            )
        } else {
            Task(id = "empty_task", waypoints = emptyList())
        }
    }

    private fun sampleTaskForFlightSurface(): Task = Task(
        id = "task_with_turns",
        waypoints = listOf(
            TaskWaypoint(
                id = "wp-start",
                title = "Start",
                subtitle = "Start Line",
                lat = 40.0,
                lon = -105.0,
                role = WaypointRole.START
            ),
            TaskWaypoint(
                id = "wp-turn-1",
                title = "Turn One",
                subtitle = "TP1",
                lat = 40.1,
                lon = -104.9,
                role = WaypointRole.TURNPOINT
            ),
            TaskWaypoint(
                id = "wp-finish",
                title = "Finish",
                subtitle = "Goal",
                lat = 40.2,
                lon = -104.8,
                role = WaypointRole.FINISH
            )
        )
    )
}
