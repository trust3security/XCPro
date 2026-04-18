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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskStoragePartitioningTest {
    private lateinit var context: Context
    private lateinit var legacyDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("racing_task_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aat_task_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("task_storage_migration_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        legacyDir = File(context.filesDir, "cup_tasks")
        if (legacyDir.exists()) {
            legacyDir.deleteRecursively()
        }
        legacyDir.mkdirs()
    }

    @Test
    fun `racing storage lists scoped plus legacy racing and excludes legacy aat`() {
        val racingStorage = RacingTaskStorage(context)
        File(legacyDir, "legacy_racing.cup").writeText(racingCupContent(), Charsets.UTF_8)
        File(legacyDir, "AAT_legacy_aat.cup").writeText(aatCupContent(), Charsets.UTF_8)

        val saved = racingStorage.saveRacingTask(sampleRacingTask(), "scoped_racing")
        assertTrue(saved)

        val names = racingStorage.getSavedRacingTasks()
        assertTrue(names.contains("legacy_racing.cup"))
        assertTrue(names.contains("scoped_racing.cup"))
        assertFalse(names.contains("AAT_legacy_aat.cup"))
        assertTrue(File(context.filesDir, "cup_tasks/racing/legacy_racing.cup").exists())
        assertFalse(File(context.filesDir, "cup_tasks/legacy_racing.cup").exists())
        assertTrue(File(context.filesDir, "cup_tasks/AAT_legacy_aat.cup").exists())
    }

    @Test
    fun `aat storage lists scoped plus legacy aat and excludes legacy racing`() {
        val aatFileIO = AATTaskFileIO(context)
        File(legacyDir, "legacy_racing.cup").writeText(racingCupContent(), Charsets.UTF_8)
        File(legacyDir, "AAT_legacy_aat.cup").writeText(aatCupContent(), Charsets.UTF_8)

        val saved = aatFileIO.saveTaskToFile(sampleAatTask(), "scoped")
        assertTrue(saved)

        val names = aatFileIO.getSavedTaskFiles()
        assertTrue(names.contains("AAT_legacy_aat.cup"))
        assertTrue(names.contains("AAT_scoped.cup"))
        assertFalse(names.contains("legacy_racing.cup"))
        assertTrue(File(context.filesDir, "cup_tasks/aat/AAT_legacy_aat.cup").exists())
        assertFalse(File(context.filesDir, "cup_tasks/AAT_legacy_aat.cup").exists())
        assertTrue(File(context.filesDir, "cup_tasks/legacy_racing.cup").exists())
    }

    @Test
    fun `load falls back to legacy location and saves to scoped directories`() {
        val racingStorage = RacingTaskStorage(context)
        val aatFileIO = AATTaskFileIO(context)

        File(legacyDir, "legacy_racing.cup").writeText(racingCupContent(), Charsets.UTF_8)
        File(legacyDir, "AAT_legacy_aat.cup").writeText(aatCupContent(), Charsets.UTF_8)

        val legacyRacing = racingStorage.loadRacingTaskFromFile("legacy_racing")
        val legacyAat = aatFileIO.loadTaskFromFile("AAT_legacy_aat")
        assertNotNull(legacyRacing)
        assertNotNull(legacyAat)
        assertTrue(File(context.filesDir, "cup_tasks/racing/legacy_racing.cup").exists())
        assertTrue(File(context.filesDir, "cup_tasks/aat/AAT_legacy_aat.cup").exists())
        assertFalse(File(context.filesDir, "cup_tasks/legacy_racing.cup").exists())
        assertFalse(File(context.filesDir, "cup_tasks/AAT_legacy_aat.cup").exists())

        assertTrue(racingStorage.saveRacingTask(sampleRacingTask(), "new_racing"))
        assertTrue(aatFileIO.saveTaskToFile(sampleAatTask(), "new_aat"))

        assertTrue(File(context.filesDir, "cup_tasks/racing/new_racing.cup").exists())
        assertTrue(File(context.filesDir, "cup_tasks/aat/AAT_new_aat.cup").exists())
        assertFalse(File(context.filesDir, "cup_tasks/new_racing.cup").exists())
        assertFalse(File(context.filesDir, "cup_tasks/AAT_new_aat.cup").exists())
    }

    @Test
    fun `cleanup archives residual legacy conflicts after both migrations complete`() {
        val racingStorage = RacingTaskStorage(context)
        val aatFileIO = AATTaskFileIO(context)
        val scopedRacingDir = File(context.filesDir, "cup_tasks/racing").apply { mkdirs() }
        val legacyConflict = File(legacyDir, "conflict.cup")
        val scopedConflict = File(scopedRacingDir, "conflict.cup")

        scopedConflict.writeText(racingCupContent(), Charsets.UTF_8)
        legacyConflict.writeText(racingCupContentAlt(), Charsets.UTF_8)

        racingStorage.getSavedRacingTasks()
        assertTrue(legacyConflict.exists())
        assertFalse(File(context.filesDir, "cup_tasks/legacy_archive/conflict.cup").exists())

        aatFileIO.getSavedTaskFiles()
        assertFalse(legacyConflict.exists())
        assertTrue(File(context.filesDir, "cup_tasks/legacy_archive/conflict.cup").exists())
        assertTrue(scopedConflict.exists())
    }

    private fun sampleRacingTask(): SimpleRacingTask {
        return SimpleRacingTask(
            id = "racing_sample",
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
                    lat = 45.2,
                    lon = 7.2,
                    role = RacingWaypointRole.FINISH
                )
            )
        )
    }

    private fun sampleAatTask(): SimpleAATTask {
        return SimpleAATTask(
            id = "aat_sample",
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
                    id = "tp1",
                    title = "TP1",
                    subtitle = "",
                    lat = 45.1,
                    lon = 7.1,
                    role = AATWaypointRole.TURNPOINT,
                    assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                        shape = AATAreaShape.CIRCLE,
                        role = AATWaypointRole.TURNPOINT
                    )
                ),
                AATWaypoint(
                    id = "finish",
                    title = "Finish",
                    subtitle = "",
                    lat = 45.2,
                    lon = 7.2,
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

    private fun racingCupContent(): String {
        return """
            name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc
            "Racing task","TASK","","","","","","","","","metadata"
            "Start","START","","4500.000N","00700.000E","0m","1","","","","start"
            "TP1","TP1","","4506.000N","00706.000E","0m","1","","","","turn"
            "Finish","FINISH","","4512.000N","00712.000E","0m","1","","","","finish"
        """.trimIndent()
    }

    private fun racingCupContentAlt(): String {
        return """
            name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc
            "Racing task conflict","TASK","","","","","","","","","metadata"
            "Start","START","","4600.000N","00800.000E","0m","1","","","","start"
            "TP1","TP1","","4606.000N","00806.000E","0m","1","","","","turn"
            "Finish","FINISH","","4612.000N","00812.000E","0m","1","","","","finish"
        """.trimIndent()
    }

    private fun aatCupContent(): String {
        return """
            name,code,country,lat,lon,elev,style,rwdir,rwlen,freq,desc
            "AAT task","TASK","","","","","","","","","metadata"
            "Start","START","","4500.000N","00700.000E","0m","2","","","","start"
            "AAT TP","AAT1","","4506.000N","00706.000E","0m","2","","","","turn"
            "Finish","FINISH","","4512.000N","00712.000E","0m","2","","","","finish"
        """.trimIndent()
    }
}
