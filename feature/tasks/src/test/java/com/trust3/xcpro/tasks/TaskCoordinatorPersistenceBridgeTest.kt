package com.trust3.xcpro.tasks

import com.trust3.xcpro.common.waypoint.SearchWaypoint
import com.trust3.xcpro.tasks.aat.AATTaskManager
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.domain.engine.AATTaskEngine
import com.trust3.xcpro.tasks.domain.engine.RacingTaskEngine
import com.trust3.xcpro.tasks.domain.persistence.TaskEnginePersistenceService
import com.trust3.xcpro.tasks.racing.RacingTaskManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCoordinatorPersistenceBridgeTest {

    @Test
    fun `syncAndAutosave pushes active manager state through engine persistence owner`() = runTest {
        val service: TaskEnginePersistenceService = mock()
        val racingEngine: RacingTaskEngine = mock()
        val aatEngine: AATTaskEngine = mock()
        val racingManager = RacingTaskManager().apply {
            initializeRacingTask(
                listOf(
                    searchWaypoint("start", 0.0, 0.0),
                    searchWaypoint("finish", 0.1, 0.1)
                )
            )
        }
        val bridge = TaskCoordinatorPersistenceBridge(
            taskTypeState = MutableStateFlow(TaskType.RACING),
            taskEnginePersistenceService = service,
            racingTaskEngine = racingEngine,
            aatTaskEngine = aatEngine,
            racingTaskManager = racingManager,
            aatTaskManager = AATTaskManager(),
            scope = this,
            log = {}
        )

        bridge.syncAndAutosave(TaskType.RACING)
        advanceUntilIdle()

        val captor = argumentCaptor<com.trust3.xcpro.tasks.core.Task>()
        verify(racingEngine).setTask(captor.capture())
        verify(aatEngine, never()).setTask(any())
        verify(service).autosaveEngines()
        assertEquals(listOf("start", "finish"), captor.firstValue.waypoints.map { it.id })
    }

    @Test
    fun `syncAllAndAutosave syncs both manager states before autosave`() = runTest {
        val service: TaskEnginePersistenceService = mock()
        val racingEngine: RacingTaskEngine = mock()
        val aatEngine: AATTaskEngine = mock()
        val racingManager = RacingTaskManager().apply {
            initializeRacingTask(
                listOf(
                    searchWaypoint("race-start", 0.0, 0.0),
                    searchWaypoint("race-finish", 0.1, 0.1)
                )
            )
        }
        val aatManager = AATTaskManager().apply {
            initializeAATTask(
                listOf(
                    searchWaypoint("aat-start", 45.0, 7.0),
                    searchWaypoint("aat-finish", 45.1, 7.1)
                )
            )
        }
        val bridge = TaskCoordinatorPersistenceBridge(
            taskTypeState = MutableStateFlow(TaskType.AAT),
            taskEnginePersistenceService = service,
            racingTaskEngine = racingEngine,
            aatTaskEngine = aatEngine,
            racingTaskManager = racingManager,
            aatTaskManager = aatManager,
            scope = this,
            log = {}
        )

        bridge.syncAllAndAutosave()
        advanceUntilIdle()

        verify(racingEngine).setTask(any())
        verify(aatEngine).setTask(any())
        verify(service).autosaveEngines()
    }

    private fun searchWaypoint(id: String, lat: Double, lon: Double): SearchWaypoint =
        SearchWaypoint(
            id = id,
            title = id,
            subtitle = "",
            lat = lat,
            lon = lon
        )
}
