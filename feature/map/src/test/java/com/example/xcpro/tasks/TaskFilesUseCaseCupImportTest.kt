package com.example.xcpro.tasks

import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.model.GeoPoint
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TaskFilesUseCaseCupImportTest {
    private val repository: TaskFilesRepository = mock()
    private val taskManager: TaskManagerCoordinator = mock()
    private val clock = FakeClock(wallMs = 1_700_000_000_000L)
    private val useCase = TaskFilesUseCase(
        repository = repository,
        taskManager = taskManager,
        clock = clock
    )

    @Test
    fun `import cup parses quoted rows and skips metadata`() = runTest {
        val document = DocumentRef(
            uri = "content://downloads/public_downloads/123",
            displayName = "sample.cup"
        )
        val cupContent = """
            name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc
            "XCPro task","TASK","","","","","","","","","metadata row"
            "Start","START","","5130.000N","00030.000E","0m","1","","","","start line"
            "Turn, One","TP1","","5135.000N","00035.000E","0m","1","","","","quoted,description"
            "Finish","FINISH","","5140.000N","00040.000E","0m","1","","","","finish line"
        """.trimIndent()
        whenever(repository.readText(document)).thenReturn(cupContent)

        val result = useCase.importTaskFile(document)

        assertTrue(result is TaskImportResult.Json)
        val json = (result as TaskImportResult.Json).json
        val persisted = TaskPersistSerializer.deserialize(json)
        assertEquals(TaskType.RACING, persisted.taskType)
        assertEquals(3, persisted.waypoints.size)
        assertEquals(WaypointRole.START, persisted.waypoints[0].role)
        assertEquals("Turn, One", persisted.waypoints[1].title)
        assertEquals(WaypointRole.FINISH, persisted.waypoints[2].role)
    }

    @Test
    fun `export cup writes explicit role codes for start and finish`() = runTest {
        whenever(taskManager.taskType).thenReturn(TaskType.RACING)
        whenever(repository.saveToDownloads(any(), any())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            DocumentRef(
                uri = "content://downloads/public_downloads/$name",
                displayName = name
            )
        }

        val task = Task(
            id = "export-task",
            waypoints = listOf(
                TaskWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = 51.5,
                    lon = 0.5,
                    role = WaypointRole.START
                ),
                TaskWaypoint(
                    id = "tp1",
                    title = "Turnpoint",
                    subtitle = "",
                    lat = 51.6,
                    lon = 0.6,
                    role = WaypointRole.TURNPOINT
                ),
                TaskWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 51.7,
                    lon = 0.7,
                    role = WaypointRole.FINISH
                )
            )
        )

        useCase.exportTaskToDownloads(task)

        val contentCaptor = argumentCaptor<String>()
        verify(repository, times(2)).saveToDownloads(any(), contentCaptor.capture())
        val cupPayload = contentCaptor.allValues.first { it.startsWith("name,code,country,lat,lon,elev,style") }

        assertTrue(cupPayload.contains("\"START\""))
        assertTrue(cupPayload.contains("\"FINISH\""))
        assertFalse(cupPayload.contains("\"001\""))
    }

    @Test
    fun `export json includes provided target snapshots`() = runTest {
        whenever(repository.saveToDownloads(any(), any())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            DocumentRef(
                uri = "content://downloads/public_downloads/$name",
                displayName = name
            )
        }

        val task = Task(
            id = "aat-export",
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
                    id = "tp1",
                    title = "TP1",
                    subtitle = "",
                    lat = 45.1,
                    lon = 7.1,
                    role = WaypointRole.TURNPOINT
                ),
                TaskWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 45.2,
                    lon = 7.2,
                    role = WaypointRole.FINISH
                )
            )
        )
        val targets = listOf(
            TaskTargetSnapshot(
                index = 1,
                id = "tp1",
                name = "TP1",
                allowsTarget = true,
                targetParam = 0.78,
                isLocked = true,
                target = GeoPoint(lat = 45.1234, lon = 7.2345)
            )
        )

        useCase.exportTaskToDownloads(
            task = task,
            taskType = TaskType.AAT,
            targets = targets
        )

        val nameCaptor = argumentCaptor<String>()
        val contentCaptor = argumentCaptor<String>()
        verify(repository, times(2)).saveToDownloads(nameCaptor.capture(), contentCaptor.capture())
        val jsonPayload = contentCaptor.allValues[nameCaptor.allValues.indexOfFirst { it.endsWith(".xcp.json") }]

        val persisted = TaskPersistSerializer.deserialize(jsonPayload)
        assertEquals(0.78, persisted.waypoints[1].targetParam ?: Double.NaN, 1e-9)
        assertEquals(true, persisted.waypoints[1].targetLocked)
        assertEquals(45.1234, persisted.waypoints[1].targetLat ?: Double.NaN, 1e-9)
        assertEquals(7.2345, persisted.waypoints[1].targetLon ?: Double.NaN, 1e-9)
    }
}
