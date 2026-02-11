package com.example.xcpro.tasks

import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AATCoordinatorDelegateTest {

    private val logs = mutableListOf<String>()
    private val log: (String) -> Unit = { logs += it }

    @Before
    fun setUp() {
        logs.clear()
    }

    @Test
    fun `updateWaypointPointType delegates update bridge`() {
        val taskManager = mock<AATTaskManager>()
        val delegate = delegate(taskManager)

        delegate.updateWaypointPointType(
            index = 1,
            startType = "start",
            finishType = "finish",
            turnType = "turn",
            gateWidth = 1.2,
            keyholeInnerRadius = 3.4,
            keyholeAngle = 45.0,
            sectorOuterRadius = 5.6
        )

        verify(taskManager).updateWaypointPointTypeBridge(
            index = 1,
            startType = "start",
            finishType = "finish",
            turnType = "turn",
            gateWidth = 1.2,
            keyholeInnerRadius = 3.4,
            keyholeAngle = 45.0,
            sectorOuterRadius = 5.6
        )
    }

    @Test
    fun `updateWaypointPointType supports null attrs`() {
        val taskManager = mock<AATTaskManager>()
        val delegate = delegate(taskManager)

        delegate.updateWaypointPointType(
            index = 0,
            startType = null,
            finishType = null,
            turnType = null,
            gateWidth = null,
            keyholeInnerRadius = null,
            keyholeAngle = null,
            sectorOuterRadius = null
        )

        verify(taskManager).updateWaypointPointTypeBridge(
            index = 0,
            startType = null,
            finishType = null,
            turnType = null,
            gateWidth = null,
            keyholeInnerRadius = null,
            keyholeAngle = null,
            sectorOuterRadius = null
        )
    }

    @Test
    fun `updateParameters delegates to task manager`() {
        val taskManager = mock<AATTaskManager>()
        val delegate = delegate(taskManager)

        val min = Duration.ofHours(2)
        val max = Duration.ofHours(4)

        delegate.updateParameters(min, max)

        verify(taskManager).updateAATTimes(min, max)
        assertTrue(logs.any { it.contains("Updated AAT parameters") })
    }

    @Test
    fun `updateArea updates radius when waypoint present`() {
        val taskManager = mock<AATTaskManager>()
        val waypoint = AATWaypoint(
            id = "wp-1",
            title = "Turnpoint",
            subtitle = "TP1",
            lat = 1.0,
            lon = 2.0,
            role = AATWaypointRole.TURNPOINT,
            assignedArea = AATAssignedArea(
                shape = AATAreaShape.CIRCLE,
                radiusMeters = 2000.0,
                outerRadiusMeters = 2000.0
            )
        )
        whenever(taskManager.currentAATTask).thenReturn(SimpleAATTask(waypoints = listOf(waypoint)))
        val delegate = delegate(taskManager)

        delegate.updateArea(index = 0, radiusMeters = 5000.0)

        val captor = argumentCaptor<AATAssignedArea>()
        verify(taskManager).updateAATArea(eq(0), captor.capture())
        assertEquals(5000.0, captor.firstValue.radiusMeters, 0.0)
        assertTrue(logs.any { it.contains("Updated AAT area radius at index 0 to 5.0km") })
    }

    @Test
    fun `updateArea skips when waypoint missing`() {
        val taskManager = mock<AATTaskManager>()
        whenever(taskManager.currentAATTask).thenReturn(SimpleAATTask())
        val delegate = delegate(taskManager)

        delegate.updateArea(index = 3, radiusMeters = 1000.0)

        verify(taskManager, never()).updateAATArea(any<Int>(), any<AATAssignedArea>())
        assertTrue(logs.none { it.contains("Updated AAT area radius") })
    }

    @Test
    fun `enterEditMode delegates to task manager`() {
        val taskManager = mock<AATTaskManager>()
        val delegate = delegate(taskManager)

        delegate.enterEditMode(2)

        verify(taskManager).setEditMode(2, true)
    }

    @Test
    fun `exitEditMode clears edit mode`() {
        val taskManager = mock<AATTaskManager>()
        val delegate = delegate(taskManager)

        delegate.exitEditMode()

        verify(taskManager).setEditMode(-1, false)
    }

    private fun delegate(
        taskManager: AATTaskManager
    ) = AATCoordinatorDelegate(taskManager, log)
}



