package com.trust3.xcpro.tasks.data.persistence

import com.trust3.xcpro.tasks.core.AATTaskTimeCustomParams
import com.trust3.xcpro.tasks.core.AATWaypointCustomParams
import com.trust3.xcpro.tasks.core.TargetStateCustomParams
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.TaskWaypointParamKeys
import com.trust3.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AATCanonicalTaskStorageTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `save and load named task preserves canonical target metadata and time params`() {
        val storage = createStorage()
        val task = canonicalAatTask(id = "phase3-named")

        assertTrue(storage.saveTask("demo-task", task))

        val loaded = storage.loadTask("demo-task")

        assertNotNull(loaded)
        assertEquals(listOf("demo-task"), storage.listTaskNames())
        assertCanonicalAatState(loaded!!)
    }

    @Test
    fun `autosave roundtrip preserves canonical target metadata and time params`() {
        val storage = createStorage()
        val task = canonicalAatTask(id = "phase3-auto")

        storage.saveAutosavedTask(task)

        val loaded = storage.loadAutosavedTask()

        assertNotNull(loaded)
        assertCanonicalAatState(loaded!!)
    }

    @Test
    fun `legacy fallback migrates named and autosaved tasks into canonical storage`() {
        val preference = InMemoryStringPreference()
        val legacy = FakeLegacyAatTaskSource().apply {
            namedTasks["legacy-demo"] = canonicalAatTask(id = "legacy-named")
            autosavedTask = canonicalAatTask(id = "legacy-auto")
        }
        val storage = AATCanonicalTaskStorage(
            namedTasksDir = temporaryFolder.newFolder("aat-store"),
            autosavePreference = preference,
            legacyStore = legacy
        )

        val namedTask = storage.loadTask("legacy-demo")
        val autosavedTask = storage.loadAutosavedTask()

        legacy.namedTasks.clear()
        legacy.autosavedTask = null

        val migratedNamedTask = storage.loadTask("legacy-demo")
        val migratedAutosavedTask = storage.loadAutosavedTask()

        assertNotNull(namedTask)
        assertNotNull(autosavedTask)
        assertNotNull(migratedNamedTask)
        assertNotNull(migratedAutosavedTask)
        assertCanonicalAatState(migratedNamedTask!!)
        assertCanonicalAatState(migratedAutosavedTask!!)
        assertTrue(preference.read()?.isNotBlank() == true)
        assertEquals(listOf("legacy-demo"), storage.listTaskNames())
    }

    private fun createStorage(): AATCanonicalTaskStorage {
        return AATCanonicalTaskStorage(
            namedTasksDir = temporaryFolder.newFolder("aat-store"),
            autosavePreference = InMemoryStringPreference(),
            legacyStore = null
        )
    }

    private fun canonicalAatTask(id: String): Task {
        val startParams = mutableMapOf<String, Any>()
        AATTaskTimeCustomParams(
            minimumTimeSeconds = 7_200.0,
            maximumTimeSeconds = 10_800.0
        ).applyTo(startParams)

        val targetParams = mutableMapOf<String, Any>()
        AATWaypointCustomParams(
            radiusMeters = 5_000.0,
            outerRadiusMeters = 5_000.0,
            innerRadiusMeters = 0.0,
            startAngleDegrees = 0.0,
            endAngleDegrees = 90.0,
            lineWidthMeters = 5_000.0,
            targetLat = 45.1234,
            targetLon = 7.2345,
            isTargetPointCustomized = true
        ).applyTo(targetParams)
        TargetStateCustomParams(
            targetParam = 0.67,
            targetLocked = true,
            targetLat = 45.1234,
            targetLon = 7.2345
        ).applyTo(targetParams)

        return Task(
            id = id,
            waypoints = listOf(
                TaskWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = 45.0,
                    lon = 7.0,
                    role = WaypointRole.START,
                    customPointType = "AAT_START_LINE",
                    customParameters = startParams
                ),
                TaskWaypoint(
                    id = "tp1",
                    title = "TP1",
                    subtitle = "",
                    lat = 45.1,
                    lon = 7.1,
                    role = WaypointRole.TURNPOINT,
                    customRadiusMeters = 5_000.0,
                    customPointType = "AAT_CYLINDER",
                    customParameters = targetParams
                ),
                TaskWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 45.2,
                    lon = 7.2,
                    role = WaypointRole.FINISH,
                    customPointType = "AAT_FINISH_CYLINDER",
                    customParameters = startParams
                )
            )
        )
    }

    private fun assertCanonicalAatState(task: Task) {
        val turnpoint = task.waypoints[1]
        val firstWaypoint = task.waypoints.first()

        assertEquals("tp1", turnpoint.id)
        assertEquals(0.67, turnpoint.customParameters[TaskWaypointParamKeys.TARGET_PARAM] as Double, 1e-9)
        assertEquals(true, turnpoint.customParameters[TaskWaypointParamKeys.TARGET_LOCKED])
        assertEquals(45.1234, turnpoint.customParameters[TaskWaypointParamKeys.TARGET_LAT] as Double, 1e-9)
        assertEquals(7.2345, turnpoint.customParameters[TaskWaypointParamKeys.TARGET_LON] as Double, 1e-9)

        val timeParams = AATTaskTimeCustomParams.from(
            source = firstWaypoint.customParameters,
            fallbackMinimumTimeSeconds = 0.0,
            fallbackMaximumTimeSeconds = null
        )
        assertEquals(7_200.0, timeParams.minimumTimeSeconds, 1e-9)
        assertEquals(10_800.0, timeParams.maximumTimeSeconds ?: Double.NaN, 1e-9)
    }
}

private class InMemoryStringPreference : CanonicalTaskStringPreference {
    private var value: String? = null

    override fun read(): String? = value

    override fun write(value: String) {
        this.value = value
    }

    override fun clear() {
        value = null
    }
}

private class FakeLegacyAatTaskSource : LegacyAATTaskSource {
    val namedTasks = linkedMapOf<String, Task>()
    var autosavedTask: Task? = null

    override fun listTaskNames(): List<String> = namedTasks.keys.toList()

    override fun loadAutosavedTask(): Task? = autosavedTask

    override fun clearAutosavedTask() {
        autosavedTask = null
    }

    override fun loadTask(taskName: String): Task? = namedTasks[taskName]

    override fun deleteTask(taskName: String): Boolean = namedTasks.remove(taskName) != null
}
