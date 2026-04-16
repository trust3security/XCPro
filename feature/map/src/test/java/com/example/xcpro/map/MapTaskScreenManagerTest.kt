package com.example.xcpro.map

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MapTaskScreenManagerTest {

    @Test
    fun addTask_fromHidden_opensPartial() {
        val manager = createManager(sampleTask(withWaypoints = true))

        manager.handleNavigationTaskSelection("add_task")

        assertEquals(
            MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL,
            manager.taskPanelState.value
        )
    }

    @Test
    fun addTask_fromExpandedPartial_hides() {
        val manager = createManager(sampleTask(withWaypoints = true))
        manager.showTaskPanel(MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL)

        manager.handleNavigationTaskSelection("add_task")

        assertEquals(MapTaskScreenManager.TaskPanelState.HIDDEN, manager.taskPanelState.value)
    }

    @Test
    fun addTask_fromExpandedFull_hides() {
        val manager = createManager(sampleTask(withWaypoints = true))
        manager.showTaskPanel(MapTaskScreenManager.TaskPanelState.EXPANDED_FULL)

        manager.handleNavigationTaskSelection("add_task")

        assertEquals(MapTaskScreenManager.TaskPanelState.HIDDEN, manager.taskPanelState.value)
    }

    @Test
    fun addTask_fromCollapsed_opensPartial() {
        val manager = createManager(sampleTask(withWaypoints = true))
        manager.setPanelState(MapTaskScreenManager.TaskPanelState.COLLAPSED)

        manager.handleNavigationTaskSelection("add_task")

        assertEquals(
            MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL,
            manager.taskPanelState.value
        )
    }

    @Test
    fun collapse_withoutWaypoints_hides() {
        val manager = createManager(sampleTask(withWaypoints = false))
        manager.showTaskPanel(MapTaskScreenManager.TaskPanelState.EXPANDED_FULL)

        manager.collapseTaskPanel()

        assertEquals(MapTaskScreenManager.TaskPanelState.HIDDEN, manager.taskPanelState.value)
    }

    @Test
    fun collapse_withWaypoints_setsCollapsed() {
        val manager = createManager(sampleTask(withWaypoints = true))
        manager.showTaskPanel(MapTaskScreenManager.TaskPanelState.EXPANDED_FULL)

        manager.collapseTaskPanel()

        assertEquals(MapTaskScreenManager.TaskPanelState.COLLAPSED, manager.taskPanelState.value)
    }

    @Test
    fun backGesture_hidesWhenPanelVisible() {
        val manager = createManager(sampleTask(withWaypoints = true))
        manager.showTaskPanel(MapTaskScreenManager.TaskPanelState.EXPANDED_FULL)

        val consumed = manager.handleBackGesture()

        assertTrue(consumed)
        assertEquals(MapTaskScreenManager.TaskPanelState.HIDDEN, manager.taskPanelState.value)
    }

    @Test
    fun backGesture_returnsFalseWhenHidden() {
        val manager = createManager(sampleTask(withWaypoints = true))

        val consumed = manager.handleBackGesture()

        assertFalse(consumed)
        assertEquals(MapTaskScreenManager.TaskPanelState.HIDDEN, manager.taskPanelState.value)
    }

    @Test
    fun handleTaskClear_invokesActionAndHidesPanel() {
        var cleared = false
        val manager = createManager(
            task = sampleTask(withWaypoints = true),
            clearTask = { cleared = true }
        )

        manager.showTaskPanel(MapTaskScreenManager.TaskPanelState.EXPANDED_FULL)
        manager.handleTaskClear()

        assertTrue(cleared)
        assertEquals(MapTaskScreenManager.TaskPanelState.HIDDEN, manager.taskPanelState.value)
    }

    private fun createManager(
        task: Task,
        clearTask: () -> Unit = {},
        saveTask: suspend (String) -> Boolean = { true }
    ): MapTaskScreenManager {
        return MapTaskScreenManager(
            mapState = MapScreenState(),
            currentTaskProvider = { task },
            clearTaskAction = clearTask,
            saveTaskAction = saveTask,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    private fun sampleTask(withWaypoints: Boolean): Task {
        return if (!withWaypoints) {
            Task(id = "empty_task", waypoints = emptyList())
        } else {
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
        }
    }
}
