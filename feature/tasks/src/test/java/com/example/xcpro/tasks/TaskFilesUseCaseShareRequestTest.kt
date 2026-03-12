package com.example.xcpro.tasks

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TaskFilesUseCaseShareRequestTest {
    private val repository: TaskFilesRepository = mock()
    private val taskManager: TaskManagerCoordinator = mock()
    private val clock = FakeClock(wallMs = 1_700_000_000_000L)
    private val useCase = TaskFilesUseCase(
        repository = repository,
        taskManager = taskManager,
        clock = clock
    )

    @Test
    fun `share task returns single multi-document request when both files are available`() = runTest {
        whenever(repository.writeCacheFile(any(), any())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            DocumentRef(
                uri = "content://cache/$name",
                displayName = name
            )
        }

        val request = useCase.shareTask(
            task = sampleTask(),
            taskType = TaskType.AAT
        )

        assertNotNull(request)
        requireNotNull(request)
        assertEquals("*/*", request.mime)
        assertEquals(2, request.allDocuments.size)
        assertTrue(request.allDocuments.any { it.displayName?.endsWith(".cup") == true })
        assertTrue(request.allDocuments.any { it.displayName?.endsWith(".xcp.json") == true })
    }

    @Test
    fun `share task falls back to single json request when cup cache write fails`() = runTest {
        whenever(repository.writeCacheFile(any(), any())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            if (name.endsWith(".cup")) {
                throw IllegalStateException("cup write failed")
            }
            DocumentRef(
                uri = "content://cache/$name",
                displayName = name
            )
        }

        val request = useCase.shareTask(
            task = sampleTask(),
            taskType = TaskType.RACING
        )

        assertNotNull(request)
        requireNotNull(request)
        assertEquals("application/json", request.mime)
        assertEquals(1, request.allDocuments.size)
        assertTrue(request.document.displayName?.endsWith(".xcp.json") == true)
    }

    private fun sampleTask(): Task {
        return Task(
            id = "share-task",
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
