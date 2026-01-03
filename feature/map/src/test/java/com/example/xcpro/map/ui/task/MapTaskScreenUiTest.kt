package com.example.xcpro.map.ui.task

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapTaskScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun taskBottomSheet_showsContainerWhenVisible() {
        val taskScreenManager = createManager(sampleTask(withWaypoints = false))
        taskScreenManager.showTaskBottomSheet()

        composeTestRule.setContent {
            MapTaskScreenUi.TaskBottomSheet(
                taskScreenManager = taskScreenManager,
                allWaypoints = emptyList(),
                currentQNH = "1013",
                bottomSheetContent = { Box(Modifier) }
            )
        }

        composeTestRule
            .onNodeWithTag(MapTaskScreenUi.Tags.TASK_BOTTOM_SHEET)
            .assertIsDisplayed()
    }

    @Test
    fun taskBottomSheet_hidesContainerWhenNotVisible() {
        val taskScreenManager = createManager(sampleTask(withWaypoints = false))

        composeTestRule.setContent {
            MapTaskScreenUi.TaskBottomSheet(
                taskScreenManager = taskScreenManager,
                allWaypoints = emptyList(),
                currentQNH = "1013",
                bottomSheetContent = { Box(Modifier) }
            )
        }

        composeTestRule
            .onAllNodesWithTag(MapTaskScreenUi.Tags.TASK_BOTTOM_SHEET)
            .assertCountEquals(0)
    }

    @Ignore("MapTaskScreenManager bottom-sheet state depends on MapLibre runtime; replace with deterministic fake before re-enabling")
    @Test
    fun taskMinimizedIndicator_showsWhenTaskActiveAndSheetHidden() {
        val taskScreenManager = createManager(sampleTask(withWaypoints = true)).apply {
            hideTaskBottomSheet()
        }

        composeTestRule.setContent {
            MapTaskScreenUi.TaskMinimizedIndicatorOverlay(
                taskScreenManager = taskScreenManager,
                indicatorContent = { Box(Modifier) }
            )
        }

        composeTestRule
            .onNodeWithTag(MapTaskScreenUi.Tags.TASK_MINIMIZED_INDICATOR)
            .assertIsDisplayed()
    }

    @Ignore("MapTaskScreenManager bottom-sheet state depends on MapLibre runtime; replace with deterministic fake before re-enabling")
    @Test
    fun taskMinimizedIndicator_hidesWhenNoTaskWaypoints() {
        val taskScreenManager = createManager(sampleTask(withWaypoints = false)).apply {
            hideTaskBottomSheet()
        }

        composeTestRule.setContent {
            MapTaskScreenUi.TaskMinimizedIndicatorOverlay(
                taskScreenManager = taskScreenManager,
                indicatorContent = { Box(Modifier) }
            )
        }

        composeTestRule
            .onAllNodesWithTag(MapTaskScreenUi.Tags.TASK_MINIMIZED_INDICATOR)
            .assertCountEquals(0)
    }

    private fun createManager(task: Task): MapTaskScreenManager {
        val taskManager: TaskManagerCoordinator = mock()
        whenever(taskManager.currentTask).thenReturn(task)

        val mapState = MapScreenState()

        return MapTaskScreenManager(
            mapState = mapState,
            taskManager = taskManager
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
}
