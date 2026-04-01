package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TaskWaypointParamKeys
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.domain.engine.AATTaskEngine
import com.example.xcpro.tasks.domain.engine.AATTaskEngineState
import com.example.xcpro.tasks.domain.engine.RacingTaskEngine
import com.example.xcpro.tasks.domain.engine.RacingTaskEngineState
import com.example.xcpro.tasks.domain.engine.TaskEngineState
import com.example.xcpro.tasks.domain.persistence.TaskEnginePersistenceService
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class TaskManagerCoordinatorTest {
    private data class SegmentFixture(
        val fromLat: Double,
        val fromLon: Double,
        val toLat: Double,
        val toLon: Double
    )

    private val aatDelegate: AATCoordinatorDelegate = mock()
    private val racingDelegate: RacingCoordinatorDelegate = mock()
    private val persistenceService: TaskEnginePersistenceService = mock()
    private val racingTaskEngine: RacingTaskEngine = mock()
    private val aatTaskEngine: AATTaskEngine = mock()

    private lateinit var coordinator: TaskManagerCoordinator

    @Before
    fun setUp() {
        whenever(racingTaskEngine.state).thenReturn(MutableStateFlow(RacingTaskEngineState()))
        whenever(aatTaskEngine.state).thenReturn(MutableStateFlow(AATTaskEngineState()))
        coordinator = TaskManagerCoordinator(
            taskEnginePersistenceService = persistenceService,
            racingTaskEngine = racingTaskEngine,
            aatTaskEngine = aatTaskEngine,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager()
        )
        coordinator.replaceAATDelegateForTesting(aatDelegate)
        coordinator.replaceRacingDelegateForTesting(racingDelegate)
    }

    @Test
    fun `updateAATTargetPoint delegates when current task is AAT`() {
        coordinator.setTaskTypeForTesting(TaskType.AAT)

        coordinator.updateAATTargetPoint(3, 51.3, -0.2)

        verify(aatDelegate).updateTargetPoint(eq(3), eq(51.3), eq(-0.2))
    }

    @Test
    fun `updateAATTargetPoint logs and skips when task type is racing`() {
        coordinator.setTaskTypeForTesting(TaskType.RACING)

        coordinator.updateAATTargetPoint(1, 40.0, 20.0)

        verify(aatDelegate, never()).updateTargetPoint(any<Int>(), any<Double>(), any<Double>())
    }

    @Test
    fun `updateAATArea routes through delegate`() {
        coordinator.setTaskTypeForTesting(TaskType.AAT)

        coordinator.updateAATArea(2, 7500.0)

        verify(aatDelegate).updateArea(eq(2), eq(7500.0))
    }

    @Test
    fun `calculateTaskDistanceForTask uses provided task waypoints via segment delegate`() {
        coordinator.setTaskTypeForTesting(TaskType.RACING)
        val first = coreWaypoint(id = "w1", lat = 0.0, lon = 0.0, role = WaypointRole.START)
        val second = coreWaypoint(id = "w2", lat = 0.0, lon = 1.0, role = WaypointRole.TURNPOINT)
        val third = coreWaypoint(id = "w3", lat = 1.0, lon = 1.0, role = WaypointRole.FINISH)
        whenever(racingDelegate.calculateSegmentDistanceMeters(eq(first), eq(second))).thenReturn(12_500.0)
        whenever(racingDelegate.calculateSegmentDistanceMeters(eq(second), eq(third))).thenReturn(7_500.0)

        val task = Task(
            id = "external-task",
            waypoints = listOf(first, second, third)
        )
        val distanceMeters = coordinator.calculateTaskDistanceForTaskMeters(task)

        assertEquals(20_000.0, distanceMeters, 0.0)
        verify(racingDelegate, never()).calculateDistanceMeters()
        verify(racingDelegate).calculateSegmentDistanceMeters(eq(first), eq(second))
        verify(racingDelegate).calculateSegmentDistanceMeters(eq(second), eq(third))
        verify(aatDelegate, never()).calculateSegmentDistanceMeters(any(), any())
    }

    @Test
    fun `racing add remove reorder waypoints keeps expected order`() {
        coordinator.setTaskTypeForTesting(TaskType.RACING)

        coordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        coordinator.addWaypoint(searchWaypoint("tp1", 0.1, 0.1))
        coordinator.addWaypoint(searchWaypoint("finish", 0.2, 0.2))
        assertEquals(listOf("start", "tp1", "finish"), coordinator.currentTask.waypoints.map { it.id })

        coordinator.removeWaypoint(1)
        assertEquals(listOf("start", "finish"), coordinator.currentTask.waypoints.map { it.id })

        coordinator.reorderWaypoints(1, 0)
        assertEquals(listOf("finish", "start"), coordinator.currentTask.waypoints.map { it.id })
    }

    @Test
    fun `aat add remove reorder waypoints keeps expected order`() {
        coordinator.setTaskTypeForTesting(TaskType.AAT)

        coordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        coordinator.addWaypoint(searchWaypoint("tp1", 0.1, 0.1))
        coordinator.addWaypoint(searchWaypoint("finish", 0.2, 0.2))
        assertEquals(listOf("start", "tp1", "finish"), coordinator.currentTask.waypoints.map { it.id })

        coordinator.reorderWaypoints(2, 1)
        assertEquals(listOf("start", "finish", "tp1"), coordinator.currentTask.waypoints.map { it.id })

        coordinator.removeWaypoint(1)
        assertEquals(listOf("start", "tp1"), coordinator.currentTask.waypoints.map { it.id })
    }

    @Test
    fun `switch task type preserves waypoint ids across racing and aat`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        localCoordinator.setTaskTypeForTesting(TaskType.RACING)
        localCoordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        localCoordinator.addWaypoint(searchWaypoint("finish", 0.3, 0.3))
        val expectedIds = localCoordinator.currentTask.waypoints.map { it.id }

        localCoordinator.setTaskType(TaskType.AAT)
        assertEquals(TaskType.AAT, localCoordinator.taskType)
        val transferredTaskId = localCoordinator.currentTask.id
        assertTrue(transferredTaskId.isNotBlank())
        assertEquals(expectedIds, localCoordinator.currentTask.waypoints.map { it.id })

        localCoordinator.setTaskType(TaskType.RACING)
        assertEquals(TaskType.RACING, localCoordinator.taskType)
        assertEquals(transferredTaskId, localCoordinator.currentTask.id)
        assertEquals(expectedIds, localCoordinator.currentTask.waypoints.map { it.id })
    }

    @Test
    fun `taskSnapshotFlow mirrors coordinator task mutations`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        localCoordinator.setTaskTypeForTesting(TaskType.RACING)
        localCoordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        localCoordinator.addWaypoint(searchWaypoint("finish", 0.3, 0.3))
        localCoordinator.setActiveLeg(1)

        val snapshot = localCoordinator.taskSnapshotFlow.value

        assertEquals(TaskType.RACING, snapshot.taskType)
        assertEquals(listOf("start", "finish"), snapshot.task.waypoints.map { it.id })
        assertEquals(1, snapshot.activeLeg)
    }

    @Test
    fun `racingAdvanceSnapshotFlow mirrors coordinator racing advance mutations`() {
        val localCoordinator = createCoordinatorWithoutPersistence()

        localCoordinator.setRacingAdvanceMode(RacingAdvanceState.Mode.MANUAL)
        localCoordinator.setRacingAdvanceArmed(true)
        localCoordinator.onRacingStartAccepted()

        assertEquals(
            RacingAdvanceState.Snapshot(
                mode = RacingAdvanceState.Mode.MANUAL,
                armState = RacingAdvanceState.ArmState.TURN_ARMED,
                isArmed = true
            ),
            localCoordinator.racingAdvanceSnapshotFlow.value
        )

        localCoordinator.resetRacingAdvanceToStartPhase()

        assertEquals(
            RacingAdvanceState.Snapshot(
                mode = RacingAdvanceState.Mode.MANUAL,
                armState = RacingAdvanceState.ArmState.START_ARMED,
                isArmed = true
            ),
            localCoordinator.racingAdvanceSnapshot()
        )
    }

    @Test
    fun `aatEditWaypointIndexFlow tracks enter and exit edit mode`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        localCoordinator.setTaskTypeForTesting(TaskType.AAT)
        localCoordinator.addWaypoint(searchWaypoint("start", 45.0, 7.0))
        localCoordinator.addWaypoint(searchWaypoint("tp1", 45.05, 7.05))
        localCoordinator.addWaypoint(searchWaypoint("finish", 45.1, 7.1))

        localCoordinator.enterAATEditMode(1)
        assertEquals(1, localCoordinator.aatEditWaypointIndexFlow.value)
        assertTrue(localCoordinator.isInAATEditMode())

        localCoordinator.exitAATEditMode()
        assertEquals(null, localCoordinator.aatEditWaypointIndexFlow.value)
        assertTrue(localCoordinator.isInAATEditMode().not())
    }

    @Test
    fun `aatEditWaypointIndexFlow clears on task-type switch`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        localCoordinator.setTaskTypeForTesting(TaskType.AAT)
        localCoordinator.addWaypoint(searchWaypoint("start", 45.0, 7.0))
        localCoordinator.addWaypoint(searchWaypoint("tp1", 45.05, 7.05))
        localCoordinator.addWaypoint(searchWaypoint("finish", 45.1, 7.1))
        localCoordinator.enterAATEditMode(1)

        localCoordinator.setTaskType(TaskType.RACING)

        assertEquals(null, localCoordinator.aatEditWaypointIndexFlow.value)
        assertTrue(localCoordinator.isInAATEditMode().not())
    }

    @Test
    fun `setAATTargetParam publishes canonical target metadata`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        localCoordinator.setTaskTypeForTesting(TaskType.AAT)
        localCoordinator.addWaypoint(searchWaypoint("start", 45.0, 7.0))
        localCoordinator.addWaypoint(searchWaypoint("tp1", 45.05, 7.05))
        localCoordinator.addWaypoint(searchWaypoint("finish", 45.1, 7.1))

        localCoordinator.setAATTargetParam(1, 0.25)

        val waypoint = localCoordinator.taskSnapshotFlow.value.task.waypoints[1]
        assertEquals(0.25, waypoint.customParameters[TaskWaypointParamKeys.TARGET_PARAM] as Double, 1e-9)
        assertEquals(false, waypoint.customParameters[TaskWaypointParamKeys.TARGET_LOCKED])
        assertTrue(waypoint.customParameters.containsKey(TaskWaypointParamKeys.TARGET_LAT))
        assertTrue(waypoint.customParameters.containsKey(TaskWaypointParamKeys.TARGET_LON))
    }

    @Test
    fun `applyAATTargetState preserves explicit locked target metadata in snapshot`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        localCoordinator.setTaskTypeForTesting(TaskType.AAT)
        localCoordinator.addWaypoint(searchWaypoint("start", 45.0, 7.0))
        localCoordinator.addWaypoint(searchWaypoint("tp1", 45.05, 7.05))
        localCoordinator.addWaypoint(searchWaypoint("finish", 45.1, 7.1))

        localCoordinator.applyAATTargetState(
            index = 1,
            targetParam = 0.67,
            targetLocked = true,
            targetLat = 45.11,
            targetLon = 7.11
        )

        val waypoint = localCoordinator.taskSnapshotFlow.value.task.waypoints[1]
        assertEquals(0.67, waypoint.customParameters[TaskWaypointParamKeys.TARGET_PARAM] as Double, 1e-9)
        assertEquals(true, waypoint.customParameters[TaskWaypointParamKeys.TARGET_LOCKED])
        assertEquals(45.11, waypoint.customParameters[TaskWaypointParamKeys.TARGET_LAT] as Double, 1e-9)
        assertEquals(7.11, waypoint.customParameters[TaskWaypointParamKeys.TARGET_LON] as Double, 1e-9)
    }

    @Test
    fun `racing segment distance calculation is positive and symmetric`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        localCoordinator.setTaskTypeForTesting(TaskType.RACING)
        val from = coreWaypoint(id = "a", lat = 0.0, lon = 0.0, role = WaypointRole.START)
        val to = coreWaypoint(id = "b", lat = 0.0, lon = 1.0, role = WaypointRole.TURNPOINT)

        val forward = localCoordinator.calculateSimpleSegmentDistanceMeters(from, to)
        val reverse = localCoordinator.calculateSimpleSegmentDistanceMeters(to, from)

        assertTrue(forward > 100_000.0)
        assertTrue(forward < 120_000.0)
        assertEquals(forward, reverse, 1e-9)
    }

    @Test
    fun `segment distance meter contract holds across racing and aat fixture matrix`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        val fixtures = listOf(
            SegmentFixture(0.0, 0.0, 0.0, 1.0),
            SegmentFixture(0.0, 0.0, 1.0, 0.0),
            SegmentFixture(45.0, 7.0, 45.3, 7.4),
            SegmentFixture(-34.90, 138.60, -35.00, 138.80)
        )

        fixtures.forEachIndexed { index, fixture ->
            val from = coreWaypoint(
                id = "from-$index",
                lat = fixture.fromLat,
                lon = fixture.fromLon,
                role = WaypointRole.START
            )
            val to = coreWaypoint(
                id = "to-$index",
                lat = fixture.toLat,
                lon = fixture.toLon,
                role = WaypointRole.TURNPOINT
            )

            localCoordinator.setTaskTypeForTesting(TaskType.RACING)
            val racingDistanceMeters = localCoordinator.calculateSimpleSegmentDistanceMeters(from, to)
            val expectedRacingMeters = RacingGeometryUtils.haversineDistanceMeters(
                fixture.fromLat,
                fixture.fromLon,
                fixture.toLat,
                fixture.toLon
            )
            assertEquals(
                "Racing fixture index $index should resolve SI meter distance",
                expectedRacingMeters,
                racingDistanceMeters,
                1.0
            )

            localCoordinator.setTaskTypeForTesting(TaskType.AAT)
            val aatDistanceMeters = localCoordinator.calculateSimpleSegmentDistanceMeters(from, to)
            val expectedAatMeters = AATMathUtils.calculateDistanceMeters(
                fixture.fromLat,
                fixture.fromLon,
                fixture.toLat,
                fixture.toLon
            )
            assertEquals(
                "AAT fixture index $index should resolve SI meter distance",
                expectedAatMeters,
                aatDistanceMeters,
                1.0
            )
        }
    }

    @Test
    fun `optimal start line crossing converts racing gate width from km to meters`() {
        val localCoordinator = createCoordinatorWithoutPersistence()
        localCoordinator.setTaskTypeForTesting(TaskType.RACING)
        localCoordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        localCoordinator.addWaypoint(searchWaypoint("next", 0.0, 1.0))

        val start = localCoordinator.currentTask.waypoints.first()
        val next = localCoordinator.currentTask.waypoints[1]
        val crossing = localCoordinator.calculateOptimalStartLineCrossingPoint(start, next)
        val offsetMeters = RacingGeometryUtils.haversineDistanceMeters(
            start.lat,
            start.lon,
            crossing.first,
            crossing.second
        )

        // Default racing start gate width is 10km; crossing is at half-width from center.
        assertTrue(offsetMeters > 4_500.0)
        assertTrue(offsetMeters < 5_500.0)
    }

    @Test
    fun `loadSavedTasks restores type from persistence service`() = runTest {
        whenever(persistenceService.restore(any())).thenReturn(TaskType.AAT)

        coordinator.loadSavedTasks()

        assertEquals(TaskType.AAT, coordinator.taskType)
        verify(persistenceService).restore(eq(TaskType.RACING))
    }

    @Test
    fun `loadSavedTasks publishes restored task snapshot`() = runTest {
        val restoredTask = Task(
            id = "restored-racing-task",
            waypoints = listOf(
                coreWaypoint(id = "start", lat = 0.0, lon = 0.0, role = WaypointRole.START),
                coreWaypoint(id = "finish", lat = 0.1, lon = 0.1, role = WaypointRole.FINISH)
            )
        )
        whenever(persistenceService.restore(any())).thenReturn(TaskType.RACING)
        whenever(racingTaskEngine.state).thenReturn(
            MutableStateFlow(
                RacingTaskEngineState(
                    base = TaskEngineState(
                        taskType = TaskType.RACING,
                        task = restoredTask,
                        activeLegIndex = 1,
                        isTaskValid = true
                    )
                )
            )
        )

        coordinator.loadSavedTasks()

        val snapshot = coordinator.taskSnapshotFlow.value
        assertEquals(TaskType.RACING, snapshot.taskType)
        assertEquals("restored-racing-task", snapshot.task.id)
        assertEquals(listOf("start", "finish"), snapshot.task.waypoints.map { it.id })
        assertEquals(1, snapshot.activeLeg)
    }

    @Test
    fun `loadSavedTasks publishes restored AAT canonical target metadata`() = runTest {
        val restoredTask = Task(
            id = "restored-aat-task",
            waypoints = listOf(
                coreWaypoint(id = "start", lat = 45.0, lon = 7.0, role = WaypointRole.START),
                coreWaypoint(
                    id = "tp1",
                    lat = 45.1,
                    lon = 7.1,
                    role = WaypointRole.TURNPOINT,
                    customParameters = mapOf(
                        TaskWaypointParamKeys.TARGET_PARAM to 0.58,
                        TaskWaypointParamKeys.TARGET_LOCKED to true,
                        TaskWaypointParamKeys.TARGET_LAT to 45.1234,
                        TaskWaypointParamKeys.TARGET_LON to 7.2345
                    )
                ),
                coreWaypoint(id = "finish", lat = 45.2, lon = 7.2, role = WaypointRole.FINISH)
            )
        )
        whenever(persistenceService.restore(any())).thenReturn(TaskType.AAT)
        whenever(aatTaskEngine.state).thenReturn(
            MutableStateFlow(
                AATTaskEngineState(
                    base = TaskEngineState(
                        taskType = TaskType.AAT,
                        task = restoredTask,
                        activeLegIndex = 1
                    )
                )
            )
        )

        coordinator.loadSavedTasks()

        val snapshot = coordinator.taskSnapshotFlow.value
        val turnpoint = snapshot.task.waypoints[1]
        assertEquals(TaskType.AAT, snapshot.taskType)
        assertEquals("restored-aat-task", snapshot.task.id)
        assertEquals(1, snapshot.activeLeg)
        assertEquals(0.58, turnpoint.customParameters[TaskWaypointParamKeys.TARGET_PARAM] as Double, 1e-9)
        assertEquals(true, turnpoint.customParameters[TaskWaypointParamKeys.TARGET_LOCKED])
        assertEquals(45.1234, turnpoint.customParameters[TaskWaypointParamKeys.TARGET_LAT] as Double, 1e-9)
        assertEquals(7.2345, turnpoint.customParameters[TaskWaypointParamKeys.TARGET_LON] as Double, 1e-9)
    }

    @Test
    fun `saveTask routes named save through persistence service`() = runTest {
        whenever(persistenceService.saveNamedTask(any(), any())).thenReturn(true)
        coordinator.setTaskTypeForTesting(TaskType.RACING)
        coordinator.addWaypoint(searchWaypoint("start", 0.0, 0.0))
        coordinator.addWaypoint(searchWaypoint("finish", 0.1, 0.1))

        val result = coordinator.saveTask("demo-task")

        assertTrue(result)
        verify(persistenceService).saveNamedTask(eq(TaskType.RACING), eq("demo-task"))
    }

    private fun createCoordinatorWithoutPersistence(): TaskManagerCoordinator =
        TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager()
        )

    private fun searchWaypoint(id: String, lat: Double, lon: Double): SearchWaypoint =
        SearchWaypoint(
            id = id,
            title = id,
            subtitle = "",
            lat = lat,
            lon = lon
        )

    private fun coreWaypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole,
        customParameters: Map<String, Any> = emptyMap()
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id,
        subtitle = "",
        lat = lat,
        lon = lon,
        role = role,
        customParameters = customParameters
    )
}



