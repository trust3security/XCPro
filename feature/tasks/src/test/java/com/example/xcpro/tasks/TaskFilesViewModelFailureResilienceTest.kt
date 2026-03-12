package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TaskFilesViewModelFailureResilienceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val useCase: TaskFilesUseCase = mock()

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `share task emits generic failure message when request is null`() = runTest {
        whenever(useCase.loadDownloads()).thenReturn(emptyList())
        whenever(useCase.shareTask(any(), any(), any())).thenReturn(null)
        val viewModel = TaskFilesViewModel(useCase)
        advanceUntilIdle()

        val eventDeferred = async { viewModel.events.first() }
        viewModel.shareTask(
            task = sampleTask(),
            taskType = TaskType.RACING,
            targets = emptyList()
        )
        advanceUntilIdle()

        val event = eventDeferred.await()
        assertTrue(event is TaskFilesEvent.ShowMessage)
        assertEquals("Share failed", (event as TaskFilesEvent.ShowMessage).message)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `share task maps thrown exception to failure message`() = runTest {
        whenever(useCase.loadDownloads()).thenReturn(emptyList())
        whenever(useCase.shareTask(any(), any(), any())).thenThrow(IllegalStateException("boom"))
        val viewModel = TaskFilesViewModel(useCase)
        advanceUntilIdle()

        val eventDeferred = async { viewModel.events.first() }
        viewModel.shareTask(
            task = sampleTask(),
            taskType = TaskType.RACING,
            targets = emptyList()
        )
        advanceUntilIdle()

        val event = eventDeferred.await()
        assertTrue(event is TaskFilesEvent.ShowMessage)
        assertTrue((event as TaskFilesEvent.ShowMessage).message.startsWith("Share failed: boom"))
    }

    private fun sampleTask(): Task {
        return Task(
            id = "task",
            waypoints = listOf(
                TaskWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = 45.0,
                    lon = 7.0,
                    role = WaypointRole.START
                ),
                TaskWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 45.1,
                    lon = 7.1,
                    role = WaypointRole.FINISH
                )
            )
        )
    }
}
