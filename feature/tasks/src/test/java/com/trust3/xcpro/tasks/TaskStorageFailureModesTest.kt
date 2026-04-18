package com.trust3.xcpro.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.tasks.aat.SimpleAATTask
import com.trust3.xcpro.tasks.aat.models.AATAreaShape
import com.trust3.xcpro.tasks.aat.models.AATAssignedArea
import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.AATWaypointRole
import com.trust3.xcpro.tasks.aat.persistence.AATTaskFileIO
import com.trust3.xcpro.tasks.racing.RacingTaskStorage
import com.trust3.xcpro.tasks.racing.SimpleRacingTask
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole
import java.io.File
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskStorageFailureModesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("racing_task_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aat_task_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("task_storage_migration_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val tasksRoot = File(context.filesDir, "cup_tasks")
        if (tasksRoot.exists()) {
            tasksRoot.deleteRecursively()
        }
        tasksRoot.mkdirs()
    }

    @Test
    fun `racing save returns false when target file path is a directory`() {
        val storage = RacingTaskStorage(context)
        val scopedDir = File(context.filesDir, "cup_tasks/racing").apply { mkdirs() }
        File(scopedDir, "collision.cup").mkdirs()

        val saved = storage.saveRacingTask(sampleRacingTask(), "collision")

        assertFalse(saved)
    }

    @Test
    fun `racing load returns null for malformed cup content`() {
        val storage = RacingTaskStorage(context)
        val scopedDir = File(context.filesDir, "cup_tasks/racing").apply { mkdirs() }
        File(scopedDir, "bad.cup").writeText("not,a,valid,cup", Charsets.UTF_8)

        val loaded = storage.loadRacingTaskFromFile("bad")

        assertNull(loaded)
    }

    @Test
    fun `aat save returns false when target file path is a directory`() {
        val fileIO = AATTaskFileIO(context)
        val scopedDir = File(context.filesDir, "cup_tasks/aat").apply { mkdirs() }
        File(scopedDir, "AAT_collision.cup").mkdirs()

        val saved = fileIO.saveTaskToFile(sampleAatTask(), "collision")

        assertFalse(saved)
    }

    @Test
    fun `aat load returns null for malformed cup content`() {
        val fileIO = AATTaskFileIO(context)
        val scopedDir = File(context.filesDir, "cup_tasks/aat").apply { mkdirs() }
        File(scopedDir, "AAT_bad.cup").writeText("this is not a cup task", Charsets.UTF_8)

        val loaded = fileIO.loadTaskFromFile("AAT_bad")

        assertNull(loaded)
    }

    private fun sampleRacingTask(): SimpleRacingTask {
        return SimpleRacingTask(
            id = "racing_failure_sample",
            waypoints = listOf(
                RacingWaypoint.createWithStandardizedDefaults(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = 45.0,
                    lon = 7.0,
                    role = RacingWaypointRole.START
                ),
                RacingWaypoint.createWithStandardizedDefaults(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 45.1,
                    lon = 7.1,
                    role = RacingWaypointRole.FINISH
                )
            )
        )
    }

    private fun sampleAatTask(): SimpleAATTask {
        return SimpleAATTask(
            id = "aat_failure_sample",
            waypoints = listOf(
                AATWaypoint(
                    id = "start",
                    title = "Start",
                    subtitle = "",
                    lat = 45.0,
                    lon = 7.0,
                    role = AATWaypointRole.START,
                    assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                        shape = AATAreaShape.LINE,
                        role = AATWaypointRole.START
                    )
                ),
                AATWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 45.1,
                    lon = 7.1,
                    role = AATWaypointRole.FINISH,
                    assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                        shape = AATAreaShape.CIRCLE,
                        role = AATWaypointRole.FINISH
                    )
                )
            ),
            minimumTime = Duration.ofHours(3),
            maximumTime = Duration.ofHours(4)
        )
    }
}
