package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TaskManagerCoordinatorTest {

    private val aatDelegate: AATCoordinatorDelegate = mock()
    private val racingDelegate: RacingCoordinatorDelegate = mock()
    private val sampleTask = Task(id = "sample-task")

    private lateinit var coordinator: TaskManagerCoordinator

    @Before
    fun setUp() {
        coordinator = TaskManagerCoordinator(context = null)
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
    fun `checkAATTargetPointHit delegates when task type is AAT`() {
        coordinator.setTaskTypeForTesting(TaskType.AAT)

        coordinator.checkAATTargetPointHit(12f, 34f)

        verify(aatDelegate).checkTargetPointHit(eq(12f), eq(34f))
    }

    @Test
    fun `checkAATTargetPointHit returns null when task type is racing`() {
        coordinator.setTaskTypeForTesting(TaskType.RACING)

        val result = coordinator.checkAATTargetPointHit(10f, 20f)

        assertNull(result)
        verify(aatDelegate, never()).checkTargetPointHit(any<Float>(), any<Float>())
    }

    @Test
    fun `plotOnMap delegates to racing implementation when task type is racing`() {
        coordinator.setTaskTypeForTesting(TaskType.RACING)

        coordinator.plotOnMap(null)

        verify(racingDelegate).plotOnMap(null)
    }

    @Test
    fun `plotOnMap delegates to AAT implementation when task type is AAT`() {
        coordinator.setTaskTypeForTesting(TaskType.AAT)

        coordinator.plotOnMap(null)

        verify(aatDelegate).plotOnMap(null)
    }

    @Test
    fun `calculateTaskDistanceForTask uses current delegate distance`() {
        coordinator.setTaskTypeForTesting(TaskType.RACING)
        whenever(racingDelegate.calculateDistance()).thenReturn(123.4)

        val distance = coordinator.calculateTaskDistanceForTask(sampleTask)

        assertEquals(123.4, distance, 0.0)
        verify(racingDelegate).calculateDistance()
        verify(aatDelegate, never()).calculateDistance()
    }
}



